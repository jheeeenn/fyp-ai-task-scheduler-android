package com.example.myapplication.ai.conversation.taskedit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTaskSemanticOrchestratorTest {
    @Test
    fun compoundSaveRejectionsPreferExplicitFieldChanges() = runBlocking {
        val cases = listOf(
            Triple(
                "No change the title first",
                json("REQUEST_TITLE_CHANGE"),
                EditTaskSemanticMove.REQUEST_TITLE_CHANGE
            ),
            Triple(
                "No change the time instead",
                json("REQUEST_TIME_CHANGE"),
                EditTaskSemanticMove.REQUEST_TIME_CHANGE
            ),
            Triple(
                "No, call it Buy Medicine instead",
                json("CHANGE_TITLE", title = "Buy Medicine"),
                EditTaskSemanticMove.CHANGE_TITLE
            )
        )

        cases.forEach { (utterance, agentResponse, expectedMove) ->
            val orchestrator = EditTaskSemanticOrchestrator(QueueClient(agentResponse))
            val context = context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
            assertNull(orchestrator.resolveImmediate(utterance, context))
            val result = orchestrator.resolve(
                utterance,
                context
            )

            assertEquals(expectedMove, result.move)
            assertTrue(result.move != EditTaskSemanticMove.REJECT_SAVE)
        }
    }

    @Test
    fun naturalTitleCorrectionsReturnOnlyTheExtractedReplacement() = runBlocking {
        val cases = listOf(
            "Actually change the title to Take Supplements",
            "Call it Take Supplements",
            "No, call it Take Supplements instead"
        )
        cases.forEach { utterance ->
            val client = QueueClient(json("CHANGE_TITLE", title = "Take Supplements"))
            val result = EditTaskSemanticOrchestrator(client).resolve(
                utterance,
                context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
            )

            assertEquals(EditTaskSemanticMove.CHANGE_TITLE, result.move)
            assertEquals("Take Supplements", result.title)
            assertFalse(result.title.equals(utterance, ignoreCase = true))
            assertEquals(1, client.calls)
        }
    }

    @Test
    fun pendingTitleAcceptsLiteralOrNaturalTitleButCanSwitchFields() = runBlocking {
        val state = EditTaskInteractionState.WAITING_FOR_TITLE
        listOf(
            "Take Supplements",
            "Actually change the title to Take Supplements"
        ).forEach { utterance ->
            val result = EditTaskSemanticOrchestrator(
                QueueClient(json("CHANGE_TITLE", title = "Take Supplements"))
            ).resolve(utterance, context(state))
            assertEquals(EditTaskSemanticMove.CHANGE_TITLE, result.move)
            assertEquals("Take Supplements", result.title)
        }

        val switch = EditTaskSemanticOrchestrator(
            QueueClient(json("REQUEST_DATE_CHANGE"))
        ).resolve("change the date instead", context(state))
        assertEquals(EditTaskSemanticMove.REQUEST_DATE_CHANGE, switch.move)
        assertEquals("", switch.title)
    }

    @Test
    fun pendingDateAndTimeAcceptRealisticVoiceTranscriptValues() = runBlocking {
        val date = EditTaskSemanticOrchestrator(
            QueueClient(json("CHANGE_DATE", date = "Sunday"))
        ).resolve("Sunday", context(EditTaskInteractionState.WAITING_FOR_DATE))
        assertEquals(EditTaskSemanticMove.CHANGE_DATE, date.move)
        assertEquals("Sunday", date.dateText)

        val time = EditTaskSemanticOrchestrator(
            QueueClient(json("CHANGE_TIME", time = "8 PM"))
        ).resolve("8 PM", context(EditTaskInteractionState.WAITING_FOR_TIME))
        assertEquals(EditTaskSemanticMove.CHANGE_TIME, time.move)
        assertEquals("8 PM", time.timeText)
    }

    @Test
    fun readOnlyQuestionsSelectAFieldWithoutReturningDraftFacts() = runBlocking {
        val cases = listOf(
            Triple("What is the title", "READ_TITLE", EditTaskSemanticMove.READ_TITLE),
            Triple("What is this task called", "READ_TITLE", EditTaskSemanticMove.READ_TITLE),
            Triple(
                "What date is this task set for",
                "READ_DATE",
                EditTaskSemanticMove.READ_DATE
            ),
            Triple(
                "What time is this task currently set for",
                "READ_TIME",
                EditTaskSemanticMove.READ_TIME
            ),
            Triple(
                "When is this task scheduled",
                "READ_SCHEDULE",
                EditTaskSemanticMove.READ_SCHEDULE
            )
        )

        cases.forEach { (utterance, responseMove, expectedMove) ->
            val result = EditTaskSemanticOrchestrator(
                QueueClient(json(responseMove))
            ).resolve(utterance, context(EditTaskInteractionState.READY_FOR_EDIT))

            assertEquals(expectedMove, result.move)
            assertEquals("", result.title)
            assertEquals("", result.dateText)
            assertEquals("", result.timeText)
        }
    }

    @Test
    fun readQuestionRemainsAvailableWhileCollectingAnotherField() = runBlocking {
        val result = EditTaskSemanticOrchestrator(
            QueueClient(json("READ_TIME"))
        ).resolve(
            "What time is this task currently set for",
            context(EditTaskInteractionState.WAITING_FOR_TITLE)
        )

        assertEquals(EditTaskSemanticMove.READ_TIME, result.move)
        assertTrue(EditTaskSemanticMove.READ_TIME in
            context(EditTaskInteractionState.WAITING_FOR_TITLE).allowedMoves)
    }

    @Test
    fun dateTimeAndScheduleCorrectionsPreserveSemanticTemporalText() = runBlocking {
        val cases = listOf(
            Triple(
                "Actually make it Sunday",
                json("CHANGE_DATE", date = "Sunday"),
                EditTaskMoveResolutionExpectation(EditTaskSemanticMove.CHANGE_DATE, "Sunday", "")
            ),
            Triple(
                "Use 8 PM instead",
                json("CHANGE_TIME", time = "8 PM"),
                EditTaskMoveResolutionExpectation(EditTaskSemanticMove.CHANGE_TIME, "", "8 PM")
            ),
            Triple(
                "Move it to Sunday at 8 PM",
                json("CHANGE_SCHEDULE", date = "Sunday", time = "8 PM"),
                EditTaskMoveResolutionExpectation(
                    EditTaskSemanticMove.CHANGE_SCHEDULE,
                    "Sunday",
                    "8 PM"
                )
            ),
            Triple(
                "No, make it Sunday instead",
                json("CHANGE_DATE", date = "Sunday"),
                EditTaskMoveResolutionExpectation(EditTaskSemanticMove.CHANGE_DATE, "Sunday", "")
            )
        )

        cases.forEach { (utterance, response, expected) ->
            val result = EditTaskSemanticOrchestrator(QueueClient(response)).resolve(
                utterance,
                context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
            )
            assertEquals(expected.move, result.move)
            assertEquals(expected.dateText, result.dateText)
            assertEquals(expected.timeText, result.timeText)
            assertTrue(result.move != EditTaskSemanticMove.REJECT_SAVE)
        }
    }

    @Test
    fun boundedConfirmationFastPathsRemainStateScoped() {
        val client = QueueClient(json("UNKNOWN"))
        val orchestrator = EditTaskSemanticOrchestrator(client)
        listOf("yes", "Yes please", "Go ahead").forEach { utterance ->
            val result = requireNotNull(
                orchestrator.resolveImmediate(
                    utterance,
                    context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
                )
            )
            assertEquals(EditTaskSemanticMove.CONFIRM_SAVE, result.move)
            assertFalse(result.agentAttempted)
        }
        val rejection = requireNotNull(
            orchestrator.resolveImmediate(
                "no",
                context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
            )
        )
        assertEquals(EditTaskSemanticMove.REJECT_SAVE, rejection.move)
        assertEquals(0, client.calls)
    }

    @Test
    fun naturalApprovalCanUseTheSameConversationAgent() = runBlocking {
        val result = EditTaskSemanticOrchestrator(
            QueueClient(json("CONFIRM_SAVE"))
        ).resolve(
            "That's fine",
            context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
        )

        assertEquals(EditTaskSemanticMove.CONFIRM_SAVE, result.move)
        assertEquals(EditTaskMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
    }

    @Test
    fun existingConversationalCorrectionsAndApprovalRemainSupported() = runBlocking {
        val cases = listOf(
            Triple(
                "Actually make it 8 PM instead",
                json("CHANGE_TIME", time = "8 PM"),
                EditTaskSemanticMove.CHANGE_TIME
            ),
            Triple(
                "No make it Sunday",
                json("CHANGE_DATE", date = "Sunday"),
                EditTaskSemanticMove.CHANGE_DATE
            ),
            Triple(
                "Actually call it medicine",
                json("CHANGE_TITLE", title = "medicine"),
                EditTaskSemanticMove.CHANGE_TITLE
            ),
            Triple(
                "That sounds good",
                json("CONFIRM_SAVE"),
                EditTaskSemanticMove.CONFIRM_SAVE
            )
        )

        cases.forEach { (utterance, agentResponse, expectedMove) ->
            val result = EditTaskSemanticOrchestrator(QueueClient(agentResponse)).resolve(
                utterance,
                context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
            )
            assertEquals(expectedMove, result.move)
        }
    }

    @Test
    fun neverMindUsesBoundedCancellationWithoutCallingTheAgent() {
        val client = QueueClient(json("UNKNOWN"))
        val result = requireNotNull(
            EditTaskSemanticOrchestrator(client).resolveImmediate(
                "Never mind",
                context(EditTaskInteractionState.WAITING_FOR_TITLE)
            )
        )

        assertEquals(EditTaskSemanticMove.CANCEL, result.move)
        assertEquals(0, client.calls)
    }

    @Test
    fun ambiguousInputAndBlankRequiredValuesFailClosed() = runBlocking {
        val ambiguous = EditTaskSemanticOrchestrator(
            QueueClient(json("UNKNOWN"), json("UNKNOWN"))
        ).resolve("The weather is pleasant", context(EditTaskInteractionState.READY_FOR_EDIT))
        assertEquals(EditTaskSemanticMove.UNKNOWN, ambiguous.move)

        val blankTitle = EditTaskSemanticOrchestrator(
            QueueClient(
                json("CHANGE_TITLE", title = ""),
                json("CHANGE_TITLE", title = "")
            )
        ).resolve(
            "Actually change the title",
            context(EditTaskInteractionState.WAITING_FOR_TITLE)
        )
        assertEquals(EditTaskSemanticMove.UNKNOWN, blankTitle.move)
        assertEquals("", blankTitle.title)
    }

    @Test
    fun operationInFlightRejectsSemanticMutation() = runBlocking {
        val client = QueueClient(json("CHANGE_TITLE", title = "Take Supplements"))
        val result = EditTaskSemanticOrchestrator(client).resolve(
            "Call it Take Supplements",
            context(EditTaskInteractionState.OPERATION_IN_FLIGHT)
        )

        assertEquals(EditTaskSemanticMove.UNKNOWN, result.move)
        assertEquals("", result.title)
    }

    @Test
    fun malformedPrimaryGetsOneBoundedRepair() = runBlocking {
        val client = QueueClient("not json", json("CHANGE_TIME", time = "8 PM"))
        val result = EditTaskSemanticOrchestrator(client).resolve(
            "Use 8 PM instead",
            context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
        )

        assertEquals(EditTaskSemanticMove.CHANGE_TIME, result.move)
        assertEquals(EditTaskMoveSource.CONVERSATION_AGENT_REPAIR, result.source)
        assertEquals(2, client.calls)
    }

    private data class EditTaskMoveResolutionExpectation(
        val move: EditTaskSemanticMove,
        val dateText: String,
        val timeText: String
    )

    private class QueueClient(vararg responses: String) : EditTaskSemanticClient {
        private val responses = ArrayDeque(responses.toList())
        var calls = 0

        override suspend fun interpretEditTaskMove(
            userText: String,
            contextSummary: String
        ): String {
            calls += 1
            return responses.removeFirst()
        }
    }

    private companion object {
        fun context(state: EditTaskInteractionState) = EditTaskAgentContext(
            interactionState = state,
            draftRevision = 3,
            pendingFieldTarget = when (state) {
                EditTaskInteractionState.WAITING_FOR_TITLE -> "TITLE"
                EditTaskInteractionState.WAITING_FOR_DATE -> "DATE"
                EditTaskInteractionState.WAITING_FOR_TIME -> "TIME"
                else -> "NONE"
            },
            temporalClarificationPending = false,
            relativeTemporalProposalActive = false,
            saveInFlight = state == EditTaskInteractionState.OPERATION_IN_FLIGHT,
            deleteInFlight = false,
            currentDraftTitle = "Take Medicine",
            currentDraftDate = "07/09/2026",
            currentDraftTime = "7:15 AM",
            authoritativeOriginalTitle = "Take Vitamins",
            authoritativeOriginalDate = "07/09/2026",
            authoritativeOriginalTime = "7:15 AM",
            currentLocalDate = "05/09/2026",
            currentLocalTime = "12:30 AM",
            timezone = "Asia/Kuala_Lumpur",
            allowedMoves = EditTaskAgentContext.allowedMoves(state)
        )

        fun json(
            move: String,
            title: String = "",
            date: String = "",
            time: String = "",
            confidence: Double = 0.97
        ): String =
            """{"move":"$move","title":"$title","date_text":"$date","time_text":"$time","confidence":$confidence}"""
    }
}
