package com.example.myapplication.ai.temporal

enum class TemporalResolutionType {
    NONE, UNRESOLVED, EXACT_DATE, EXACT_TIME, EXACT_DATE_TIME, DATE_RANGE, TIME_RANGE, DATE_TIME_WINDOW, OVERDUE, UPCOMING
}

enum class TemporalResolutionStatus { NONE, RESOLVED, UNRESOLVED }

enum class TemporalDateScope { ALL, EXACT_DATE, DATE_RANGE, OVERDUE, UPCOMING }

data class TemporalResolution(
    val type: TemporalResolutionType,
    val dateScope: TemporalDateScope = TemporalDateScope.ALL,
    val startDateInclusive: String? = null,
    val endDateInclusive: String? = null,
    val startMinuteInclusive: Int? = null,
    val endMinuteInclusive: Int? = null,
    val wrapsMidnight: Boolean = false,
    val spokenLabel: String = "",
    val originalDatePhrase: String = "",
    val originalTimePhrase: String = ""
) {
    constructor(
        status: TemporalResolutionStatus,
        dateScope: TemporalDateScope = TemporalDateScope.ALL,
        startDateInclusive: String? = null,
        endDateInclusive: String? = null,
        startMinuteInclusive: Int? = null,
        endMinuteInclusive: Int? = null,
        wrapsMidnight: Boolean = false,
        spokenLabel: String = ""
    ) : this(
        type = when (status) {
            TemporalResolutionStatus.NONE -> TemporalResolutionType.NONE
            TemporalResolutionStatus.UNRESOLVED -> TemporalResolutionType.UNRESOLVED
            TemporalResolutionStatus.RESOLVED -> when {
                dateScope == TemporalDateScope.OVERDUE -> TemporalResolutionType.OVERDUE
                dateScope == TemporalDateScope.UPCOMING -> TemporalResolutionType.UPCOMING
                dateScope == TemporalDateScope.EXACT_DATE && startDateInclusive == endDateInclusive && startMinuteInclusive != null && startMinuteInclusive == endMinuteInclusive -> TemporalResolutionType.EXACT_DATE_TIME
                dateScope != TemporalDateScope.ALL && startMinuteInclusive != null -> TemporalResolutionType.DATE_TIME_WINDOW
                dateScope == TemporalDateScope.EXACT_DATE -> TemporalResolutionType.EXACT_DATE
                dateScope == TemporalDateScope.DATE_RANGE -> TemporalResolutionType.DATE_RANGE
                startMinuteInclusive != null && startMinuteInclusive == endMinuteInclusive -> TemporalResolutionType.EXACT_TIME
                startMinuteInclusive != null || endMinuteInclusive != null -> TemporalResolutionType.TIME_RANGE
                else -> TemporalResolutionType.NONE
            }
        },
        dateScope = dateScope,
        startDateInclusive = startDateInclusive,
        endDateInclusive = endDateInclusive,
        startMinuteInclusive = startMinuteInclusive,
        endMinuteInclusive = endMinuteInclusive,
        wrapsMidnight = wrapsMidnight,
        spokenLabel = spokenLabel
    )

    val status: TemporalResolutionStatus get() = when (type) {
        TemporalResolutionType.NONE -> TemporalResolutionStatus.NONE
        TemporalResolutionType.UNRESOLVED -> TemporalResolutionStatus.UNRESOLVED
        else -> TemporalResolutionStatus.RESOLVED
    }
    val hasDateConstraint: Boolean get() = dateScope != TemporalDateScope.ALL
    val hasTimeConstraint: Boolean get() = startMinuteInclusive != null || endMinuteInclusive != null
    val isExactDate: Boolean get() = dateScope == TemporalDateScope.EXACT_DATE && startDateInclusive != null && startDateInclusive == endDateInclusive
    val isExactTime: Boolean get() = hasTimeConstraint && startMinuteInclusive == endMinuteInclusive
}

typealias TemporalQueryWindow = TemporalResolution
