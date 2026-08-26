package com.example.myapplication.ai

/** Requested schedule component only; never a task reference or a temporal filter. */
enum class TaskQueryDetail {
    NONE,
    DATE,
    TIME,
    DATE_TIME;

    companion object {
        fun fromWireValue(value: String): TaskQueryDetail? =
            entries.firstOrNull { it.name == value }
    }
}
