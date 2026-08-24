package com.example.myapplication.ai.conversation

import java.util.Locale

/**
 * Detects only explicit requests to perform task breakdown so a contradictory DIRECT_REPLY can
 * use the existing bounded schema-repair path. Semantic routing remains model-authoritative.
 */
internal object OperationalBreakdownRoutingGuard {
    fun requiresRepair(
        normalizedUserText: String,
        decision: ConversationDecision
    ): Boolean = decision.route == ConversationRoute.DIRECT_REPLY &&
        isExplicitOperationalRequest(normalizedUserText)

    internal fun isExplicitOperationalRequest(normalizedUserText: String): Boolean {
        val tokens = WORD.findAll(normalizedUserText.lowercase(Locale.ROOT))
            .map { it.value }
            .toList()
        val command = stripRequestFraming(tokens)
        if (command.isEmpty()) return false

        return when {
            command.startsWith("break", "down") ->
                command.drop(2).hasExplicitTaskTargetEvidence()
            command.first() == "breakdown" ->
                command.drop(1).hasExplicitTaskTargetEvidence()
            command.startsWith("break", "it", "down") ||
                command.startsWith("break", "this", "down") ||
            command.startsWith("break", "that", "down") -> true
            command.first() == "split" || command.first() == "divide" ->
                "into" in command && command.drop(1).any { it in DECOMPOSITION_RESULTS }
            else -> false
        }
    }

    private fun List<String>.hasExplicitTaskTargetEvidence(): Boolean =
        isNotEmpty() && (
            first() == "my" ||
                any { it == "task" || it == "project" || it in DECOMPOSITION_RESULTS }
            )

    private fun stripRequestFraming(tokens: List<String>): List<String> {
        var index = 0
        if (tokens.getOrNull(index) == "please") index += 1
        if (tokens.getOrNull(index) in REQUEST_MODALS && tokens.getOrNull(index + 1) == "you") {
            index += 2
        }
        if (tokens.getOrNull(index) == "please") index += 1
        return tokens.drop(index)
    }

    private fun List<String>.startsWith(vararg prefix: String): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val WORD = Regex("[\\p{L}\\p{N}']+")
    private val REQUEST_MODALS = setOf("can", "could", "will", "would")
    private val DECOMPOSITION_RESULTS = setOf("step", "steps", "subtask", "subtasks")
}
