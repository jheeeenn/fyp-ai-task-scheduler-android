package com.example.myapplication.reminder

enum class ReminderEscalationStage(val offsetMinutes: Long) {
    DUE(0),
    FOLLOW_UP(5),
    FINAL(15);

    companion object {
        fun fromWireValue(value: String?): ReminderEscalationStage? =
            entries.firstOrNull { it.name == value }
    }
}
