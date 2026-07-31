package com.example.myapplication.ai.temporal

enum class RelativeTemporalProposalState {
    ACTIVE,
    CANCELLED,
    SAVED
}

class RelativeTemporalCorrectionToken internal constructor(
    val requestGeneration: Long,
    val baseRevision: Int
)

enum class RelativeTemporalRevisionResult {
    APPLIED,
    STALE_REQUEST,
    INACTIVE
}

sealed class RelativeTemporalConfirmationResult {
    data class Latest(
        val revision: Int,
        val schedule: ExactTemporalSchedule
    ) : RelativeTemporalConfirmationResult()

    data object StaleRevision : RelativeTemporalConfirmationResult()
    data object Inactive : RelativeTemporalConfirmationResult()
}

/** Pure revision state for one authoritative task and one unsaved schedule proposal. */
class RelativeTemporalProposalSession(
    val authoritativeOriginal: ExactTemporalSchedule,
    initialProposal: ExactTemporalSchedule,
    initialRevision: Int = 1
) {
    init {
        require(initialRevision > 0)
    }

    var revision: Int = initialRevision
        private set
    var currentProposal: ExactTemporalSchedule = initialProposal
        private set
    var state: RelativeTemporalProposalState = RelativeTemporalProposalState.ACTIVE
        private set

    private var requestGeneration: Long = 0L

    fun beginCorrection(): RelativeTemporalCorrectionToken {
        check(state == RelativeTemporalProposalState.ACTIVE)
        requestGeneration += 1L
        return RelativeTemporalCorrectionToken(requestGeneration, revision)
    }

    fun applyCorrection(
        token: RelativeTemporalCorrectionToken,
        schedule: ExactTemporalSchedule
    ): RelativeTemporalRevisionResult {
        if (state != RelativeTemporalProposalState.ACTIVE) {
            return RelativeTemporalRevisionResult.INACTIVE
        }
        if (token.requestGeneration != requestGeneration || token.baseRevision != revision) {
            return RelativeTemporalRevisionResult.STALE_REQUEST
        }
        currentProposal = schedule
        revision += 1
        return RelativeTemporalRevisionResult.APPLIED
    }

    fun isCurrent(token: RelativeTemporalCorrectionToken): Boolean =
        state == RelativeTemporalProposalState.ACTIVE &&
            token.requestGeneration == requestGeneration &&
            token.baseRevision == revision

    fun restoreOriginal(token: RelativeTemporalCorrectionToken): RelativeTemporalRevisionResult =
        applyCorrection(token, authoritativeOriginal)

    fun replaceFromManualEdit(schedule: ExactTemporalSchedule) {
        if (state != RelativeTemporalProposalState.ACTIVE || schedule == currentProposal) return
        requestGeneration += 1L
        currentProposal = schedule
        revision += 1
    }

    fun invalidatePendingCorrection() {
        requestGeneration += 1L
    }

    fun proposalForConfirmation(requestedRevision: Int): RelativeTemporalConfirmationResult {
        if (state != RelativeTemporalProposalState.ACTIVE) {
            return RelativeTemporalConfirmationResult.Inactive
        }
        if (requestedRevision != revision) {
            return RelativeTemporalConfirmationResult.StaleRevision
        }
        return RelativeTemporalConfirmationResult.Latest(revision, currentProposal)
    }

    fun markSaved(savedRevision: Int): Boolean {
        if (proposalForConfirmation(savedRevision) !is RelativeTemporalConfirmationResult.Latest) {
            return false
        }
        requestGeneration += 1L
        state = RelativeTemporalProposalState.SAVED
        return true
    }

    fun cancel(): ExactTemporalSchedule {
        requestGeneration += 1L
        state = RelativeTemporalProposalState.CANCELLED
        currentProposal = authoritativeOriginal
        return authoritativeOriginal
    }
}
