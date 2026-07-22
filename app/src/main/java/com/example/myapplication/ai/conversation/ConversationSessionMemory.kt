package com.example.myapplication.ai.conversation

class ConversationSessionMemory {
    private val turns = ArrayDeque<String>()

    var lastReferencedTask: String? = null
        private set
    var lastQueryDate: String? = null
        private set
    var pendingAction: String? = null
        private set
    var latestExecutionOperation: ExecutionOperation? = null
        private set
    var latestExecutionOutcome: ExecutionOutcome? = null
        private set
    var latestRequiredInput: RequiredInput? = null
        private set
    var finalSpokenResponse: String? = null
        private set

    fun recordUser(text: String) {
        addTurn("User: $text")
    }

    fun recordAssistant(text: String) {
        if (text.isNotBlank()) {
            addTurn("Assistant: $text")
        }
    }

    fun recordObservation(observation: ExecutionObservation) {
        latestExecutionOperation = observation.operation
        latestExecutionOutcome = observation.outcome
        latestRequiredInput = observation.requiredInput
        if (observation.taskTitle.isNotBlank()) {
            lastReferencedTask = sanitizeUntrustedValue(observation.taskTitle)
        }
        if (observation.operation == ExecutionOperation.QUERY_TASK && observation.dateText.isNotBlank()) {
            lastQueryDate = sanitizeUntrustedValue(observation.dateText)
        }
        addTurn(
            "Observation: operation=${observation.operation.name} " +
                "outcome=${observation.outcome.name} " +
                "task=${sanitizeUntrustedValue(observation.taskTitle)} " +
                "date=${sanitizeUntrustedValue(observation.dateText)} " +
                "requiredInput=${observation.requiredInput.name}"
        )
    }

    fun recordFinalSpokenResponse(text: String) {
        finalSpokenResponse = text.takeIf { it.isNotBlank() }
        recordAssistant(text)
    }

    fun updateFromDecision(decision: ConversationDecision) {
        when (decision.route) {
            ConversationRoute.TASK_COMMAND -> {
                pendingAction = "TASK_COMMAND"
            }
            ConversationRoute.ASK_CLARIFICATION -> pendingAction = "ASK_CLARIFICATION"
            ConversationRoute.END_SESSION -> pendingAction = null
            ConversationRoute.DIRECT_REPLY,
            ConversationRoute.UNKNOWN -> Unit
        }
        recordAssistant(decision.reply)
    }

    fun snapshotForPrompt(): String {
        return buildString {
            appendLine("Recent turns:")
            if (turns.isEmpty()) {
                appendLine("None")
            } else {
                turns.forEach { appendLine(it) }
            }
            appendLine("lastReferencedTask=${lastReferencedTask.orEmpty()}")
            appendLine("lastQueryDate=${lastQueryDate.orEmpty()}")
            appendLine("pendingAction=${pendingAction.orEmpty()}")
            appendLine("latestExecutionOperation=${latestExecutionOperation?.name.orEmpty()}")
            appendLine("latestExecutionOutcome=${latestExecutionOutcome?.name.orEmpty()}")
            appendLine("latestRequiredInput=${latestRequiredInput?.name.orEmpty()}")
        }.trim()
    }

    fun clear() {
        turns.clear()
        lastReferencedTask = null
        lastQueryDate = null
        pendingAction = null
        latestExecutionOperation = null
        latestExecutionOutcome = null
        latestRequiredInput = null
        finalSpokenResponse = null
    }

    private fun addTurn(turn: String) {
        turns.addLast(sanitizeForPrompt(turn))
        while (turns.size > MAX_TURNS) {
            turns.removeFirst()
        }
    }

    private fun sanitizeForPrompt(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (character.isISOControl()) append(' ') else append(character)
        }
    }.replace(WHITESPACE, " ").trim().take(MAX_MEMORY_TEXT_LENGTH)

    private fun sanitizeUntrustedValue(value: String): String = sanitizeForPrompt(value)
        .replace("\\", "\\\\")
        .replace("=", "\\=")
        .replace(":", "\\:")
        .take(MAX_MEMORY_VALUE_LENGTH)

    companion object {
        private const val MAX_TURNS = 8
        private const val MAX_MEMORY_TEXT_LENGTH = 240
        private const val MAX_MEMORY_VALUE_LENGTH = 120
        private val WHITESPACE = Regex("\\s+")
    }
}
