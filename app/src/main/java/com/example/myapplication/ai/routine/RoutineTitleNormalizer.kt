package com.example.myapplication.ai.routine

import java.util.Locale

object RoutineTitleNormalizer {
    fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
