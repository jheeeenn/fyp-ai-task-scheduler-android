package com.example.myapplication.ai.routine

import com.example.myapplication.ai.routine.saved.SavedRoutineAction
import com.example.myapplication.ai.routine.saved.SavedRoutineCandidate
import com.example.myapplication.ai.routine.saved.SavedRoutineChoice
import com.example.myapplication.ai.routine.saved.SavedRoutineInteractionController
import com.example.myapplication.ai.routine.saved.SavedRoutineInteractionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRoutineInteractionControllerTest {
    @Test
    fun ambiguitySupportsOrdinalsTitlesAndCancelWithoutExposingIds() {
        val controller = SavedRoutineInteractionController()
        val first = controller.begin(SavedRoutineAction.RUN, "tomorrow")
        assertTrue(
            controller.chooseCandidates(
                first.generation,
                listOf(
                    SavedRoutineCandidate(41, "Morning routine"),
                    SavedRoutineCandidate(82, "Study routine")
                )
            )
        )
        assertEquals(
            SavedRoutineChoice.Selected(82),
            controller.choose("second option")
        )

        val byTitle = controller.begin(SavedRoutineAction.READ_DETAILS, "")
        controller.chooseCandidates(
            byTitle.generation,
            listOf(
                SavedRoutineCandidate(41, "Morning routine"),
                SavedRoutineCandidate(82, "Study routine")
            )
        )
        assertEquals(
            SavedRoutineChoice.Selected(41),
            controller.choose("morning, routine")
        )

        val cancelled = controller.begin(SavedRoutineAction.DELETE, "")
        controller.chooseCandidates(
            cancelled.generation,
            listOf(
                SavedRoutineCandidate(1, "Duplicate"),
                SavedRoutineCandidate(2, "Duplicate")
            )
        )
        assertEquals(SavedRoutineChoice.Cancelled, controller.choose("cancel"))
        assertEquals(SavedRoutineInteractionState.NONE, controller.state)
        assertTrue(controller.candidates.isEmpty())
    }

    @Test
    fun staleChoiceIsDroppedAndCancellationInvalidatesGeneration() {
        val controller = SavedRoutineInteractionController()
        val stale = controller.begin(SavedRoutineAction.RUN, "tomorrow")
        val current = controller.begin(SavedRoutineAction.LIST, "")

        assertFalse(
            controller.chooseCandidates(
                stale.generation,
                listOf(
                    SavedRoutineCandidate(1, "One"),
                    SavedRoutineCandidate(2, "Two")
                )
            )
        )
        assertTrue(controller.isCurrent(current.generation))
        controller.clear()
        assertFalse(controller.isCurrent(current.generation))
    }

    @Test
    fun deleteRequiresConfirmationAndCanBeClaimedOnlyOnce() {
        val controller = SavedRoutineInteractionController()
        val interaction = controller.begin(SavedRoutineAction.DELETE, "")
        assertTrue(controller.selectSingle(interaction.generation, 91))
        assertTrue(controller.beginDeleteConfirmation(interaction.generation, 91))

        assertEquals(91L, controller.claimDelete())
        assertNull(controller.claimDelete())
    }
}
