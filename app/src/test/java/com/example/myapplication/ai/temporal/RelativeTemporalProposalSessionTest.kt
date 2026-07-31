package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTemporalProposalSessionTest {
    private val original = ExactTemporalSchedule("31/07/2026", "9:00 AM")
    private val first = ExactTemporalSchedule("31/07/2026", "9:30 AM")

    @Test
    fun staleRevisionCannotReplaceLatestProposal() {
        val session = RelativeTemporalProposalSession(original, first)
        val stale = session.beginCorrection()
        session.invalidatePendingCorrection()
        val current = session.beginCorrection()

        assertEquals(
            RelativeTemporalRevisionResult.STALE_REQUEST,
            session.applyCorrection(stale, ExactTemporalSchedule("31/07/2026", "10:00 AM"))
        )
        assertEquals(
            RelativeTemporalRevisionResult.APPLIED,
            session.applyCorrection(current, ExactTemporalSchedule("31/07/2026", "10:30 AM"))
        )
        assertEquals("10:30 AM", session.currentProposal.time)
        assertEquals(2, session.revision)
    }

    @Test
    fun confirmationAppliesOnlyToLatestRevision() {
        val session = RelativeTemporalProposalSession(original, first)
        val token = session.beginCorrection()
        session.applyCorrection(token, ExactTemporalSchedule("31/07/2026", "10:00 AM"))

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
        val session = RelativeTemporalProposalSession(original, first)
        val claimed = session.claimSave(1, "Original title") as RelativeTemporalSaveClaimResult.Claimed

        session.replaceFromManualEdit(ExactTemporalSchedule("01/08/2026", "10:30 AM"))

        assertEquals(1, session.revision)
        assertEquals(first, session.currentProposal)
        assertEquals("Original title", claimed.claim.title)
        assertEquals(first, claimed.claim.schedule)
        assertEquals(RelativeTemporalProposalState.SAVING, session.state)
    }

    @Test
    fun correctionAndSecondConfirmationAreRejectedWhileSaving() {
        val session = RelativeTemporalProposalSession(original, first)
        val correction = session.beginCorrection()
        val claimed = session.claimSave(1, "Revision") as RelativeTemporalSaveClaimResult.Claimed

        assertEquals(
            RelativeTemporalRevisionResult.INACTIVE,
            session.applyCorrection(correction, ExactTemporalSchedule("31/07/2026", "10:00 AM"))
        )
        assertTrue(
            session.claimSave(1, "Different title") is RelativeTemporalSaveClaimResult.Inactive
        )
        assertTrue(session.isCurrentSaveClaim(claimed.claim))
    }

    @Test
    fun completionMustMatchTheClaimedRevisionAndClaim() {
        val session = RelativeTemporalProposalSession(original, first)
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
        val session = RelativeTemporalProposalSession(original, first)
        val claimed = session.claimSave(1, "Revision") as RelativeTemporalSaveClaimResult.Claimed

        assertTrue(session.failSave(claimed.claim, retryable = false))
        assertEquals(RelativeTemporalProposalState.CANCELLED, session.state)
        assertFalse(session.completeSave(claimed.claim))
    }

    @Test
    fun cancellationRestoresOriginalAndLeavesNoConfirmableProposal() {
        val session = RelativeTemporalProposalSession(original, first)
        assertEquals(original, session.cancel())
        assertEquals(original, session.currentProposal)
        assertEquals(RelativeTemporalProposalState.CANCELLED, session.state)
        assertTrue(session.proposalForConfirmation(1) is RelativeTemporalConfirmationResult.Inactive)
    }

    @Test
    fun restoreOriginalIsARevisionNotADatabaseMutation() {
        val session = RelativeTemporalProposalSession(original, first)
        val token = session.beginCorrection()
        assertEquals(RelativeTemporalRevisionResult.APPLIED, session.restoreOriginal(token))
        assertEquals(original, session.currentProposal)
        assertEquals(2, session.revision)
        assertEquals(RelativeTemporalProposalState.ACTIVE, session.state)
    }
}
