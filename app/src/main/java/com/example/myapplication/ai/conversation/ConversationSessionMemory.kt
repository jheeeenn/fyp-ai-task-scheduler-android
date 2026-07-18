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
        if (observation.taskTitle.isNotBlank()) lastReferencedTask = observation.taskTitle
        addTurn("Observation: operation=${observation.operation.name} outcome=${observation.outcome.name} task=${observation.taskTitle} requiredInput=${observation.requiredInput.name}")
    }

    fun recordFinalSpokenResponse(text: String) {
        finalSpokenResponse = text.takeIf { it.isNotBlank() }
        recordAssistant(text)
    }

    fun updateFromDecision(decision: ConversationDecision) {
        when (decision.route) {
            ConversationRoute.TASK_COMMAND -> {
                pendingAction = "TASK_COMMAND"
                lastReferencedTask = decision.taskText.takeIf { it.isNotBlank() } ?: lastReferencedTask
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
        turns.addLast(turn)
        while (turns.size > MAX_TURNS) {
            turns.removeFirst()
        }
    }

    companion object {
        private const val MAX_TURNS = 8
    }
}
