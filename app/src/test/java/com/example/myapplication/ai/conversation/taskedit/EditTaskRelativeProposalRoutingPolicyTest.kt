package com.example.myapplication.ai.conversation.taskedit

import com.example.myapplication.ai.temporal.RelativeTemporalProposalState
import com.example.myapplication.ai.temporal.RelativeTemporalExpectedField
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTaskRelativeProposalRoutingPolicyTest {
    @Test
    fun clearProposalControlsRemainLocal() {
        assertEquals(
            EditTaskRelativeProposalLocalAction.CONFIRM,
            localAction("Yes please")
        )
        assertEquals(
            EditTaskRelativeProposalLocalAction.REJECT,
            localAction("No")
        )
        assertEquals(
            EditTaskRelativeProposalLocalAction.CANCEL,
            localAction("Never mind")
        )
        assertEquals(
            EditTaskRelativeProposalLocalAction.REPEAT,
            localAction("Repeat the proposal", explicitRepeat = true)
        )
        assertEquals(
            EditTaskRelativeProposalLocalAction.CONFIRM,
            localAction("Save it", explicitSave = true)
        )
    }

    @Test
    fun naturalCorrectionsAreNotMistakenForProposalControls() {
        listOf(
            "Actually call it Take Supplements",
            "Change the title instead",
            "Delete this task instead",
            "No, call it Take Supplements instead",
            "Two hours later",
            "Same time tomorrow",
            "Actually make it Sunday at 8 PM",
            "The weather is nice"
        ).forEach { input ->
            assertNull(input, localAction(input))
        }
    }

    @Test
    fun generalMovesStayInEditSemanticPathDuringRelativeProposal() = runBlocking {
        val cases = listOf(
            Triple(
                "Actually call it Take Supplements",
                json("CHANGE_TITLE", title = "Take Supplements"),
                EditTaskSemanticMove.CHANGE_TITLE
            ),
            Triple(
                "Change the title instead",
                json("REQUEST_TITLE_CHANGE"),
                EditTaskSemanticMove.REQUEST_TITLE_CHANGE
            ),
            Triple(
                "Delete this task instead",
                json("DELETE"),
                EditTaskSemanticMove.DELETE
            ),
            Triple(
                "No, call it Take Supplements instead",
                json("CHANGE_TITLE", title = "Take Supplements"),
                EditTaskSemanticMove.CHANGE_TITLE
            ),
            Triple(
                "The weather is nice",
                json("UNKNOWN"),
                EditTaskSemanticMove.UNKNOWN
            )
        )

        cases.forEach { (input, agentResponse, expectedMove) ->
            val result = EditTaskSemanticOrchestrator(QueueClient(agentResponse)).resolve(
                input,
                context()
            )
            assertEquals(expectedMove, result.move)
            assertEquals(
                EditTaskRelativeProposalSemanticRoute.EDIT_SEMANTIC,
                EditTaskRelativeProposalRoutingPolicy.semanticRoute(result)
            )
        }
    }

    @Test
    fun temporalMovesUseSpecialisedRelativeCorrectionPath() = runBlocking {
        val cases = listOf(
            Triple(
                "Two hours later",
                json("CHANGE_TIME", time = "two hours later"),
                RelativeTemporalExpectedField.TIME
            ),
            Triple(
                "Same time tomorrow",
                json("CHANGE_DATE", date = "same time tomorrow"),
                RelativeTemporalExpectedField.DATE
            ),
            Triple(
                "Actually make it Sunday at 8 PM",
                json("CHANGE_SCHEDULE", date = "Sunday", time = "8 PM"),
                RelativeTemporalExpectedField.SCHEDULE
            )
        )

        cases.forEach { (input, agentResponse, expectedField) ->
            val result = EditTaskSemanticOrchestrator(QueueClient(agentResponse)).resolve(
                input,
                context()
            )
            assertEquals(
                EditTaskRelativeProposalSemanticRoute.RELATIVE_TEMPORAL_CORRECTION,
                EditTaskRelativeProposalRoutingPolicy.semanticRoute(result)
            )
            assertEquals(
                expectedField,
                EditTaskRelativeProposalRoutingPolicy.expectedTemporalField(result)
            )
        }
    }

    @Test
    fun proposalRevisionChangeMakesCapturedSemanticResponseStale() {
        assertTrue(
            EditTaskRelativeProposalRoutingPolicy.isCurrentProposal(
                capturedRevision = 4,
                currentRevision = 4,
                currentState = RelativeTemporalProposalState.ACTIVE
            )
        )
        assertTrue(
            !EditTaskRelativeProposalRoutingPolicy.isCurrentProposal(
                capturedRevision = 4,
                currentRevision = 5,
                currentState = RelativeTemporalProposalState.ACTIVE
            )
        )
        assertTrue(
            !EditTaskRelativeProposalRoutingPolicy.isCurrentProposal(
                capturedRevision = 4,
                currentRevision = 4,
                currentState = RelativeTemporalProposalState.CANCELLED
            )
        )
    }

    private fun localAction(
        input: String,
        explicitSave: Boolean = false,
        explicitCancellation: Boolean = false,
        explicitRepeat: Boolean = false,
        explicitConversationExit: Boolean = false
    ) = EditTaskRelativeProposalRoutingPolicy.localAction(
        normalizedText = input,
        signals = EditTaskRelativeProposalControlSignals(
            explicitSave = explicitSave,
            explicitCancellation = explicitCancellation,
            explicitRepeat = explicitRepeat,
            explicitConversationExit = explicitConversationExit
        )
    )

    private class QueueClient(private val response: String) : EditTaskSemanticClient {
        override suspend fun interpretEditTaskMove(
            userText: String,
            contextSummary: String
        ): String = response
    }

    private companion object {
        fun context() = EditTaskAgentContext(
            interactionState =
                EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION,
            draftRevision = 5,
            pendingFieldTarget = "NONE",
            temporalClarificationPending = false,
            relativeTemporalProposalActive = true,
            saveInFlight = false,
            deleteInFlight = false,
            currentDraftTitle = "Take Medicine",
            currentDraftDate = "06/09/2026",
            currentDraftTime = "9:00 PM",
            authoritativeOriginalTitle = "Take Medicine",
            authoritativeOriginalDate = "05/09/2026",
            authoritativeOriginalTime = "9:00 PM",
            currentLocalDate = "05/09/2026",
            currentLocalTime = "1:00 AM",
            timezone = "Asia/Kuala_Lumpur",
            allowedMoves = EditTaskAgentContext.allowedMoves(
                EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION
            )
        )

        fun json(
            move: String,
            title: String = "",
            date: String = "",
            time: String = ""
        ): String =
            """{"move":"$move","title":"$title","date_text":"$date","time_text":"$time","confidence":0.97}"""
    }
}
