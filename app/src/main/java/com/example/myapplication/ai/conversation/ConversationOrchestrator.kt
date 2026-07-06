package com.example.myapplication.ai.conversation

import android.util.Log

class ConversationOrchestrator(
    private val conversationAgentClient: ConversationAgentClient,
    private val parser: ConversationDecisionParser,
    private val memory: ConversationSessionMemory = ConversationSessionMemory()
) {
    suspend fun process(normalizedText: String, appContextSummary: String): ConversationDecision {
        memory.recordUser(normalizedText)

        val localConversationDecision = handleSimpleLocalConversation(normalizedText)
        if (localConversationDecision != null) {
            Log.d(
                "CONVO_ORCH",
                "local conversation fast path route=${localConversationDecision.route}"
            )
            memory.updateFromDecision(localConversationDecision)
            return localConversationDecision
        }

        if (looksLikeTaskCommand(normalizedText)) {
            Log.d("CONVO_ORCH", "local task fast path")
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

    private fun handleSimpleLocalConversation(text: String): ConversationDecision? {
        val normalized = text.lowercase().trim()
        return when {
            GREETINGS.any { normalized == it } -> ConversationDecision(
                route = ConversationRoute.DIRECT_REPLY,
                reply = "Hello. I can help you create, check, reschedule, delete, complete, or break down tasks.",
                confidence = 1.0,
                listenAgain = true,
                source = "local_conversation_fast_path"
            )
            HELP_PHRASES.any { normalized == it || normalized.contains(it) } -> ConversationDecision(
                route = ConversationRoute.DIRECT_REPLY,
                reply = "I can help you manage tasks by voice. You can ask me to create tasks, check today's tasks, reschedule, delete, mark tasks as done, or break down a large task.",
                confidence = 1.0,
                listenAgain = true,
                source = "local_conversation_fast_path"
            )
            THANKS_PHRASES.any { normalized == it } -> ConversationDecision(
                route = ConversationRoute.DIRECT_REPLY,
                reply = "You're welcome. Anything else you would like to do?",
                confidence = 1.0,
                listenAgain = true,
                source = "local_conversation_fast_path"
            )
            END_SESSION_PHRASES.any { normalized == it } -> ConversationDecision(
                route = ConversationRoute.END_SESSION,
                reply = "Okay, stopping the assistant.",
                confidence = 1.0,
                listenAgain = false,
                source = "local_conversation_fast_path"
            )
            else -> null
        }
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
        private val GREETINGS = listOf(
            "hello",
            "hi",
            "hey",
            "good morning",
            "good afternoon",
            "good evening"
        )

        private val HELP_PHRASES = listOf(
            "help",
            "what can you do",
            "what can i do",
            "how can you help",
            "what are your functions",
            "what can this app do"
        )

        private val THANKS_PHRASES = listOf(
            "thank you",
            "thanks",
            "okay thanks",
            "ok thanks"
        )

        private val END_SESSION_PHRASES = listOf(
            "bye",
            "goodbye",
            "stop assistant",
            "exit assistant",
            "that's all",
            "thats all"
        )

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
