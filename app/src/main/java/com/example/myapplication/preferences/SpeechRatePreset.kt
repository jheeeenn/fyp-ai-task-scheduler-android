package com.example.myapplication.preferences

enum class SpeechRatePreset(
    val displayName: String,
    val rate: Float
) {
    SLOW("Slow", 0.80f),
    NORMAL("Normal", 1.00f),
    FAST("Fast", 1.25f),
    VERY_FAST("Very Fast", 1.50f);

    companion object {
        fun fromStoredValue(value: String?): SpeechRatePreset =
            values().firstOrNull { preset ->
                preset.displayName.equals(value?.trim(), ignoreCase = true)
            } ?: NORMAL
    }
}
