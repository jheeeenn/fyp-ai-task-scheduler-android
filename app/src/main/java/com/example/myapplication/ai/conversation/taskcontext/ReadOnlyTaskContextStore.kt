package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.data.TaskEntity
import java.util.Collections
import java.util.Locale

/**
 * Session-scoped bridge from authoritative Android query results to model-visible task context.
 * It never queries Room and never exposes the private temporary-ref-to-Room-ID mapping.
 */
class ReadOnlyTaskContextStore {
    private var generation: Long = 0
    private var currentSnapshot = emptySnapshot(generation)
    private var roomIdByRef: Map<String, Long> = emptyMap()

    @Synchronized
    fun replaceRecentQueryResults(
        tasks: List<TaskEntity>,
        subtasksByParentId: Map<Long, List<TaskEntity>> = emptyMap()
    ) {
        replace(
            scope = TaskContextScope.RECENT_QUERY_RESULTS,
            tasks = tasks,
            subtasksByParentId = subtasksByParentId
        )
    }

    @Synchronized
    fun replaceDailyBriefingResults(
        tasks: List<TaskEntity>,
        subtasksByParentId: Map<Long, List<TaskEntity>> = emptyMap()
    ) {
        replace(
            scope = TaskContextScope.DAILY_BRIEFING,
            tasks = tasks,
            subtasksByParentId = subtasksByParentId
        )
    }

    @Synchronized
    fun replaceTaskMatchChoices(tasks: List<TaskEntity>) {
        replace(
            scope = TaskContextScope.TASK_MATCH_CHOICES,
            tasks = tasks,
            subtasksByParentId = emptyMap()
        )
    }

    @Synchronized
    fun clear() {
        generation += 1
        roomIdByRef = emptyMap()
        currentSnapshot = emptySnapshot(generation)
    }

    @Synchronized
    fun snapshot(): ReadOnlyTaskContextSnapshot = currentSnapshot.copy(
        items = currentSnapshot.items.toList()
    )

    @Synchronized
    fun snapshotForPrompt(): String = currentSnapshot.toPromptText()

    @Synchronized
    fun capture(): ReadOnlyTaskContextCapture {
        val capturedSnapshot = currentSnapshot.copy(
            items = Collections.unmodifiableList(currentSnapshot.items.toList())
        )
        return ReadOnlyTaskContextCapture(
            snapshot = capturedSnapshot,
            promptText = capturedSnapshot.toPromptText()
        )
    }

    @Synchronized
    fun currentGeneration(): Long = generation

    /** Android-only infrastructure for future authoritative reference handling. */
    @Synchronized
    fun resolveRef(ref: String, expectedGeneration: Long): Long? {
        if (expectedGeneration != generation) return null
        return roomIdByRef[ref.trim().uppercase(Locale.ROOT)]
    }

    private fun replace(
        scope: TaskContextScope,
        tasks: List<TaskEntity>,
        subtasksByParentId: Map<Long, List<TaskEntity>>
    ) {
        generation += 1
        val boundedTasks = tasks.take(MAX_PROMPT_ITEMS)
        val items = boundedTasks.mapIndexed { index, task ->
            val subtasks = subtasksByParentId[task.id].orEmpty()
            ReadOnlyTaskContextItem(
                ref = "T${index + 1}",
                title = sanitizeTitle(task.title),
                dueDate = sanitizeField(task.dueDate.orEmpty()),
                dueTime = sanitizeField(task.dueTime.orEmpty()),
                isDone = task.isDone,
                subtaskCount = subtasks.size,
                unfinishedSubtaskCount = subtasks.count { !it.isDone }
            )
        }
        roomIdByRef = boundedTasks.mapIndexed { index, task ->
            "T${index + 1}" to task.id
        }.toMap()
        currentSnapshot = ReadOnlyTaskContextSnapshot(
            scope = scope,
            generation = generation,
            items = items,
            truncated = tasks.size > MAX_PROMPT_ITEMS
        )
    }

    private fun ReadOnlyTaskContextSnapshot.toPromptText(): String = buildString {
        appendLine("Scope: ${scope.name}")
        appendLine("Generation: $generation")
        if (items.isEmpty()) {
            appendLine("Items: None")
        } else {
            appendLine("Items:")
            items.forEach { item ->
                appendLine(
                    "{\"ref\":${jsonString(item.ref)}," +
                        "\"title\":${jsonString(item.title)}," +
                        "\"date\":${jsonString(item.dueDate)}," +
                        "\"time\":${jsonString(item.dueTime)}," +
                        "\"status\":${jsonString(if (item.isDone) "COMPLETED" else "ACTIVE")}," +
                        "\"subtasks\":${item.subtaskCount}," +
                        "\"unfinished_subtasks\":${item.unfinishedSubtaskCount}}"
                )
            }
        }
        append("Truncated: $truncated")
    }

    private fun sanitizeTitle(title: String): String = sanitizeField(title)
        .take(MAX_PROMPT_TITLE_LENGTH)
        .trim()

    private fun sanitizeField(value: String): String {
        val flattened = buildString(value.length) {
            value.forEach { character ->
                when {
                    character.isISOControl() || character.isWhitespace() -> append(' ')
                    else -> append(character)
                }
            }
        }
        return flattened.replace(WHITESPACE, " ").trim()
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(character)
            }
        }
        append('"')
    }

    private companion object {
        const val MAX_PROMPT_ITEMS = 8
        const val MAX_PROMPT_TITLE_LENGTH = 120
        val WHITESPACE = Regex("\\s+")

        fun emptySnapshot(generation: Long) = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.NONE,
            generation = generation,
            items = emptyList(),
            truncated = false
        )
    }
}
