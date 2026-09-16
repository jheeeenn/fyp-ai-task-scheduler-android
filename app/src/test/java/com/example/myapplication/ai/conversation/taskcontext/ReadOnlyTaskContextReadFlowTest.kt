package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyTaskContextReadFlowTest {
    @Test
    fun androidRendersCapturedT2SummaryDeterministically() {
        val store = populatedStore()
        val capture = store.capture()
        val validated = validate(
            store = store,
            capture = capture,
            ref = "T2",
            detail = ConversationContextDetail.SUMMARY
        )

        assertTrue(validated.isValid)
        assertEquals(
            "The second task was Buy groceries, scheduled for 23 July at 8:30 PM.",
            ReadOnlyTaskContextResponseRenderer.render(
                item = requireNotNull(validated.item),
                detail = validated.detail
            )
        )
    }

    @Test
    fun timeReadUsesOnlyCapturedT1Time() {
        val store = populatedStore()
        val capture = store.capture()
        val validated = validate(
            store = store,
            capture = capture,
            ref = "T1",
            detail = ConversationContextDetail.TIME
        )

        val speech = ReadOnlyTaskContextResponseRenderer.render(
            item = requireNotNull(validated.item),
            detail = validated.detail
        )
        assertEquals("Take medicine is scheduled at 11 AM.", speech)
        assertFalse(speech.contains("23 July"))
        assertFalse(speech.contains("Buy groceries"))
    }

    @Test
    fun canonicalWholeHourUsesNaturalContextTimeSpeech() {
        val item = ReadOnlyTaskContextItem(
            ref = "T1",
            title = "Doctor appointment",
            dueDate = "17/09/2026",
            dueTime = "9:00 AM",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )

        assertEquals(
            "Doctor appointment is scheduled at 9 AM.",
            ReadOnlyTaskContextResponseRenderer.render(
                item,
                ConversationContextDetail.TIME
            )
        )
    }

    @Test
    fun unknownRefIsRejected() {
        val store = populatedStore()
        val capture = store.capture()

        val validated = validate(
            store = store,
            capture = capture,
            ref = "T9",
            detail = ConversationContextDetail.TITLE
        )

        assertEquals(ContextReadValidationResult.UNKNOWN_REF, validated.result)
        assertFalse(validated.isValid)
    }

    @Test
    fun staleCapturedGenerationIsRejected() {
        val store = populatedStore()
        val capture = store.capture()
        store.clear()

        val validated = validate(
            store = store,
            capture = capture,
            ref = "T2",
            detail = ConversationContextDetail.SUMMARY
        )

        assertEquals(ContextReadValidationResult.STALE_GENERATION, validated.result)
        assertFalse(validated.isValid)
    }

    @Test
    fun replacementDuringModelRequestCannotChangeT2Meaning() {
        val store = populatedStore()
        val requestCapture = store.capture()
        assertEquals("Buy groceries", requestCapture.snapshot.items[1].title)

        store.replaceRecentQueryResults(
            listOf(
                task(7001, "New first", "24/07/2026", "1:00 PM"),
                task(7002, "Different T2", "24/07/2026", "2:00 PM")
            )
        )

        val validated = validate(
            store = store,
            capture = requestCapture,
            ref = "T2",
            detail = ConversationContextDetail.SUMMARY
        )

        assertEquals("Buy groceries", requestCapture.snapshot.items[1].title)
        assertEquals("Different T2", store.snapshot().items[1].title)
        assertEquals(ContextReadValidationResult.STALE_GENERATION, validated.result)
        assertFalse(validated.isValid)
    }

    @Test
    fun capturePromptAndSnapshotComeFromSameGenerationAndContainNoRoomIds() {
        val store = populatedStore()
        val capture = store.capture()

        assertTrue(capture.promptText.contains("Generation: ${capture.snapshot.generation}"))
        capture.snapshot.items.forEach { item ->
            assertTrue(capture.promptText.contains("\"ref\":\"${item.ref}\""))
            assertTrue(capture.promptText.contains("\"title\":\"${item.title}\""))
        }
        assertFalse(capture.promptText.contains("918273645"))
        assertFalse(capture.promptText.contains("827364519"))

        val speech = ReadOnlyTaskContextResponseRenderer.render(
            capture.snapshot.items[1],
            ConversationContextDetail.SUMMARY
        )
        assertFalse(speech.contains("918273645"))
        assertFalse(speech.contains("827364519"))
    }

    @Test
    fun missingValuesAndSubtasksHaveDeterministicSpeech() {
        val item = ReadOnlyTaskContextItem(
            ref = "T1",
            title = "Unscheduled task",
            dueDate = "",
            dueTime = "",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )

        assertEquals(
            "It does not have a date.",
            ReadOnlyTaskContextResponseRenderer.render(item, ConversationContextDetail.DATE)
        )
        assertEquals(
            "It does not have a time.",
            ReadOnlyTaskContextResponseRenderer.render(item, ConversationContextDetail.TIME)
        )
        assertEquals(
            "It has no subtasks.",
            ReadOnlyTaskContextResponseRenderer.render(item, ConversationContextDetail.SUBTASKS)
        )

        val withSubtasks = item.copy(subtaskCount = 2, unfinishedSubtaskCount = 1)
        assertEquals(
            "Unscheduled task has two subtasks, with one unfinished.",
            ReadOnlyTaskContextResponseRenderer.render(
                withSubtasks,
                ConversationContextDetail.SUBTASKS
            )
        )
    }

    @Test
    fun statusAndTitleDetailsUseCapturedFacts() {
        val active = ReadOnlyTaskContextItem(
            ref = "T2",
            title = "Buy groceries",
            dueDate = "23/07/2026",
            dueTime = "8:30 PM",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )

        assertEquals(
            "The second task was Buy groceries.",
            ReadOnlyTaskContextResponseRenderer.render(active, ConversationContextDetail.TITLE)
        )
        assertEquals(
            "Buy groceries is active.",
            ReadOnlyTaskContextResponseRenderer.render(active, ConversationContextDetail.STATUS)
        )
        assertEquals(
            "Buy groceries is completed.",
            ReadOnlyTaskContextResponseRenderer.render(
                active.copy(isDone = true),
                ConversationContextDetail.STATUS
            )
        )
    }

    @Test
    fun lowConfidenceAndNoneDetailFailClosed() {
        val store = populatedStore()
        val capture = store.capture()
        val lowConfidence = contextDecision("T1", ConversationContextDetail.TITLE).copy(
            confidence = 0.79
        )
        val noneDetail = contextDecision("T1", ConversationContextDetail.NONE)

        assertEquals(
            ContextReadValidationResult.LOW_CONFIDENCE,
            ReadOnlyTaskContextReadValidator.validate(
                lowConfidence,
                capture.snapshot,
                store.currentGeneration()
            ).result
        )
        assertEquals(
            ContextReadValidationResult.INVALID_DETAIL,
            ReadOnlyTaskContextReadValidator.validate(
                noneDetail,
                capture.snapshot,
                store.currentGeneration()
            ).result
        )
    }

    private fun validate(
        store: ReadOnlyTaskContextStore,
        capture: ReadOnlyTaskContextCapture,
        ref: String,
        detail: ConversationContextDetail
    ): ValidatedContextRead = ReadOnlyTaskContextReadValidator.validate(
        decision = contextDecision(ref, detail),
        capturedSnapshot = capture.snapshot,
        currentGeneration = store.currentGeneration()
    )

    private fun contextDecision(
        ref: String,
        detail: ConversationContextDetail
    ) = ConversationDecision(
        route = ConversationRoute.CONTEXT_READ,
        contextRef = ref,
        contextDetail = detail,
        confidence = 0.97,
        listenAgain = true
    )

    private fun populatedStore() = ReadOnlyTaskContextStore().apply {
        replaceRecentQueryResults(
            listOf(
                task(918273645, "Take medicine", "23/07/2026", "11:00 AM"),
                task(827364519, "Buy groceries", "23/07/2026", "8:30 PM")
            )
        )
    }

    private fun task(
        id: Long,
        title: String,
        date: String,
        time: String
    ) = TaskEntity(
        id = id,
        title = title,
        dueDate = date,
        dueTime = time
    )
}
