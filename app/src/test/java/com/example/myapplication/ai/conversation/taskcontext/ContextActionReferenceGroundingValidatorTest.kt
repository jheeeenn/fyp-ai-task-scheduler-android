package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextActionReferenceGroundingValidatorTest {
    @Test
    fun strictTaskDetailImplicitFocusAcceptsMatchingOrBlankModelRef() {
        val detailSnapshot = snapshot.copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(item("T1", "Medicine"))
        )
        val detailFocus = focus("T1")

        listOf("T1", "").forEach { selectedRef ->
            val result = validateAgainst(
                text = "reschedule the date to today",
                selectedRef = selectedRef,
                capturedSnapshot = detailSnapshot,
                focus = detailFocus
            )

            assertEquals(
                ContextActionReferenceGroundingResult.VALID_TASK_DETAIL_IMPLICIT_FOCUS,
                result.result
            )
            assertEquals("T1", result.ref)
            assertTrue(result.isValid)
        }
    }

    @Test
    fun strictTaskDetailImplicitFocusRejectsConflictingModelRef() {
        val detailSnapshot = snapshot.copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(item("T1", "Medicine"))
        )
        val result = validateAgainst(
            text = "reschedule the date to today",
            selectedRef = "T2",
            capturedSnapshot = detailSnapshot,
            focus = focus("T1")
        )

        assertEquals(ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH, result.result)
        assertFalse(result.isValid)
    }

    @Test
    fun strictTaskDetailImplicitFocusRequiresAvailableCurrentFocus() {
        val detailSnapshot = snapshot.copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(item("T1", "Medicine"))
        )
        val missing = validateAgainst(
            text = "reschedule the date to today",
            selectedRef = "T1",
            capturedSnapshot = detailSnapshot,
            focus = null
        )
        val unavailable = validateAgainst(
            text = "reschedule the date to today",
            selectedRef = "T1",
            capturedSnapshot = detailSnapshot,
            focus = focus("T1").copy(available = false)
        )
        val stale = validateAgainst(
            text = "reschedule the date to today",
            selectedRef = "T1",
            capturedSnapshot = detailSnapshot,
            focus = focus("T1").copy(generation = detailSnapshot.generation - 1)
        )
        val missingRef = validateAgainst(
            text = "reschedule the date to today",
            selectedRef = "T1",
            capturedSnapshot = detailSnapshot,
            focus = focus("T1").copy(ref = "T2")
        )

        assertEquals(ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS, missing.result)
        assertEquals(
            ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS,
            unavailable.result
        )
        assertEquals(ContextActionReferenceGroundingResult.STALE_FOCUS, stale.result)
        assertEquals(ContextActionReferenceGroundingResult.STALE_FOCUS, missingRef.result)
        assertFalse(missing.isValid)
        assertFalse(unavailable.isValid)
        assertFalse(stale.isValid)
        assertFalse(missingRef.isValid)
    }

    @Test
    fun implicitFocusAuthorityDoesNotApplyToRecentQueryResults() {
        val oneResultSnapshot = snapshot.copy(items = listOf(item("T1", "Medicine")))
        val oneResult = validateAgainst(
            text = "reschedule the date to today",
            selectedRef = "T1",
            capturedSnapshot = oneResultSnapshot,
            focus = focus("T1")
        )
        val multipleResults = validateAgainst(
            text = "reschedule the date to today",
            selectedRef = "T1",
            capturedSnapshot = snapshot,
            focus = focus("T1")
        )

        assertEquals(ContextActionReferenceGroundingResult.NO_REFERENCE_EVIDENCE, oneResult.result)
        assertEquals(
            ContextActionReferenceGroundingResult.NO_REFERENCE_EVIDENCE,
            multipleResults.result
        )
        assertFalse(oneResult.isValid)
        assertFalse(multipleResults.isValid)
    }

    @Test
    fun strictTaskDetailImplicitFocusGroundsOtherSemanticContextActions() {
        val detailSnapshot = snapshot.copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(item("T1", "Medicine"))
        )
        val cases = listOf(
            "change the time to 5 PM" to ConversationContextAction.RESCHEDULE,
            "change the title to Buy Milk" to ConversationContextAction.UPDATE,
            "mark as done" to ConversationContextAction.MARK_DONE,
            "mark as completed" to ConversationContextAction.MARK_DONE
        )

        cases.forEach { (text, action) ->
            val result = validateAgainst(
                text = text,
                selectedRef = "T1",
                capturedSnapshot = detailSnapshot,
                focus = focus("T1"),
                action = action
            )

            assertEquals(
                text,
                ContextActionReferenceGroundingResult.VALID_TASK_DETAIL_IMPLICIT_FOCUS,
                result.result
            )
            assertEquals(text, "T1", result.ref)
        }
    }

    @Test
    fun taskDetailCompletionAcceptsBlankModelRefFromCurrentFocus() {
        val detailSnapshot = snapshot.copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(item("T1", "Software Revision"))
        )
        val detailFocus = focus("T1")
        val result = validateAgainst(
            text = "mark it done",
            selectedRef = "",
            capturedSnapshot = detailSnapshot,
            focus = detailFocus
        )

        assertEquals(ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS, result.result)
        assertEquals("T1", result.ref)
    }

    @Test
    fun taskDetailCompletionStillRejectsConflictingModelRef() {
        val detailSnapshot = snapshot.copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(item("T1", "Software Revision"), item("T2", "Other"))
        )
        val result = validateAgainst(
            text = "mark it done",
            selectedRef = "T2",
            capturedSnapshot = detailSnapshot,
            focus = focus("T1")
        )

        assertEquals(ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH, result.result)
    }

    @Test
    fun ordinalAndUniqueTitleMayCanonicalizeBlankModelRef() {
        val ordinal = validate("mark the second one done", "")
        val title = validate("mark Software Revision done", "")

        assertEquals(ContextActionReferenceGroundingResult.VALID_ORDINAL, ordinal.result)
        assertEquals("T2", ordinal.ref)
        assertEquals(ContextActionReferenceGroundingResult.VALID_UNIQUE_TITLE, title.result)
        assertEquals("T2", title.ref)
    }

    @Test
    fun pronounWithoutFocusWinsOverDestinationTitleMatch() {
        val destinationTitleSnapshot = snapshot.copy(
            items = listOf(item("T1", "Friday"), item("T2", "Software Revision"))
        )
        val result = validateAgainst(
            text = "move it to Friday",
            selectedRef = "T1",
            capturedSnapshot = destinationTitleSnapshot,
            focus = null
        )

        assertEquals(ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS, result.result)
        assertFalse(result.isValid)
    }

    @Test
    fun pronounWithoutFocusWinsOverReplacementTitleMatch() {
        val result = validateAgainst(
            text = "rename it to Software Revision",
            selectedRef = "T2",
            capturedSnapshot = snapshot,
            focus = null
        )

        assertEquals(ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS, result.result)
        assertFalse(result.isValid)
    }

    @Test
    fun validFocusWinsOverDestinationTitleMatch() {
        val destinationTitleSnapshot = snapshot.copy(
            items = listOf(item("T1", "Friday"), item("T2", "Software Revision"))
        )
        val result = validateAgainst(
            text = "move it to Friday",
            selectedRef = "T2",
            capturedSnapshot = destinationTitleSnapshot,
            focus = focus("T2")
        )

        assertEquals(ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS, result.result)
        assertEquals("T2", result.ref)
    }

    @Test
    fun titleGroundingStillAppliesWhenNoPronounIsPresent() {
        val titledSnapshot = snapshot.copy(
            items = listOf(item("T1", "Friday"), item("T2", "Software Revision"))
        )
        val result = validateAgainst(
            text = "move Friday to Monday",
            selectedRef = "T1",
            capturedSnapshot = titledSnapshot,
            focus = null
        )

        assertEquals(ContextActionReferenceGroundingResult.VALID_UNIQUE_TITLE, result.result)
        assertEquals("T1", result.ref)
    }

    @Test
    fun noFocusPronounCannotAcceptT1OrAnyDefault() {
        val result = validate("move it to Friday", "T1", focus = null)
        assertEquals(ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS, result.result)
        assertFalse(result.isValid)
    }

    @Test
    fun validFocusT2AcceptsOnlyT2() {
        val accepted = validate("move it to Friday", "T2", focus = focus("T2"))
        val rejected = validate("move it to Friday", "T1", focus = focus("T2"))

        assertEquals(ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS, accepted.result)
        assertEquals("T2", accepted.ref)
        assertTrue(accepted.isValid)
        assertEquals(ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH, rejected.result)
    }

    @Test
    fun contextualDeletePronounsRequireAndUseCurrentFocus() {
        listOf("delete this task", "remove it").forEach { utterance ->
            val accepted = validate(utterance, "T1", focus = focus("T1"))
            assertEquals(
                ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS,
                accepted.result
            )
            assertEquals("T1", accepted.ref)
        }

        val missingFocus = validate("delete this task", "T1", focus = null)
        assertEquals(
            ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS,
            missingFocus.result
        )
        assertFalse(missingFocus.isValid)
    }

    @Test
    fun taskDetailBareThisGroundsOnlyTheModelSelectedCurrentFocus() {
        val detailSnapshot = snapshot.copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(item("T1", "Medicine"))
        )
        val detailFocus = focus("T1")
        val modelDelete = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = "T1",
            contextAction = ConversationContextAction.DELETE,
            confidence = 0.97
        )

        val result = ContextActionReferenceGroundingValidator.validate(
            normalizedText = "delete this",
            decision = modelDelete,
            capturedSnapshot = detailSnapshot,
            currentFocus = detailFocus
        )

        assertEquals(ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS, result.result)
        assertEquals("T1", result.ref)
    }

    @Test
    fun bareThisNeverDefaultsToT1InAnUnfocusedOrMultiItemContext() {
        val noFocus = validate("delete this", "T1", focus = null)
        val multiItemFocus = validate("delete this", "T1", focus = focus("T1"))

        assertEquals(ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS, noFocus.result)
        assertEquals(
            ContextActionReferenceGroundingResult.NO_REFERENCE_EVIDENCE,
            multiItemFocus.result
        )
        assertFalse(noFocus.isValid)
        assertFalse(multiItemFocus.isValid)
    }

    @Test
    fun ordinalDeterministicallyRequiresSecondRef() {
        val accepted = validate("move the second one to Friday", "T2")
        val rejected = validate("move the second one to Friday", "T1")

        assertEquals(ContextActionReferenceGroundingResult.VALID_ORDINAL, accepted.result)
        assertEquals(ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH, rejected.result)
    }

    @Test
    fun formerAndLatterAreBoundedToAnExactPair() {
        assertEquals(
            ContextActionReferenceGroundingResult.VALID_ORDINAL,
            validate("shift the latter task by an hour", "T2").result
        )
        assertEquals(
            ContextActionReferenceGroundingResult.VALID_ORDINAL,
            validate("move the former task to Friday", "T1").result
        )
        val threeItems = snapshot.copy(
            items = snapshot.items + item("T3", "Third task")
        )
        val ambiguous = validateAgainst(
            text = "move the latter task to Friday",
            selectedRef = "T3",
            capturedSnapshot = threeItems,
            focus = null
        )
        assertFalse(ambiguous.isValid)
    }

    @Test
    fun explicitTemporaryRefRequiresSameModelRef() {
        assertEquals(
            ContextActionReferenceGroundingResult.VALID_EXPLICIT_REF,
            validate("move T2 to Friday", "T2").result
        )
        assertEquals(
            ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH,
            validate("move T2 to Friday", "T1").result
        )
    }

    @Test
    fun uniqueSuppliedTitleGroundsItsRef() {
        val result = validate("move Software Revision to Friday", "T2")
        assertEquals(ContextActionReferenceGroundingResult.VALID_UNIQUE_TITLE, result.result)
        assertEquals("T2", result.ref)
    }

    @Test
    fun duplicateSuppliedTitleIsAmbiguous() {
        val duplicateSnapshot = snapshot.copy(
            items = listOf(item("T1", "Software Revision"), item("T2", "Software Revision"))
        )
        val result = ContextActionReferenceGroundingValidator.validate(
            normalizedText = "move Software Revision to Friday",
            decision = decision("T1"),
            capturedSnapshot = duplicateSnapshot,
            currentFocus = null
        )
        assertEquals(ContextActionReferenceGroundingResult.AMBIGUOUS_TITLE, result.result)
    }

    @Test
    fun staleFocusAndNoReferenceEvidenceFailClosed() {
        assertEquals(
            ContextActionReferenceGroundingResult.STALE_FOCUS,
            validate("move it to Friday", "T2", focus("T2").copy(generation = 3)).result
        )
        assertEquals(
            ContextActionReferenceGroundingResult.NO_REFERENCE_EVIDENCE,
            validate("reschedule to Friday", "T1").result
        )
    }

    private fun validate(
        text: String,
        selectedRef: String,
        focus: ConversationContextFocus? = null
    ) = validateAgainst(
        text = text,
        selectedRef = selectedRef,
        capturedSnapshot = snapshot,
        focus = focus
    )

    private fun validateAgainst(
        text: String,
        selectedRef: String,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        focus: ConversationContextFocus?,
        action: ConversationContextAction = ConversationContextAction.RESCHEDULE
    ) = ContextActionReferenceGroundingValidator.validate(
        normalizedText = text,
        decision = decision(selectedRef, action),
        capturedSnapshot = capturedSnapshot,
        currentFocus = focus
    )

    private fun decision(
        ref: String,
        action: ConversationContextAction = ConversationContextAction.RESCHEDULE
    ) = ConversationDecision(
        route = ConversationRoute.CONTEXT_ACTION,
        contextRef = ref,
        contextAction = action,
        confidence = 0.97
    )

    private fun focus(ref: String) = ConversationContextFocus(
        available = true,
        ref = ref,
        generation = 4,
        detail = ConversationContextDetail.SUMMARY,
        title = snapshot.items.first { it.ref == ref }.title
    )

    private companion object {
        val snapshot = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.RECENT_QUERY_RESULTS,
            generation = 4,
            items = listOf(item("T1", "Medicine"), item("T2", "Software Revision")),
            truncated = false
        )

        fun item(ref: String, title: String) = ReadOnlyTaskContextItem(
            ref = ref,
            title = title,
            dueDate = "",
            dueTime = "",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )
    }
}
