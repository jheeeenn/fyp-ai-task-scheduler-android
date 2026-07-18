package com.example.myapplication.ai.temporal

data class PendingTemporalClarification(
    val original: TemporalResolution,
    val exactDate: String? = null,
    val exactMinute: Int? = null,
    val needsExactDate: Boolean,
    val needsExactTime: Boolean,
    val replacingOriginalConstraint: Boolean = false
) {
    val isComplete: Boolean
        get() = (!needsExactDate || exactDate != null) && (!needsExactTime || exactMinute != null)
}
