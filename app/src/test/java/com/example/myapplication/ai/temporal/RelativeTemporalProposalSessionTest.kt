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
        assertFalse(session.markSaved(1))
        assertTrue(session.markSaved(2))
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
