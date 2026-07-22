package com.example.myapplication.ai.conversation

data class ConversationContextFocus(
    val available: Boolean,
    val ref: String,
    val generation: Long,
    val detail: ConversationContextDetail,
    val title: String
) {
    fun toPromptText(): String = buildString {
        appendLine("Available: $available")
        appendLine("Ref: $ref")
        appendLine("Generation: $generation")
        appendLine("Still present in captured snapshot: true")
        appendLine("Last requested detail: ${detail.name}")
        append("Title: ${jsonString(title)}")
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.take(MAX_TITLE_LENGTH).forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character.isISOControl() -> append(' ')
                else -> append(character)
            }
        }
        append('"')
    }

    companion object {
        private const val MAX_TITLE_LENGTH = 120
        const val UNAVAILABLE_PROMPT = "Available: false"
    }
}
