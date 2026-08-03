package com.example.myapplication

object TaskNavigationSpeechRenderer {
    fun openingDetails(title: String): String =
        "Opening details for ${title.ifBlank { "this task" }}."
}
