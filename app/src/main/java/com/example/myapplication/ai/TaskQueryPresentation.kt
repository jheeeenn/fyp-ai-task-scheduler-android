package com.example.myapplication.ai

import java.util.Locale

enum class TaskQueryPresentation {
    NONE,
    COUNT_ONLY,
    OVERVIEW,
    DETAILS;

    companion object {
        fun fromWireValue(value: String): TaskQueryPresentation? =
            entries.firstOrNull { it.name == value.trim().uppercase(Locale.ROOT) }
    }
}
