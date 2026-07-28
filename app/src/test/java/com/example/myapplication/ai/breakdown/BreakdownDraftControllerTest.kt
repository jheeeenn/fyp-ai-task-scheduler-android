package com.example.myapplication.ai.breakdown

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakdownDraftControllerTest {
    @Test
    fun ambiguousSelectionPreservesValidatedPlanUntilExistingRootIsChosen() {
        val controller = BreakdownDraftController()
        val resolving = begin(controller)
        val first = root(11, "Final year project report")
        val second = root(12, "Final year project presentation")

        val choosing = controller.applyAmbiguousTargets(
            resolving.generation,
            listOf(first, second)
        )

        assertTrue(choosing is BreakdownDraftUpdate.ChoosingTarget)
        assertEquals(BreakdownDraftState.CHOOSING_TARGET, controller.state)
        assertEquals(steps(), controller.draft!!.proposedSubtasks)
        assertEquals(
            second,
            controller.resolveTargetChoice("second", listOf(first, second))
        )

        val review = controller.applyExistingRoot(
            resolving.generation,
            second,
            hasSubtasks = false
        ) as BreakdownDraftUpdate.Review
        assertEquals(BreakdownDraftMode.EXISTING_ROOT, review.draft.mode)
        assertEquals(steps(), review.draft.proposedSubtasks)
    }

    @Test
    fun existingRootScheduleIsInheritedAndExistingChildrenRejectWithoutSave() {
        val controller = BreakdownDraftController()
        val resolving = begin(controller)
        val parent = root(
            id = 30,
            title = "Final year project",
            date = "30/07/2026",
            time = "4:00 PM"
        )
        val review = controller.applyExistingRoot(
            resolving.generation,
            parent,
            hasSubtasks = false
        ) as BreakdownDraftUpdate.Review

        assertEquals(parent.dueDate, review.draft.dateText)
        assertEquals(parent.dueTime, review.draft.timeText)
        assertEquals(30L, review.draft.resolvedParentTaskId())

        val blocked = BreakdownDraftController()
        val blockedDraft = begin(blocked)
        assertEquals(
            BreakdownDraftUpdate.AlreadyHasSubtasks,
            blocked.applyExistingRoot(
                blockedDraft.generation,
                parent,
                hasSubtasks = true
            )
        )
        assertEquals(BreakdownDraftState.NONE, blocked.state)
        assertNull(blocked.markSaving())
    }

    @Test
    fun cancellationBeforeConfirmationAndRepeatedConfirmationAreMutationSafe() {
        val cancelled = BreakdownDraftController()
        val cancelledDraft = begin(cancelled)
        cancelled.applyNewRoot(cancelledDraft.generation)
        assertTrue(cancelled.clear())
        assertEquals(BreakdownDraftState.NONE, cancelled.state)
        assertNull(cancelled.markSaving("30/07/2026", "4:00 PM"))

        val controller = BreakdownDraftController()
        val draft = begin(controller)
        controller.applyNewRoot(draft.generation)
        val firstSave = controller.markSaving("30/07/2026", "4:00 PM")

        assertTrue(firstSave != null)
        assertEquals(BreakdownDraftState.SAVING, controller.state)
        assertNull(controller.markSaving("30/07/2026", "4:00 PM"))
        assertFalse(controller.clear())
        assertTrue(controller.completeSaving(firstSave!!.saveGeneration))
        assertEquals(BreakdownDraftState.NONE, controller.state)
    }

    @Test
    fun staleGenerationAndRevisionCannotOverwriteNewerDraft() {
        val controller = BreakdownDraftController()
        val first = begin(controller)
        controller.applyNewRoot(first.generation)
        val firstRevision = controller.draft!!.revision

        val revised = controller.applyRevision(
            first.generation,
            firstRevision,
            listOf("Review the literature", "Draft the implementation")
        ) as BreakdownDraftUpdate.Review
        assertEquals(firstRevision + 1, revised.draft.revision)

        assertEquals(
            BreakdownDraftUpdate.Stale,
            controller.applyRevision(
                first.generation,
                firstRevision,
                listOf("Stale one", "Stale two")
            )
        )
        assertEquals(
            listOf("Review the literature", "Draft the implementation"),
            controller.draft!!.proposedSubtasks
        )

        val second = begin(
            controller,
            listOf("Prepare slides", "Practise delivery")
        )
        assertEquals(
            BreakdownDraftUpdate.Stale,
            controller.applyNewRoot(first.generation)
        )
        assertEquals(second.generation, controller.draft!!.generation)
    }

    private fun begin(
        controller: BreakdownDraftController,
        plan: List<String> = steps()
    ): PendingBreakdownDraft {
        val update = controller.beginDraft(
            parentTitle = "Final year project",
            proposedSubtasks = plan,
            originalRequest = "break down my final year project",
            dateText = "next Friday",
            timeText = "4 PM"
        )
        return (update as BreakdownDraftUpdate.Resolving).draft
    }

    private fun steps() = listOf("Review literature", "Draft methodology")

    private fun root(
        id: Long,
        title: String,
        date: String? = null,
        time: String? = null
    ) = TaskEntity(
        id = id,
        title = title,
        dueDate = date,
        dueTime = time
    )
}
