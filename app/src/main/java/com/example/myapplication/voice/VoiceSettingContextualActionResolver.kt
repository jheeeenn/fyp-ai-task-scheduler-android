package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingAction

/**
 * Bounded Android recovery for a focused setting reference whose target and value are both
 * independently grounded. This is not a general settings intent classifier.
 */
object VoiceSettingContextualActionResolver {
    fun resolve(
        normalizedUtterance: String,
        focus: VoiceSettingConversationFocus?
    ): ConversationSettingAction? {
        val authoritativeTarget = focus?.target ?: return null
        val text = normalizedUtterance.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isBlank() || isGuidance(text) || !hasContextReference(text)) return null

        val explicitTarget = VoiceSettingsMutationSafetyPolicy.groundedTarget(text)
        if (explicitTarget != null && explicitTarget != authoritativeTarget) return null
        if (explicitTarget == null && containsHapticConcept(text)) return null

        return when (authoritativeTarget) {
            VoiceSettingTarget.LARGE_TEXT -> booleanAction(
                text,
                ConversationSettingAction.LARGE_TEXT_ON,
                ConversationSettingAction.LARGE_TEXT_OFF
            )
            VoiceSettingTarget.HIGH_CONTRAST -> booleanAction(
                text,
                ConversationSettingAction.HIGH_CONTRAST_ON,
                ConversationSettingAction.HIGH_CONTRAST_OFF
            )
            VoiceSettingTarget.PROCESSING_HAPTIC -> booleanAction(
                text,
                ConversationSettingAction.PROCESSING_HAPTIC_ON,
                ConversationSettingAction.PROCESSING_HAPTIC_OFF
            )
            VoiceSettingTarget.SESSION_END_HAPTIC -> booleanAction(
                text,
                ConversationSettingAction.SESSION_END_HAPTIC_ON,
                ConversationSettingAction.SESSION_END_HAPTIC_OFF
            )
            VoiceSettingTarget.ASSISTANT_TONE -> toneAction(text)
            VoiceSettingTarget.REPLY_LENGTH -> replyLengthAction(text)
        }
    }

    private fun booleanAction(
        text: String,
        onAction: ConversationSettingAction,
        offAction: ConversationSettingAction
    ): ConversationSettingAction? {
        val on = ON_REFERENCE_REQUEST.containsMatchIn(text)
        val off = OFF_REFERENCE_REQUEST.containsMatchIn(text)
        return when {
            on && !off -> onAction
            off && !on -> offAction
            else -> null
        }
    }

    private fun toneAction(text: String): ConversationSettingAction? {
        if (!hasValueSelectionRequest(text)) return null
        return when {
            containsAny(text, "professional", "formal") ->
                ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL
            containsAny(text, "friendly", "warmer") ->
                ConversationSettingAction.ASSISTANT_TONE_FRIENDLY
            containsWord(text, "neutral") -> ConversationSettingAction.ASSISTANT_TONE_NEUTRAL
            else -> null
        }
    }

    private fun replyLengthAction(text: String): ConversationSettingAction? {
        if (!hasValueSelectionRequest(text)) return null
        return when {
            containsAny(text, "short", "brief", "concise") ->
                ConversationSettingAction.REPLY_LENGTH_SHORT
            containsAny(text, "normal", "default") ->
                ConversationSettingAction.REPLY_LENGTH_NORMAL
            containsAny(text, "detailed", "more detail", "longer", "thorough") ->
                ConversationSettingAction.REPLY_LENGTH_DETAILED
            else -> null
        }
    }

    private fun isGuidance(text: String): Boolean =
        text.startsWith("how ") || text.startsWith("what ") || text.startsWith("where ") ||
            text.startsWith("tell me about ") || text.startsWith("explain ") ||
            text.startsWith("can i ") || text.startsWith("could i ")

    private fun hasContextReference(text: String): Boolean =
        containsWord(text, "it") || containsWord(text, "that") || containsWord(text, "one")

    private fun containsHapticConcept(text: String): Boolean =
        containsAny(text, "haptic", "vibrat", "heartbeat", "pulse")

    private fun hasValueSelectionRequest(text: String): Boolean = containsAny(
        text,
        "use ", "set ", "make ", "be ", "switch ", "change ", "keep ", "give "
    )

    private fun containsAny(text: String, vararg values: String): Boolean = values.any(text::contains)

    private fun containsWord(text: String, word: String): Boolean =
        Regex("(^|\\s)${Regex.escape(word)}(\\s|$)").containsMatchIn(text)

    private val ON_REFERENCE_REQUEST = Regex(
        "\\b(?:turn|switch)\\s+(?:it|that|the one)(?:\\s+back)?\\s+on\\b|" +
            "\\b(?:enable|activate|start)\\s+(?:it|that|the one)\\b"
    )
    private val OFF_REFERENCE_REQUEST = Regex(
        "\\b(?:turn|switch)\\s+(?:it|that|the one)(?:\\s+back)?\\s+off\\b|" +
            "\\b(?:disable|deactivate|stop)\\s+(?:it|that|the one)\\b"
    )
}
