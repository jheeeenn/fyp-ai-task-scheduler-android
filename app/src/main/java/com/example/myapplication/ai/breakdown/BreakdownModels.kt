package com.example.myapplication.ai.breakdown

import com.example.myapplication.ai.temporal.PendingTemporalClarification

enum class BreakdownDraftMode {
    EXISTING_ROOT,
    NEW_ROOT
}

enum class BreakdownTargetPreference {
    AUTO,
    NEW_ROOT;

    companion object {
        fun fromWireValue(value: String): BreakdownTargetPreference? =
            entries.firstOrNull { it.name == value.trim() }
    }
}

enum class BreakdownDraftState {
    NONE,
    RESOLVING_TARGET,
    CHOOSING_TARGET,
    WAITING_FOR_CONFIRMATION,
    COLLECTING_SCHEDULE,
    SAVING
}

data class PendingBreakdownDraft(
    val mode: BreakdownDraftMode?,
    private val parentTaskId: Long?,
    val parentTitle: String,
    val proposedSubtasks: List<String>,
    val dateText: String?,
    val timeText: String?,
    val originalRequest: String,
    val revision: Long,
    val generation: Long,
    val temporalClarification: PendingTemporalClarification? = null
) {
    internal fun resolvedParentTaskId(): Long? = parentTaskId

    override fun toString(): String =
        "PendingBreakdownDraft(mode=$mode, parentTaskId=<redacted>, " +
            "parentTitle=$parentTitle, proposedSubtaskCount=${proposedSubtasks.size}, " +
            "dateText=$dateText, timeText=$timeText, revision=$revision, " +
            "generation=$generation)"
}

data class PendingBreakdownSave(
    val saveGeneration: Long,
    val draft: PendingBreakdownDraft
)

sealed class BreakdownDraftUpdate {
    data class Resolving(val draft: PendingBreakdownDraft) : BreakdownDraftUpdate()
    data class ChoosingTarget(
        val choices: List<String>,
        val draft: PendingBreakdownDraft
    ) : BreakdownDraftUpdate()
    data class Review(val draft: PendingBreakdownDraft) : BreakdownDraftUpdate()
    data class Rejected(val reason: BreakdownPlanValidationReason) : BreakdownDraftUpdate()
    data object AlreadyHasSubtasks : BreakdownDraftUpdate()
    data object ParentChanged : BreakdownDraftUpdate()
    data object Stale : BreakdownDraftUpdate()
}
