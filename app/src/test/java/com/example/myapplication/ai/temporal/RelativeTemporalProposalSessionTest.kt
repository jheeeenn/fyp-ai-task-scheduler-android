package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTemporalProposalSessionTest {
    private val original = ExactTemporalSchedule("31/07/2026", "9:00 AM")
    private val first = ExactTemporalSchedule("31/07/2026", "9:30 AM")
    private val initialSemantic = offsetProposal(30, RelativeTemporalBase.AUTHORITATIVE_TASK)

    @Test
    fun staleRevisionCannotReplaceLatestProposalOrSemanticState() {
        val session = session()
        val stale = session.beginCorrection()
        session.invalidatePendingCorrection()
        val current = session.beginCorrection()
        val replacement = offsetProposal(60, RelativeTemporalBase.AUTHORITATIVE_TASK)

        assertEquals(
            RelativeTemporalRevisionResult.STALE_REQUEST,
            session.applyCorrection(
                stale,
                ExactTemporalSchedule("31/07/2026", "10:00 AM"),
                replacement
            )
        )
        assertEquals(initialSemantic, session.currentSemanticProposal)
        assertEquals(
            RelativeTemporalRevisionResult.APPLIED,
            session.applyCorrection(
                current,
                ExactTemporalSchedule("31/07/2026", "10:30 AM"),
                replacement
            )
        )
        assertEquals("10:30 AM", session.currentProposal.time)
        assertEquals(replacement, session.currentSemanticProposal)
        assertEquals(2, session.revision)
    }

    @Test
    fun initialAndAppliedRevisionsExposeBoundedSemanticContext() {
        val session = session()
        val initialContext = requireNotNull(session.correctionContext())

        assertEquals(RelativeTemporalOperation.KEEP, initialContext.previousDateOperation)
        assertEquals(RelativeTemporalOperation.OFFSET, initialContext.previousTimeOperation)
        assertEquals(RelativeTemporalBase.AUTHORITATIVE_TASK, initialContext.previousRelativeBase)
        assertEquals(30, initialContext.previousTimeOffsetMinutes)
        assertEquals(1, initialContext.proposalRevision)

        val cumulative = offsetProposal(60, RelativeTemporalBase.CURRENT_PROPOSAL)
        val token = session.beginCorrection()
        session.applyCorrection(
            token,
            ExactTemporalSchedule("31/07/2026", "10:30 AM"),
            cumulative
        )
        val revisedContext = requireNotNull(session.correctionContext())
        assertEquals(RelativeTemporalBase.CURRENT_PROPOSAL, revisedContext.previousRelativeBase)
        assertEquals(60, revisedContext.previousTimeOffsetMinutes)
        assertEquals(2, revisedContext.proposalRevision)
    }

    @Test
    fun confirmationAppliesOnlyToLatestRevision() {
        val session = session()
        val token = session.beginCorrection()
        session.applyCorrection(
            token,
            ExactTemporalSchedule("31/07/2026", "10:00 AM"),
            offsetProposal(60, RelativeTemporalBase.AUTHORITATIVE_TASK)
        )

        assertTrue(session.proposalForConfirmation(1) is RelativeTemporalConfirmationResult.StaleRevision)
        val latest = session.proposalForConfirmation(2)
        assertTrue(latest is RelativeTemporalConfirmationResult.Latest)
        assertEquals("10:00 AM", (latest as RelativeTemporalConfirmationResult.Latest).schedule.time)
        assertTrue(session.claimSave(1, "Revision") is RelativeTemporalSaveClaimResult.StaleRevision)
        val claim = session.claimSave(2, "Revision") as RelativeTemporalSaveClaimResult.Claimed
        assertTrue(session.completeSave(claim.claim))
        assertEquals(RelativeTemporalProposalState.SAVED, session.state)
    }

    @Test
    fun saveClaimFreezesRevisionOneWhenManualRevisionTwoIsAttempted() {
        val session = session()
        val claimed = session.claimSave(1, "Original title") as RelativeTemporalSaveClaimResult.Claimed

        session.replaceFromManualEdit(ExactTemporalSchedule("01/08/2026", "10:30 AM"))

        assertEquals(1, session.revision)
        assertEquals(first, session.currentProposal)
        assertEquals(initialSemantic, session.currentSemanticProposal)
        assertEquals("Original title", claimed.claim.title)
        assertEquals(first, claimed.claim.schedule)
        assertEquals(RelativeTemporalProposalState.SAVING, session.state)
    }

    @Test
    fun activeManualEditInvalidatesSemanticContext() {
        val session = session()

        session.replaceFromManualEdit(ExactTemporalSchedule("01/08/2026", "10:30 AM"))

        assertEquals(2, session.revision)
        assertNull(session.currentSemanticProposal)
        assertNull(session.correctionContext())
    }

    @Test
    fun correctionAndSecondConfirmationAreRejectedWhileSaving() {
        val session = session()
        val correction = session.beginCorrection()
        val claimed = session.claimSave(1, "Revision") as RelativeTemporalSaveClaimResult.Claimed

        assertEquals(
            RelativeTemporalRevisionResult.INACTIVE,
            session.applyCorrection(
                correction,
                ExactTemporalSchedule("31/07/2026", "10:00 AM"),
                offsetProposal(60, RelativeTemporalBase.AUTHORITATIVE_TASK)
            )
        )
        assertEquals(initialSemantic, session.currentSemanticProposal)
        assertTrue(
            session.claimSave(1, "Different title") is RelativeTemporalSaveClaimResult.Inactive
        )
        assertTrue(session.isCurrentSaveClaim(claimed.claim))
    }

    @Test
    fun completionMustMatchTheClaimedRevisionAndClaim() {
        val session = session()
        val claimed = session.claimSave(1, "Revision") as RelativeTemporalSaveClaimResult.Claimed
        val wrongClaim = claimed.claim.copy(revision = 2)
        val copiedClaim = claimed.claim.copy()

        assertFalse(session.completeSave(wrongClaim))
        assertFalse(session.completeSave(copiedClaim))
        assertEquals(RelativeTemporalProposalState.SAVING, session.state)
        assertTrue(session.completeSave(claimed.claim))
        assertEquals(RelativeTemporalProposalState.SAVED, session.state)
    }

    @Test
    fun failedConditionalMutationCannotEmitSavedState() {
        val session = session()
        val claimed = session.claimSave(1, "Revision") as RelativeTemporalSaveClaimResult.Claimed

        assertTrue(session.failSave(claimed.claim, retryable = false))
        assertEquals(RelativeTemporalProposalState.CANCELLED, session.state)
        assertFalse(session.completeSave(claimed.claim))
    }

    @Test
    fun cancellationRestoresOriginalAndClearsSemanticContext() {
        val session = session()
        assertEquals(original, session.cancel())
        assertEquals(original, session.currentProposal)
        assertNull(session.currentSemanticProposal)
        assertEquals(RelativeTemporalProposalState.CANCELLED, session.state)
        assertTrue(session.proposalForConfirmation(1) is RelativeTemporalConfirmationResult.Inactive)
    }

    @Test
    fun restoreOriginalIsARevisionNotADatabaseMutation() {
        val session = session()
        val token = session.beginCorrection()
        assertEquals(RelativeTemporalRevisionResult.APPLIED, session.restoreOriginal(token))
        assertEquals(original, session.currentProposal)
        assertEquals(RelativeTemporalBase.AUTHORITATIVE_TASK, session.currentSemanticProposal?.relativeBase)
        assertEquals(2, session.revision)
        assertEquals(RelativeTemporalProposalState.ACTIVE, session.state)
    }

    private fun session() = RelativeTemporalProposalSession(
        authoritativeOriginal = original,
        initialProposal = first,
        initialSemanticProposal = initialSemantic
    )

    private fun offsetProposal(
        minutes: Int,
        base: RelativeTemporalBase
    ) = RelativeTemporalProposal(
        dateOperation = RelativeTemporalOperation.KEEP,
        timeOperation = RelativeTemporalOperation.OFFSET,
        relativeBase = base,
        replacementDateText = "",
        replacementTimeText = "",
        dateOffsetDays = 0,
        timeOffsetMinutes = minutes,
        confidence = 0.98,
        needClarification = false
    )
}
