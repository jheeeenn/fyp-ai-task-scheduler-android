package com.example.myapplication.ai.temporal

data class RelativeTemporalCanonicalizationReport(
    val changedFields: List<String>
) {
    companion object {
        val NONE = RelativeTemporalCanonicalizationReport(emptyList())
    }
}

data class CanonicalizedRelativeTemporalProposal(
    val proposal: RelativeTemporalProposal,
    val report: RelativeTemporalCanonicalizationReport
)

/**
 * Removes only empty SET artifacts that carry no authority to change a temporal field.
 * Every other operation/field combination remains untouched for strict validation.
 */
class RelativeTemporalProposalCanonicalizer {
    fun canonicalize(
        proposal: RelativeTemporalProposal
    ): CanonicalizedRelativeTemporalProposal {
        val changedFields = mutableListOf<String>()
        var dateOperation = proposal.dateOperation
        var timeOperation = proposal.timeOperation

        if (
            dateOperation == RelativeTemporalOperation.SET &&
            proposal.replacementDateText.isBlank() &&
            proposal.dateOffsetDays == 0
        ) {
            dateOperation = RelativeTemporalOperation.KEEP
            changedFields += DATE_OPERATION_FIELD
        }
        if (
            timeOperation == RelativeTemporalOperation.SET &&
            proposal.replacementTimeText.isBlank() &&
            proposal.timeOffsetMinutes == 0
        ) {
            timeOperation = RelativeTemporalOperation.KEEP
            changedFields += TIME_OPERATION_FIELD
        }

        return CanonicalizedRelativeTemporalProposal(
            proposal = proposal.copy(
                dateOperation = dateOperation,
                timeOperation = timeOperation
            ),
            report = if (changedFields.isEmpty()) {
                RelativeTemporalCanonicalizationReport.NONE
            } else {
                RelativeTemporalCanonicalizationReport(changedFields.toList())
            }
        )
    }

    companion object {
        const val DATE_OPERATION_FIELD = "date_operation"
        const val TIME_OPERATION_FIELD = "time_operation"
    }
}
