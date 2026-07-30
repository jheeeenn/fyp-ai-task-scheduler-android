package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.ai.conversation.AssistantRequestToken
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ContextSuggestionDeliveryGuardTest {
    @Test
    fun staleRequestTokenPreventsDelivery() {
        assertEquals(
            ContextSuggestionDeliveryResult.STALE_REQUEST,
            ContextSuggestionDeliveryGuard.requestResult(
                token = AssistantRequestToken(4),
                currentRequestGeneration = 5,
                requestActive = true
            )
        )
        assertEquals(
            ContextSuggestionDeliveryResult.CURRENT,
            ContextSuggestionDeliveryGuard.requestResult(
                token = AssistantRequestToken(5),
                currentRequestGeneration = 5,
                requestActive = true
            )
        )
    }

    @Test
    fun noSuggestionIsSuppressedWhenAnActiveCandidateAppears() {
        assertEquals(
            ContextSuggestionDeliveryResult.CANDIDATES_CHANGED,
            ContextSuggestionDeliveryGuard.validateFreshNoSuggestion(
                snapshot(listOf(task(1, "New task")))
            )
        )
        assertEquals(
            ContextSuggestionDeliveryResult.CURRENT,
            ContextSuggestionDeliveryGuard.validateFreshNoSuggestion(
                snapshot(emptyList())
            )
        )
    }

    @Test
    fun deletedCompletedChildOrRescheduledTaskPreventsFreshSpeech() {
        val task = task(1, "Task", "31/07/2026", "9 AM")
        val snapshot = snapshot(listOf(task))
        val decision = decision(ContextSuggestionType.FOCUS_TASK)

        assertEquals(
            ContextSuggestionDeliveryResult.SELECTED_TASK_MISSING,
            validate(snapshot, decision, emptyMap())
        )
        assertEquals(
            ContextSuggestionDeliveryResult.SELECTED_TASK_INACTIVE,
            validate(snapshot, decision, mapOf(1L to task.copy(isDone = true)))
        )
        assertEquals(
            ContextSuggestionDeliveryResult.SELECTED_TASK_INACTIVE,
            validate(snapshot, decision, mapOf(1L to task.copy(parentTaskId = 99)))
        )
        assertEquals(
            ContextSuggestionDeliveryResult.SCHEDULE_CHANGED,
            validate(snapshot, decision, mapOf(1L to task.copy(dueTime = "10 AM")))
        )
        assertEquals(
            ContextSuggestionDeliveryResult.TITLE_CHANGED,
            validate(snapshot, decision, mapOf(1L to task.copy(title = "Renamed")))
        )
    }

    @Test
    fun continueRejectsAnyChangedOrCompletedSubtaskState() {
        val root = task(1, "Project", "31/07/2026", "9 AM")
        val step = task(10, "Step", parentId = root.id)
        val snapshot = snapshot(listOf(root), mapOf(root.id to listOf(step)))
        val decision = decision(ContextSuggestionType.CONTINUE_SUBTASK)

        assertEquals(
            ContextSuggestionDeliveryResult.CURRENT,
            validate(
                snapshot,
                decision,
                mapOf(root.id to root),
                mapOf(root.id to listOf(step))
            )
        )
        assertEquals(
            ContextSuggestionDeliveryResult.SUBTASKS_CHANGED,
            validate(
                snapshot,
                decision,
                mapOf(root.id to root),
                mapOf(root.id to listOf(step.copy(isDone = true)))
            )
        )
    }

    @Test
    fun breakdownAndClosePairAreRevalidatedAgainstFreshStructureAndSchedule() {
        val broad = task(1, "Project", "31/07/2026", "9 AM")
        val second = task(2, "Meeting", "31/07/2026", "9:30 AM")
        val snapshot = snapshot(listOf(broad, second))
        val breakdown = decision(ContextSuggestionType.BREAK_DOWN_TASK)
        val newStep = task(10, "New step", parentId = broad.id)

        assertEquals(
            ContextSuggestionDeliveryResult.BREAKDOWN_NO_LONGER_ELIGIBLE,
            validate(
                snapshot,
                breakdown,
                mapOf(broad.id to broad),
                mapOf(broad.id to listOf(newStep))
            )
        )

        val pair = snapshot.closePairs.single()
        val review = ContextSuggestionDecision(
            ContextSuggestionType.REVIEW_CLOSE_SCHEDULE,
            pair.primaryRef,
            pair.secondaryRef,
            0.95
        )
        assertEquals(
            ContextSuggestionDeliveryResult.CURRENT,
            validate(
                snapshot,
                review,
                mapOf(broad.id to broad, second.id to second)
            )
        )
        assertEquals(
            ContextSuggestionDeliveryResult.SCHEDULE_CHANGED,
            validate(
                snapshot,
                review,
                mapOf(
                    broad.id to broad,
                    second.id to second.copy(dueTime = "10 AM")
                )
            )
        )
    }

    private fun validate(
        snapshot: ContextSuggestionSnapshot,
        decision: ContextSuggestionDecision,
        tasks: Map<Long, TaskEntity>,
        subtasks: Map<Long, List<TaskEntity>> = emptyMap()
    ) = ContextSuggestionDeliveryGuard.validateFreshSelection(
        snapshot,
        decision,
        tasks,
        subtasks,
        now()
    )

    private fun decision(type: ContextSuggestionType) =
        ContextSuggestionDecision(type, "S1", "", 0.95)

    private fun snapshot(
        roots: List<TaskEntity>,
        subtasks: Map<Long, List<TaskEntity>> = emptyMap()
    ) = ContextSuggestionSnapshotBuilder.build(now(), roots, subtasks)

    private fun now(): Calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Kuala_Lumpur"),
        Locale.UK
    ).apply {
        set(2026, Calendar.JULY, 30, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun task(
        id: Long,
        title: String,
        date: String? = null,
        time: String? = null,
        parentId: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        dueDate = date,
        dueTime = time,
        parentTaskId = parentId
    )
}
