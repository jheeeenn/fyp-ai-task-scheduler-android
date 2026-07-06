package com.example.myapplication.ai.conversation

class ConversationOrchestrator(
    private val conversationAgentClient: ConversationAgentClient,
    private val parser: ConversationDecisionParser,
    private val memory: ConversationSessionMemory = ConversationSessionMemory()
) {
    suspend fun process(normalizedText: String, appContextSummary: String): ConversationDecision {
        memory.recordUser(normalizedText)

        if (looksLikeTaskCommand(normalizedText)) {
            return ConversationDecision(
                route = ConversationRoute.TASK_COMMAND,
                taskText = normalizedText,
                confidence = 1.0,
                listenAgain = true,
                source = "local_task_fast_path"
            )
        }

        val rawContent = conversationAgentClient.process(
            userText = normalizedText,
            memorySnapshot = memory.snapshotForPrompt(),
            appContextSummary = appContextSummary
        )
        val parsed = parser.parse(rawContent)
        val decision = normalizeDecision(parsed, normalizedText)
        memory.updateFromDecision(decision)
        return decision
    }

    private fun normalizeDecision(
        decision: ConversationDecision,
        normalizedText: String
    ): ConversationDecision {
        return when (decision.route) {
            ConversationRoute.TASK_COMMAND -> decision.copy(
                taskText = decision.taskText.ifBlank { normalizedText }
            )
            ConversationRoute.DIRECT_REPLY -> decision.copy(
                reply = decision.reply.ifBlank { "Hi. I can help you create, check, reschedule, delete, complete, or break down tasks." }
            )
            ConversationRoute.ASK_CLARIFICATION -> decision.copy(
                reply = decision.reply.ifBlank { "Could you say that another way, or tell me which task you mean?" }
            )
            ConversationRoute.UNKNOWN -> decision.copy(
                reply = decision.reply.ifBlank { "I can help with task scheduling. Try asking me to create, check, reschedule, delete, complete, or break down a task." }
            )
            ConversationRoute.END_SESSION -> decision.copy(
                reply = decision.reply.ifBlank { "Okay, stopping the assistant." },
                listenAgain = false
            )
        }
    }

    private fun looksLikeTaskCommand(text: String): Boolean {
        val normalized = text.lowercase()
        return TASK_HINTS.any { normalized.contains(it) }
    }

    companion object {
        private val TASK_HINTS = listOf(
            "remind me",
            "create task",
            "add task",
            "schedule",
            "what task",
            "what tasks",
            "tasks do i have",
            "do i have anything",
            "delete",
            "remove",
            "reschedule",
            "move",
            "change time",
            "mark",
            "as done",
            "complete",
            "undone",
            "break down",
            "split",
            "edit task",
            "update task"
        )
    }
}
