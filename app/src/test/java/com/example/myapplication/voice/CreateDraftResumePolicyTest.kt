package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateDraftResumePolicyTest {
    @Test
    fun resumesAtFirstMissingAuthoritativeDraftField() {
        assertEquals(
            CreateTaskDialogState.WAITING_FOR_TITLE,
            CreateDraftResumePolicy.nextState(false, false, false)
        )
        assertEquals(
            CreateTaskDialogState.WAITING_FOR_DATE,
            CreateDraftResumePolicy.nextState(true, false, false)
        )
        assertEquals(
            CreateTaskDialogState.WAITING_FOR_TIME,
            CreateDraftResumePolicy.nextState(true, true, false)
        )
        assertEquals(
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
            CreateDraftResumePolicy.nextState(true, true, true)
        )
    }

    @Test
    fun completeResumedDraftAcceptsCorrectionInsteadOfTreatingItAsTitle() {
        val resumed = CreateDraftResumePolicy.nextState(true, true, true)
        val move = CreateDraftMoveInterpreter().interpret("change the date to tomorrow", resumed)

        assertEquals(CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION, resumed)
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.DATE, "tomorrow"),
            move
        )
    }
}
