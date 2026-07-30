package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ContextSuggestionDecisionTest {
    private val parser = ContextSuggestionDecisionParser()

    @Test
    fun parserRequiresExactlyFourFieldsAndKnownType() {
        val accepted = parser.parse(json("FOCUS_TASK", "S1"))
        assertEquals(ContextSuggestionType.FOCUS_TASK, accepted.suggestionType)

        listOf(
            json("FOCUS_TASK", "S1").replace("\"confidence\":0.95", "\"confidence\":0.95,\"reply\":\"x\""),
            """{"suggestion_type":"FOCUS_TASK","primary_ref":"S1","confidence":0.95}""",
            json("INVENTED", "S1"),
            json("FOCUS_TASK", "S1").replace("\"confidence\":0.95", "\"confidence\":\"high\"")
        ).forEach { invalid ->
            assertThrows(ContextSuggestionSchemaException::class.java) {
                parser.parse(invalid)
            }
        }
    }

    @Test
    fun lowConfidenceUnknownRefsAndInvalidTypePreconditionsFailClosed() {
        val withSubtasks = task(1, "A project")
        val withoutSubtasks = task(2, "Z errand")
        val subtask = task(10, "Step", parentId = withSubtasks.id)
        val snapshot = snapshot(
            listOf(withSubtasks, withoutSubtasks),
            mapOf(withSubtasks.id to listOf(subtask))
        )

        assertEquals(
            ContextSuggestionValidationResult.LOW_CONFIDENCE,
            validate(snapshot, ContextSuggestionType.FOCUS_TASK, "S1", confidence = 0.79)
        )
        assertEquals(
            ContextSuggestionValidationResult.UNKNOWN_PRIMARY_REF,
            validate(snapshot, ContextSuggestionType.FOCUS_TASK, "S99")
        )
        assertEquals(
            ContextSuggestionValidationResult.NO_UNFINISHED_SUBTASK,
            validate(snapshot, ContextSuggestionType.CONTINUE_SUBTASK, "S2")
        )
        assertEquals(
            ContextSuggestionValidationResult.NOT_BREAKDOWN_ELIGIBLE,
            validate(snapshot, ContextSuggestionType.BREAK_DOWN_TASK, "S1")
        )
    }

    @Test
    fun closeScheduleMustBeAnExactAndroidSuppliedOrderedPair() {
        val snapshot = snapshot(
            listOf(
                task(1, "First", "31/07/2026", "10:00"),
                task(2, "Second", "31/07/2026", "10:15")
            )
        )
        val pair = snapshot.closePairs.single()

        assertEquals(
            ContextSuggestionValidationResult.ACCEPTED,
            validate(
                snapshot,
                ContextSuggestionType.REVIEW_CLOSE_SCHEDULE,
                pair.primaryRef,
                pair.secondaryRef
            )
        )
        assertEquals(
            ContextSuggestionValidationResult.UNKNOWN_CLOSE_PAIR,
            validate(
                snapshot,
                ContextSuggestionType.REVIEW_CLOSE_SCHEDULE,
                pair.secondaryRef,
                pair.primaryRef
            )
        )
    }

    @Test
    fun noSuggestionIsValidOnlyForEmptySnapshot() {
        assertEquals(
            ContextSuggestionValidationResult.ACCEPTED,
            validate(snapshot(emptyList()), ContextSuggestionType.NO_SUGGESTION, "")
        )
        assertEquals(
            ContextSuggestionValidationResult.NO_SUGGESTION_WITH_CANDIDATES,
            validate(snapshot(listOf(task(1, "Task"))), ContextSuggestionType.NO_SUGGESTION, "")
        )
    }

    @Test
    fun overdueWorkBlocksBreakdownAndCloseScheduleProposals() {
        val overdue = task(1, "Urgent", "30/07/2026", "10 AM")
        val broad = task(2, "Plan final year project", "31/07/2026", "10 AM")
        val close = task(3, "Review draft", "31/07/2026", "10:15 AM")
        val snapshot = snapshot(listOf(overdue, broad, close))
        val pair = snapshot.closePairs.single()

        assertEquals(
            ContextSuggestionValidationResult.OVERDUE_SAFEGUARD,
            validate(snapshot, ContextSuggestionType.BREAK_DOWN_TASK, "S2")
        )
        assertEquals(
            ContextSuggestionValidationResult.OVERDUE_SAFEGUARD,
            validate(
                snapshot,
                ContextSuggestionType.REVIEW_CLOSE_SCHEDULE,
                pair.primaryRef,
                pair.secondaryRef
            )
        )
    }

    @Test
    fun semanticFailureFallsBackOnceToHighestRankedAttentionCandidate() = runBlocking {
        val root = task(1, "Overdue project", "30/07/2026", "10 AM")
        val subtask = task(10, "Continue this", parentId = root.id)
        val snapshot = snapshot(listOf(root), mapOf(root.id to listOf(subtask)))
        var calls = 0
        val orchestrator = ContextSuggestionSemanticOrchestrator(
            ContextSuggestionSemanticClient { _, _ ->
                calls += 1
                throw IllegalStateException("offline")
            }
        )

        val result = orchestrator.select("What should I do next?", snapshot)

        assertEquals(1, calls)
        assertEquals(ContextSuggestionDecisionSource.DETERMINISTIC_FALLBACK, result.source)
        assertEquals(ContextSuggestionValidationResult.REQUEST_FAILED, result.validationResult)
        assertEquals(ContextSuggestionType.CONTINUE_SUBTASK, result.decision.suggestionType)
        assertEquals("S1", result.decision.primaryRef)
    }

    @Test
    fun emptySnapshotSkipsSemanticCallAndReturnsNoSuggestion() = runBlocking {
        var calls = 0
        val orchestrator = ContextSuggestionSemanticOrchestrator(
            ContextSuggestionSemanticClient { _, _ ->
                calls += 1
                json("NO_SUGGESTION", "")
            }
        )

        val result = orchestrator.select("What should I do next?", snapshot(emptyList()))

        assertEquals(0, calls)
        assertEquals(ContextSuggestionDecisionSource.ANDROID_NO_CANDIDATES, result.source)
        assertEquals(ContextSuggestionType.NO_SUGGESTION, result.decision.suggestionType)
    }

    @Test
    fun validSemanticDecisionIsAcceptedWithoutRepairCall() = runBlocking {
        var calls = 0
        val orchestrator = ContextSuggestionSemanticOrchestrator(
            ContextSuggestionSemanticClient { _, snapshotJson ->
                calls += 1
                assertTrue(snapshotJson.contains("\"ref\":\"S1\""))
                json("FOCUS_TASK", "S1")
            }
        )

        val result = orchestrator.select(
            "What should I focus on?",
            snapshot(listOf(task(1, "Task")))
        )

        assertEquals(1, calls)
        assertEquals(ContextSuggestionDecisionSource.SEMANTIC_AGENT, result.source)
        assertEquals(ContextSuggestionValidationResult.ACCEPTED, result.validationResult)
    }

    private fun validate(
        snapshot: ContextSuggestionSnapshot,
        type: ContextSuggestionType,
        primary: String,
        secondary: String = "",
        confidence: Double = 0.95
    ) = ContextSuggestionDecisionValidator.validate(
        ContextSuggestionDecision(type, primary, secondary, confidence),
        snapshot
    )

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

    private fun json(
        type: String,
        primary: String,
        secondary: String = ""
    ) = """{"suggestion_type":"$type","primary_ref":"$primary","secondary_ref":"$secondary","confidence":0.95}"""
}
