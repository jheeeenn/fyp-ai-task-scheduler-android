package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import java.util.Locale

class ConversationSessionMemory {
    private val turns = ArrayDeque<String>()
    private var authoritativeContextTitle: String? = null

    var lastReferencedTask: String? = null
        private set
    var lastQueryDate: String? = null
        private set
    var lastContextRef: String? = null
        private set
    var lastContextGeneration: Long? = null
        private set
    var lastContextDetail: ConversationContextDetail = ConversationContextDetail.NONE
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
            clearStructuredContextSelection()
            lastReferencedTask = sanitizeUntrustedValue(observation.taskTitle)
        }
        if (observation.operation == ExecutionOperation.QUERY_TASK && observation.dateText.isNotBlank()) {
            lastQueryDate = sanitizeUntrustedValue(observation.dateText)
        }
        addTurn(
            "Observation: operation=${observation.operation.name} " +
                "outcome=${observation.outcome.name} " +
                "task=${jsonString(sanitizeUntrustedValue(observation.taskTitle))} " +
                "date=${jsonString(sanitizeUntrustedValue(observation.dateText))} " +
                "requiredInput=${observation.requiredInput.name}"
        )
    }

    fun recordFinalSpokenResponse(text: String) {
        finalSpokenResponse = text.takeIf { it.isNotBlank() }
        recordAssistant(text)
    }

    fun recordAuthoritativeContextRead(
        item: ReadOnlyTaskContextItem,
        selectedRef: String,
        selectedDetail: ConversationContextDetail,
        capturedGeneration: Long,
        finalSpeech: String
    ) {
        pendingAction = null
        lastContextRef = selectedRef.trim().uppercase(Locale.ROOT)
        lastContextGeneration = capturedGeneration
        lastContextDetail = selectedDetail
        authoritativeContextTitle = sanitizeUntrustedValue(item.title)
        lastReferencedTask = authoritativeContextTitle
        recordFinalSpokenResponse(finalSpeech)
    }

    fun contextFocusForGeneration(
        currentGeneration: Long,
        suppliedRefs: Set<String>
    ): ConversationContextFocus? {
        val ref = lastContextRef ?: return null
        val generation = lastContextGeneration ?: return null
        val title = authoritativeContextTitle ?: return null
        if (generation != currentGeneration) return null
        if (suppliedRefs.none { it.equals(ref, ignoreCase = true) }) return null
        return ConversationContextFocus(
            available = true,
            ref = ref,
            generation = generation,
            detail = lastContextDetail,
            title = title
        )
    }

    fun clearInvalidContextFocus(
        currentGeneration: Long,
        suppliedRefs: Set<String>
    ): Boolean {
        if (lastContextRef == null) return false
        if (contextFocusForGeneration(currentGeneration, suppliedRefs) != null) return false
        clearStructuredContextSelection()
        lastReferencedTask = null
        return true
    }

    fun commitFinalDecision(decision: ConversationDecision) {
        when (decision.route) {
            ConversationRoute.TASK_COMMAND -> {
                pendingAction = "TASK_COMMAND"
            }
            ConversationRoute.DAILY_BRIEFING -> {
                pendingAction = "DAILY_BRIEFING"
            }
            ConversationRoute.ASK_CLARIFICATION -> {
                pendingAction = "ASK_CLARIFICATION"
                recordAssistant(decision.reply)
            }
            ConversationRoute.END_SESSION,
            ConversationRoute.DIRECT_REPLY,
            ConversationRoute.UNKNOWN -> {
                pendingAction = null
                recordAssistant(decision.reply)
            }
            ConversationRoute.CONTEXT_READ -> Unit
            ConversationRoute.CONTEXT_ACTION -> {
                pendingAction = "CONTEXT_ACTION"
            }
            ConversationRoute.QUERY_READING_CONTROL -> {
                pendingAction = null
            }
        }
    }

    fun snapshotForPrompt(): String {
        return buildString {
            appendLine("Recent turns:")
            if (turns.isEmpty()) {
                appendLine("None")
            } else {
                turns.forEach { appendLine(it) }
            }
            appendLine("lastReferencedTask=${jsonString(lastReferencedTask.orEmpty())}")
            appendLine("lastQueryDate=${jsonString(lastQueryDate.orEmpty())}")
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
        lastContextRef = null
        lastContextGeneration = null
        lastContextDetail = ConversationContextDetail.NONE
        authoritativeContextTitle = null
        pendingAction = null
        latestExecutionOperation = null
        latestExecutionOutcome = null
        latestRequiredInput = null
        finalSpokenResponse = null
    }

    private fun clearStructuredContextSelection() {
        lastContextRef = null
        lastContextGeneration = null
        lastContextDetail = ConversationContextDetail.NONE
        authoritativeContextTitle = null
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
        .take(MAX_MEMORY_VALUE_LENGTH)

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(character)
            }
        }
        append('"')
    }

    companion object {
        private const val MAX_TURNS = 8
        private const val MAX_MEMORY_TEXT_LENGTH = 240
        private const val MAX_MEMORY_VALUE_LENGTH = 120
        private val WHITESPACE = Regex("\\s+")
    }
}
