package com.example.myapplication.ai.temporal

enum class TemporalResolutionStatus { NONE, RESOLVED, UNRESOLVED }

enum class TemporalDateScope { ALL, EXACT_DATE, DATE_RANGE, OVERDUE, UPCOMING }

data class TemporalQueryWindow(
    val status: TemporalResolutionStatus,
    val dateScope: TemporalDateScope = TemporalDateScope.ALL,
    val startDateInclusive: String? = null,
    val endDateInclusive: String? = null,
    val startMinuteInclusive: Int? = null,
    val endMinuteInclusive: Int? = null,
    val wrapsMidnight: Boolean = false,
    val spokenLabel: String = ""
) {
    val hasDateConstraint: Boolean get() = dateScope != TemporalDateScope.ALL
    val hasTimeConstraint: Boolean get() = startMinuteInclusive != null || endMinuteInclusive != null
    val isExactDate: Boolean
        get() = dateScope == TemporalDateScope.EXACT_DATE && startDateInclusive != null && startDateInclusive == endDateInclusive
}
