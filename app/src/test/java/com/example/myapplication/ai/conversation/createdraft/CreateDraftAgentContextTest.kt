package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateTaskDialogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftAgentContextTest {
    @Test
    fun everyDialogStateMapsToStableInteractionContext() {
        val expected = mapOf(
            CreateTaskDialogState.IDLE to
                    (CreateDraftPreviousAssistantAct.NONE to CreateDraftExpectedResponseKind.TITLE_VALUE),
            CreateTaskDialogState.WAITING_FOR_TITLE to
                    (CreateDraftPreviousAssistantAct.ASKED_FOR_TITLE to CreateDraftExpectedResponseKind.TITLE_VALUE),
            CreateTaskDialogState.WAITING_FOR_DATE to
                    (CreateDraftPreviousAssistantAct.ASKED_FOR_DATE to CreateDraftExpectedResponseKind.DATE_VALUE),
            CreateTaskDialogState.WAITING_FOR_TIME to
                    (CreateDraftPreviousAssistantAct.ASKED_FOR_TIME to CreateDraftExpectedResponseKind.TIME_VALUE),
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD to
                    (CreateDraftPreviousAssistantAct.ASKED_WHICH_FIELD_TO_CHANGE to
                            CreateDraftExpectedResponseKind.FIELD_SELECTION_OR_REPLACEMENT),
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION to
                    (CreateDraftPreviousAssistantAct.ASKED_TO_CONFIRM_SAVE to
                            CreateDraftExpectedResponseKind.CONFIRM_REJECT_OR_CORRECT),
            CreateTaskDialogState.READY_TO_SAVE to
                    (CreateDraftPreviousAssistantAct.SAVE_IN_PROGRESS to CreateDraftExpectedResponseKind.NONE)
        )

        expected.forEach { (state, expectedContext) ->
            val context = CreateDraftAgentContext.capture(
                state = state,
                pendingReplacementField = null,
                hasTitle = false,
                hasSelectedDate = false,
                hasSelectedTime = false,
                localCandidate = CreateDraftMove.Unknown
            )
            assertEquals("state=$state", expectedContext.first, context.previousAssistantAct)
            assertEquals("state=$state", expectedContext.second, context.expectedResponseKind)
        }
    }

    @Test
    fun completeSaveConfirmationIncludesPresentedDraftContext() {
        val context = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = true,
            localCandidate = CreateDraftMove.Unknown
        )
        val text = context.toPromptText()

        assertEquals(CreateDraftPreviousAssistantAct.ASKED_TO_CONFIRM_SAVE, context.previousAssistantAct)
        assertEquals(
            CreateDraftExpectedResponseKind.CONFIRM_REJECT_OR_CORRECT,
            context.expectedResponseKind
        )
        assertTrue(context.draftComplete)
        assertTrue(context.completedDraftWasPresented)
        assertTrue(text.contains("Previous assistant act: ASKED_TO_CONFIRM_SAVE"))
        assertTrue(text.contains("Expected response kind: CONFIRM_REJECT_OR_CORRECT"))
        assertTrue(text.contains("Draft complete: true"))
        assertTrue(text.contains("Completed draft was presented: true"))
    }

    @Test
    fun contextContainsOnlyTrustedDraftPresenceAndState() {
        val text = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.WAITING_FOR_TIME,
            pendingReplacementField = CreateDraftField.TIME,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = false,
            localCandidate = CreateDraftMove.ProvideField(CreateDraftField.TIME, "just 9 am")
        ).toPromptText()

        assertTrue(text.contains("waiting for an exact or interpretable time phrase"))
        assertTrue(text.contains("Expected field: TIME"))
        assertTrue(text.contains("Pending replacement field: TIME"))
        assertTrue(text.contains("Title exists: true"))
        assertTrue(text.contains("Advisory local candidate move: PROVIDE_FIELD"))
        assertTrue(text.contains("Advisory local candidate field: TIME"))
        assertTrue(text.contains("Advisory local candidate has a value: true"))
        assertTrue(text.contains("Advisory local candidate recognised: true"))
        assertTrue(text.contains("local candidate is advisory and not authoritative"))
        assertFalse(text.contains("just 9 am"))
        assertFalse(text.contains("taskId"))
        assertFalse(text.contains("endpoint"))
        assertFalse(text.contains("Room"))
    }

    @Test
    fun readyToSaveContextDisallowsSemanticAgentRequest() {
        val text = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.READY_TO_SAVE,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = true,
            localCandidate = CreateDraftMove.Unknown
        ).toPromptText()
        assertTrue(text.contains("No create-draft semantic request is allowed"))
        assertTrue(text.contains("Allowed moves: UNKNOWN"))
    }

    @Test
    fun allowedMovesExposeReadsAndCombinedScheduleOnlyInBoundedStates() {
        val dateContext = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.WAITING_FOR_DATE,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = false,
            hasSelectedTime = false,
            localCandidate = CreateDraftMove.Unknown
        )
        listOf("PROVIDE_SCHEDULE", "READ_TITLE", "READ_DATE", "READ_TIME", "READ_SCHEDULE")
            .forEach { move -> assertTrue(move, dateContext.allowedMoves.contains(move)) }

        val saveContext = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = true,
            localCandidate = CreateDraftMove.Unknown
        )
        assertTrue(saveContext.allowedMoves.contains("PROVIDE_SCHEDULE"))
        assertTrue(saveContext.allowedMoves.contains("PROVIDE_FIELD"))
        assertTrue(saveContext.allowedMoves.contains("READ_SUMMARY"))
        assertFalse(saveContext.allowedMoves.contains("APPLY_UNSPECIFIED_CORRECTION"))

        val titleContext = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.WAITING_FOR_TITLE,
            pendingReplacementField = null,
            hasTitle = false,
            hasSelectedDate = false,
            hasSelectedTime = false,
            localCandidate = CreateDraftMove.Unknown
        )
        assertFalse(titleContext.allowedMoves.contains("PROVIDE_SCHEDULE"))
    }

    @Test
    fun repairContextTruthfullyDescribesEachBoundedReason() {
        val context = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.WAITING_FOR_DATE,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = false,
            hasSelectedTime = false,
            localCandidate = CreateDraftMove.Unknown
        )

        CreateDraftRepairReason.entries.forEach { reason ->
            val repair = context.toRepairPromptText(reason)
            assertTrue(repair.contains("Semantic repair status: ${reason.name}"))
            assertTrue(repair.contains(reason.instruction))
        }
        assertTrue(
            context.toRepairPromptText(CreateDraftRepairReason.PRIMARY_TEMPORAL_MEANING_INCOMPLETE)
                .contains("PROVIDE_SCHEDULE")
        )
    }

    @Test
    fun internalUnspecifiedFallbackIsNotExposedAsAnAgentMove() {
        val context = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = true,
            localCandidate = CreateDraftMove.ApplyUnspecifiedCorrection("Friday evening")
        )

        assertEquals("UNKNOWN", context.localCandidateMove)
        assertFalse(context.localCandidateRecognised)
        assertFalse(context.toPromptText().contains("APPLY_UNSPECIFIED_CORRECTION"))
    }
}
