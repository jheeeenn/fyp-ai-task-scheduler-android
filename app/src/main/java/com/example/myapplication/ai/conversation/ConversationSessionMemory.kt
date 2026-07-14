package com.example.myapplication.ai.conversation

class ConversationSessionMemory {
    private val turns = ArrayDeque<String>()

    var lastReferencedTask: String? = null
        private set
    var lastQueryDate: String? = null
        private set
    var pendingAction: String? = null
        private set

    fun recordUser(text: String) {
        addTurn("User: $text")
    }

    fun recordAssistant(text: String) {
        if (text.isNotBlank()) {
            addTurn("Assistant: $text")
        }
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
        }.trim()
    }

    fun clear() {
        turns.clear()
        lastReferencedTask = null
        lastQueryDate = null
        pendingAction = null
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
