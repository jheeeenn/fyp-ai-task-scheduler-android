package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.data.TaskEntity

/** Room-backed discovery. The model selects a ref; Android owns records, order and speech. */
class AuthoritativeSubtaskReader(
    private val store: ReadOnlyTaskContextStore,
    private val getById: suspend (Long) -> TaskEntity?,
    private val getSubtasks: suspend (Long) -> List<TaskEntity>,
    private val isCurrent: () -> Boolean = { true }
) {
    suspend fun read(ref: String, generation: Long): String? {
        val id = store.resolveRef(ref, generation) ?: return null
        val parent = getById(id) ?: return null
        if (!store.matchesResolvedTask(ref, generation, parent)) return null
        val children = getSubtasks(parent.id)
        val refreshedParent = getById(id) ?: return null
        if (!isCurrent() || !store.matchesResolvedTask(ref, generation, refreshedParent)) return null
        if (children.any { it.parentTaskId != parent.id }) return null
        if (children.isEmpty()) return "${parent.title} has no subtasks."
        store.replaceSubtaskList(children)
        val counts = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight")
        val ordinals = listOf("First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth")
        return buildString {
            append("${parent.title} has ${counts.getOrNull(children.size) ?: children.size} ")
            append(if (children.size == 1) "subtask." else "subtasks.")
            children.forEachIndexed { index, child ->
                append(" ${ordinals.getOrNull(index) ?: "Number ${index + 1}"}, ${child.title}")
                if (!child.title.endsWith('.') && !child.title.endsWith('!') && !child.title.endsWith('?')) append('.')
            }
        }
    }
}
