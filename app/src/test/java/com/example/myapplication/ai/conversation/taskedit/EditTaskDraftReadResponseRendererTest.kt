package com.example.myapplication.ai.conversation.taskedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTaskDraftReadResponseRendererTest {
    @Test
    fun readTimeDuringTitleCollectionAnswersThenResumesTitleQuestion() {
        val response = EditTaskDraftReadResponseRenderer.render(
            move = EditTaskSemanticMove.READ_TIME,
            title = "Take medicine",
            date = "06/09/2026",
            time = "8:00 PM",
            interactionState = EditTaskInteractionState.WAITING_FOR_TITLE,
            pendingFieldTarget = EditFieldTarget.TITLE
        )

        assertEquals(
            "It's currently set for 8:00 PM. What would you like the new title to be?",
            response
        )
    }

    @Test
    fun readMovesUseCurrentAndroidDraftValues() {
        assertEquals(
            "The current title is Buy medicine.",
            render(EditTaskSemanticMove.READ_TITLE, title = "Buy medicine")
        )
        assertTrue(
            render(EditTaskSemanticMove.READ_DATE, date = "06/09/2026")
                .startsWith("It's currently set for Sunday, 6 September 2026.")
        )
        assertEquals(
            "It's currently scheduled for Sunday, 6 September 2026 at 8:00 PM.",
            render(
                EditTaskSemanticMove.READ_SCHEDULE,
                date = "06/09/2026",
                time = "8:00 PM"
            )
        )
    }

    @Test
    fun missingDraftValuesAreExplainedNaturally() {
        assertEquals(
            "This task doesn't have a time set yet.",
            render(EditTaskSemanticMove.READ_TIME)
        )
        assertEquals(
            "This task doesn't have a date or time set yet.",
            render(EditTaskSemanticMove.READ_SCHEDULE)
        )
    }

    @Test
    fun saveConfirmationReadRestoresTheConfirmationQuestion() {
        assertEquals(
            "The current title is Take medicine. Would you like to save these changes?",
            EditTaskDraftReadResponseRenderer.render(
                move = EditTaskSemanticMove.READ_TITLE,
                title = "Take medicine",
                date = "06/09/2026",
                time = "8:00 PM",
                interactionState = EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION,
                pendingFieldTarget = EditFieldTarget.NONE
            )
        )
    }

    private fun render(
        move: EditTaskSemanticMove,
        title: String = "",
        date: String? = null,
        time: String? = null
    ) = EditTaskDraftReadResponseRenderer.render(
        move = move,
        title = title,
        date = date,
        time = time,
        interactionState = EditTaskInteractionState.READY_FOR_EDIT,
        pendingFieldTarget = EditFieldTarget.NONE
    )
}
