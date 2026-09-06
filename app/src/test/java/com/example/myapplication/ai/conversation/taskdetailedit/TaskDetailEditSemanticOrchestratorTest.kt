package com.example.myapplication.ai.conversation.taskdetailedit

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TaskDetailEditSemanticOrchestratorTest {
    @Test
    fun naturalSaveConfirmationUsesSemanticMeaningOutsideFastPaths() = runBlocking {
        listOf("That sounds good", "Sure thing", "Save it").forEach { input ->
            val result = TaskDetailEditSemanticOrchestrator(
                QueueClient(json("CONFIRM_SAVE"))
            ).resolve(input, saveContext(), TaskDetailEditLocalCandidate.Invalid)

            assertEquals(TaskDetailEditAgentMove.CONFIRM_SAVE, result.move)
            assertEquals(TaskDetailEditProposal.ConfirmSave, result.proposal)
            assertTrue(result.agentAttempted)
        }

        val rejection = TaskDetailEditSemanticOrchestrator(
            QueueClient(json("REJECT_SAVE"))
        ).resolve("Leave it for now", saveContext(), TaskDetailEditLocalCandidate.Invalid)
        assertEquals(TaskDetailEditAgentMove.REJECT_SAVE, rejection.move)
        assertEquals(TaskDetailEditProposal.RejectSave, rejection.proposal)
    }

    @Test
    fun boundedSaveConfirmationFastPathsRemainWholeUtteranceScoped() {
        val orchestrator = TaskDetailEditSemanticOrchestrator(QueueClient(json("UNKNOWN")))
        listOf("Yes please", "No thanks", "Don't save it").forEach { input ->
            val result = requireNotNull(orchestrator.resolveImmediate(input, saveContext()))
            assertFalse(result.agentAttempted)
        }
        listOf(
            "No change the time instead",
            "No, make the date Sunday instead",
            "Wait, call it Buy Vitamins"
        ).forEach { input ->
            assertNull(input, orchestrator.resolveImmediate(input, saveContext()))
        }
    }

    @Test
    fun correctionsBeatGenericRejectionDuringSaveConfirmation() = runBlocking {
        val cases = listOf(
            Triple(
                "No change the time instead",
                json("REQUEST_TIME_CHANGE"),
                TaskDetailEditAgentMove.REQUEST_TIME_CHANGE
            ),
            Triple(
                "No, make the date Sunday instead",
                json("SET_DATE", date = "Sunday"),
                TaskDetailEditAgentMove.SET_DATE
            ),
            Triple(
                "Wait, call it Buy Vitamins",
                json("SET_TITLE", title = "Buy Vitamins"),
                TaskDetailEditAgentMove.SET_TITLE
            )
        )
        cases.forEach { (input, response, expectedMove) ->
            val result = TaskDetailEditSemanticOrchestrator(QueueClient(response)).resolve(
                input,
                saveContext(),
                TaskDetailEditLocalCandidate.Invalid
            )
            assertEquals(expectedMove, result.move)
            assertTrue(result.move != TaskDetailEditAgentMove.REJECT_SAVE)
        }
    }

    @Test
    fun fullScheduleCorrectionPreservesLiteralTranscriptValues() = runBlocking {
        val result = TaskDetailEditSemanticOrchestrator(
            QueueClient(json("SET_SCHEDULE", date = "Sunday", time = "9 AM"))
        ).resolve(
            "Move it to Sunday at 9 AM instead",
            saveContext(),
            TaskDetailEditLocalCandidate.Invalid
        )

        assertEquals(TaskDetailEditAgentMove.SET_SCHEDULE, result.move)
        assertEquals(TaskDetailEditProposal.Schedule("Sunday", "9 AM"), result.proposal)
    }

    @Test
    fun saveConfirmationReadMovesSelectOnlyTheRequestedDraftFact() = runBlocking {
        val cases = listOf(
            Triple("What is the title", "READ_TITLE", TaskDetailDraftReadTarget.TITLE),
            Triple("What date is it set for", "READ_DATE", TaskDetailDraftReadTarget.DATE),
            Triple(
                "What time is it currently set for",
                "READ_TIME",
                TaskDetailDraftReadTarget.TIME
            ),
            Triple("When is this task scheduled", "READ_SCHEDULE", TaskDetailDraftReadTarget.SCHEDULE)
        )
        cases.forEach { (input, move, target) ->
            val result = TaskDetailEditSemanticOrchestrator(
                QueueClient(json(move))
            ).resolve(input, saveContext(), TaskDetailEditLocalCandidate.Invalid)

            assertEquals(TaskDetailEditProposal.ReadDraft(target), result.proposal)
        }
    }

    @Test
    fun titleDateTimeAndExactTimeAllAttemptTheSemanticAgent() = runBlocking {
        val cases = listOf(
            Triple(TaskDetailEditField.TITLE, localTitle(), json("SET_TITLE", title = "breakfast")),
            Triple(TaskDetailEditField.DATE, localSchedule(), json("SET_DATE", date = "tomorrow")),
            Triple(TaskDetailEditField.TIME, localSchedule(), json("SET_TIME", time = "11:45 PM"))
        )
        cases.forEach { (field, local, response) ->
            val client = QueueClient(response)
            val result = TaskDetailEditSemanticOrchestrator(client).resolve(
                userText = if (field == TaskDetailEditField.TIME) "11:45 PM" else "natural answer",
                context = context(field),
                localCandidate = local
            )
            assertEquals(1, client.calls)
            assertTrue(result.agentAttempted)
            assertEquals(TaskDetailEditMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        }
    }

    @Test
    fun realisticBareFieldAnswersRemainSupported() = runBlocking {
        val cases = listOf(
            Triple(TaskDetailEditField.TITLE, "Buy medicine", json("SET_TITLE", title = "Buy medicine")),
            Triple(TaskDetailEditField.DATE, "Sunday", json("SET_DATE", date = "Sunday")),
            Triple(TaskDetailEditField.TIME, "8 PM", json("SET_TIME", time = "8 PM"))
        )
        cases.forEach { (field, input, response) ->
            val result = TaskDetailEditSemanticOrchestrator(QueueClient(response)).resolve(
                input,
                context(field),
                TaskDetailEditLocalCandidate.Invalid
            )
            assertTrue(result.move.name.startsWith("SET_"))
        }
    }

    @Test
    fun explicitCancelBypassesTheModelAsSafetyReflex() {
        val client = QueueClient(json("UNKNOWN"))
        val result = requireNotNull(TaskDetailEditSemanticOrchestrator(client).resolveImmediate("never mind"))
        assertEquals(TaskDetailEditProposal.Cancel, result.proposal)
        assertFalse(result.agentAttempted)
        assertEquals(0, client.calls)
    }

    @Test
    fun malformedPrimaryGetsExactlyOneRepair() = runBlocking {
        val client = QueueClient("not json", json("SET_TIME", time = "9 PM"))
        val result = TaskDetailEditSemanticOrchestrator(client).resolve(
            "nine tonight", context(TaskDetailEditField.TIME), localSchedule()
        )
        assertEquals(2, client.calls)
        assertEquals(TaskDetailEditMoveSource.CONVERSATION_AGENT_REPAIR, result.source)
    }

    @Test
    fun unknownPrimaryAndUnknownRepairAreBoundedThenUseFallback() = runBlocking {
        val client = QueueClient(json("UNKNOWN"), json("UNKNOWN"), json("SET_TIME", time = "10 PM"))
        val result = TaskDetailEditSemanticOrchestrator(client).resolve(
            "nine", context(TaskDetailEditField.TIME), localSchedule()
        )
        assertEquals(2, client.calls)
        assertEquals(TaskDetailEditMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
    }

    @Test
    fun modelFailureUsesSafeFallbackAndUnsafeFallbackAsksUnknown() = runBlocking {
        val safeClient = FailingClient()
        val safe = TaskDetailEditSemanticOrchestrator(safeClient).resolve(
            "11:45 PM", context(TaskDetailEditField.TIME), localSchedule()
        )
        assertEquals(TaskDetailEditMoveSource.LOCAL_FAILURE_FALLBACK, safe.source)
        assertTrue(safe.proposal is TaskDetailEditProposal.Schedule)

        val unsafe = TaskDetailEditSemanticOrchestrator(FailingClient()).resolve(
            "something unclear", context(TaskDetailEditField.TIME), TaskDetailEditLocalCandidate.Invalid
        )
        assertEquals(TaskDetailEditMoveSource.DETERMINISTIC_UNKNOWN, unsafe.source)
        assertEquals(TaskDetailEditProposal.Unknown, unsafe.proposal)
        assertTrue(unsafe.agentAttempted)
    }

    private fun context(field: TaskDetailEditField) = TaskDetailEditAgentContext(
        requestedField = field,
        interactionState = "WAITING_FOR_${field.name}",
        interactionGeneration = 2,
        draftRevision = 4,
        hasTitle = true,
        currentDueDate = "03/08/2026",
        currentDueTime = "09:00 PM",
        currentLocalDate = "03/08/2026",
        currentLocalTime = "08:00 PM",
        timezone = "Asia/Kuala_Lumpur",
        currentSchedulePast = false,
        pendingClarification = "",
        allowedMoves = TaskDetailEditAgentContext.allowedMoves(field)
    )

    private fun saveContext() = TaskDetailEditAgentContext(
        requestedField = null,
        interactionState = "WAITING_FOR_SAVE_CONFIRMATION",
        interactionGeneration = 3,
        draftRevision = 5,
        hasTitle = true,
        currentDueDate = "09/08/2026",
        currentDueTime = "08:00 PM",
        currentLocalDate = "08/08/2026",
        currentLocalTime = "06:00 PM",
        timezone = "Asia/Kuala_Lumpur",
        currentSchedulePast = false,
        pendingClarification = "",
        allowedMoves = TaskDetailEditAgentContext.saveConfirmationMoves()
    )

    private fun localTitle() = TaskDetailEditLocalCandidate.Title("breakfast")
    private fun localSchedule() = TaskDetailEditLocalCandidate.Schedule("03/08/2026", "11:45 PM")

    private fun json(
        move: String,
        title: String = "",
        date: String = "",
        time: String = "",
        clarification: String = "",
        confidence: Double = 0.95
    ): String = """{"move":"$move","title":"$title","date_text":"$date","time_text":"$time","clarification":"$clarification","confidence":$confidence}"""

    private class QueueClient(vararg responses: String) : TaskDetailEditSemanticClient {
        private val queue = ArrayDeque(responses.toList())
        var calls = 0
        override suspend fun interpretTaskDetailEditMove(userText: String, contextSummary: String): String {
            calls++
            return queue.removeFirst()
        }
    }

    private class FailingClient : TaskDetailEditSemanticClient {
        override suspend fun interpretTaskDetailEditMove(userText: String, contextSummary: String): String {
            throw IOException("offline")
        }
    }
}
