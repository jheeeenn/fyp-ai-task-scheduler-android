package com.example.myapplication.ai.temporal

import java.util.Calendar

class TemporalQueryResolver(private val delegate: TemporalExpressionResolver = TemporalExpressionResolver()) {
    fun resolve(agentDateText: String?, agentTimeText: String?, originalText: String, baseCalendar: Calendar = Calendar.getInstance()): TemporalQueryWindow =
        delegate.resolve(agentDateText, agentTimeText, originalText, baseCalendar)
}
