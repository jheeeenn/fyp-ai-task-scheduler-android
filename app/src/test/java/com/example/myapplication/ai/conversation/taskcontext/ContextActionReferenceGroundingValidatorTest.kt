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
    fun ordinalDeterministicallyRequiresSecondRef() {
        val accepted = validate("move the second one to Friday", "T2")
        val rejected = validate("move the second one to Friday", "T1")

        assertEquals(ContextActionReferenceGroundingResult.VALID_ORDINAL, accepted.result)
        assertEquals(ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH, rejected.result)
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
    ) = ContextActionReferenceGroundingValidator.validate(
        normalizedText = text,
        decision = decision(selectedRef),
        capturedSnapshot = snapshot,
        currentFocus = focus
    )

    private fun decision(ref: String) = ConversationDecision(
        route = ConversationRoute.CONTEXT_ACTION,
        contextRef = ref,
        contextAction = ConversationContextAction.RESCHEDULE,
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
