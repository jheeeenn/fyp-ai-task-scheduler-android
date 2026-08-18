package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingAction

enum class VoiceSettingsSafetyDisposition {
    ALLOW,
    GUIDANCE_ONLY,
    CLARIFY_HAPTIC_TARGET,
    CLARIFY_SETTING_TARGET
}

data class VoiceSettingsSafetyResult(
    val disposition: VoiceSettingsSafetyDisposition,
    val authorizedAction: ConversationSettingAction = ConversationSettingAction.NONE,
    val speech: String = "",
    val pendingHapticEnabled: Boolean? = null
)

/**
 * A narrow final mutation veto. The Conversation Agent remains the semantic router; this policy
 * only requires recognizable guidance, target, and value evidence before Android mutates state.
 */
object VoiceSettingsMutationSafetyPolicy {
    fun evaluate(
        normalizedUtterance: String,
        proposedAction: ConversationSettingAction
    ): VoiceSettingsSafetyResult {
        val text = safetyText(normalizedUtterance)
        if (proposedAction == ConversationSettingAction.NONE || text.isBlank()) {
            return clarifySetting()
        }

        if (isClearGuidanceQuestion(text)) {
            return VoiceSettingsSafetyResult(
                disposition = VoiceSettingsSafetyDisposition.GUIDANCE_ONLY,
                speech = guidanceSpeech(text, proposedAction)
            )
        }

        val hapticEvidence = hapticEvidence(text)
        if (hapticEvidence.mentionsHaptic &&
            hapticEvidence.processing == hapticEvidence.sessionEnd
        ) {
            return clarifyHaptic(genericRequestedEnabled(text))
        }

        if (!isActionGrounded(text, proposedAction, hapticEvidence)) {
            return clarifySetting()
        }

        return VoiceSettingsSafetyResult(
            disposition = VoiceSettingsSafetyDisposition.ALLOW,
            authorizedAction = proposedAction
        )
    }

    /** Resolves only a bounded target-only continuation after Android already retained on/off. */
    fun evaluateHapticClarification(
        normalizedUtterance: String,
        requestedEnabled: Boolean
    ): VoiceSettingsSafetyResult? {
        val text = safetyText(normalizedUtterance)
        val evidence = hapticEvidence(text, allowTargetOnly = true)
        return when {
            evidence.processing && !evidence.sessionEnd -> VoiceSettingsSafetyResult(
                disposition = VoiceSettingsSafetyDisposition.ALLOW,
                authorizedAction = if (requestedEnabled) {
                    ConversationSettingAction.PROCESSING_HAPTIC_ON
                } else {
                    ConversationSettingAction.PROCESSING_HAPTIC_OFF
                }
            )
            evidence.sessionEnd && !evidence.processing -> VoiceSettingsSafetyResult(
                disposition = VoiceSettingsSafetyDisposition.ALLOW,
                authorizedAction = if (requestedEnabled) {
                    ConversationSettingAction.SESSION_END_HAPTIC_ON
                } else {
                    ConversationSettingAction.SESSION_END_HAPTIC_OFF
                }
            )
            evidence.mentionsHaptic || isBareClarificationAgreement(text) -> clarifyHaptic(
                requestedEnabled
            )
            else -> null
        }
    }

    private fun isActionGrounded(
        text: String,
        action: ConversationSettingAction,
        hapticEvidence: HapticEvidence
    ): Boolean = when (action) {
        ConversationSettingAction.NONE -> false
        ConversationSettingAction.LARGE_TEXT_ON ->
            hasLargeTextTarget(text) && requestedBoolean(text, SettingTarget.LARGE_TEXT) == true
        ConversationSettingAction.LARGE_TEXT_OFF ->
            hasLargeTextTarget(text) && requestedBoolean(text, SettingTarget.LARGE_TEXT) == false
        ConversationSettingAction.HIGH_CONTRAST_ON ->
            hasHighContrastTarget(text) && requestedBoolean(text, SettingTarget.HIGH_CONTRAST) == true
        ConversationSettingAction.HIGH_CONTRAST_OFF ->
            hasHighContrastTarget(text) && requestedBoolean(text, SettingTarget.HIGH_CONTRAST) == false
        ConversationSettingAction.PROCESSING_HAPTIC_ON ->
            hapticEvidence.processing && !hapticEvidence.sessionEnd &&
                requestedBoolean(text, SettingTarget.HAPTIC) == true
        ConversationSettingAction.PROCESSING_HAPTIC_OFF ->
            hapticEvidence.processing && !hapticEvidence.sessionEnd &&
                requestedBoolean(text, SettingTarget.HAPTIC) == false
        ConversationSettingAction.SESSION_END_HAPTIC_ON ->
            hapticEvidence.sessionEnd && !hapticEvidence.processing &&
                requestedBoolean(text, SettingTarget.HAPTIC) == true
        ConversationSettingAction.SESSION_END_HAPTIC_OFF ->
            hapticEvidence.sessionEnd && !hapticEvidence.processing &&
                requestedBoolean(text, SettingTarget.HAPTIC) == false
        ConversationSettingAction.ASSISTANT_TONE_FRIENDLY -> hasToneRequest(text) && containsAny(
            text,
            "friendly",
            "warmer",
            "warm tone"
        )
        ConversationSettingAction.ASSISTANT_TONE_NEUTRAL ->
            hasToneRequest(text) && containsWord(text, "neutral")
        ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL -> hasToneRequest(text) && containsAny(
            text,
            "professional",
            "formal"
        )
        ConversationSettingAction.REPLY_LENGTH_SHORT -> containsAny(
            text,
            "concise"
        ) || hasReplyNoun(text) && containsAny(text, "short", "shorter", "brief")
        ConversationSettingAction.REPLY_LENGTH_NORMAL -> containsAny(
            text,
            "normal reply length",
            "default reply length",
            "default replies"
        ) || hasReplyNoun(text) && containsAny(text, "normal", "default")
        ConversationSettingAction.REPLY_LENGTH_DETAILED -> containsAny(
            text,
            "more detail",
            "thorough"
        ) || hasReplyNoun(text) && containsAny(text, "detailed", "longer")
    }

    private fun isClearGuidanceQuestion(text: String): Boolean =
        text.startsWith("how ") ||
            text.startsWith("what ") ||
            text.startsWith("where ") ||
            text.startsWith("tell me about ") ||
            text.startsWith("explain ") ||
            text.startsWith("can i ") ||
            text.startsWith("could i ") ||
            text.startsWith("am i able to ") ||
            text.startsWith("do you know how ")

    private fun hasLargeTextTarget(text: String): Boolean = containsAny(
        text,
        "large text",
        "larger text",
        "bigger text",
        "text larger",
        "text bigger",
        "smaller text",
        "text smaller",
        "text size"
    )

    private fun hasHighContrastTarget(text: String): Boolean = containsWord(text, "contrast")

    private fun requestedBoolean(text: String, target: SettingTarget): Boolean? {
        val off = containsAny(
            text,
            "turn off",
            "switch off",
            "disable",
            "deactivate",
            "stop",
            "do not",
            "don t",
            "without"
        ) || (target == SettingTarget.LARGE_TEXT && containsAny(
            text,
            "smaller text",
            "decrease text size",
            "reduce text size"
        ))
        val on = containsAny(
            text,
            "turn on",
            "switch on",
            "enable",
            "activate",
            "start",
            "open"
        ) || when (target) {
            SettingTarget.LARGE_TEXT -> containsAny(
                text,
                "large text",
                "larger text",
                "bigger text",
                "text larger",
                "text bigger",
                "text size",
                "increase text size"
            ) && containsAny(text, "use", "make", "want", "increase")
            SettingTarget.HIGH_CONTRAST -> containsAny(
                text,
                "more contrast",
                "stronger contrast",
                "contrast stronger",
                "increase contrast",
                "high contrast"
            ) && containsAny(text, "use", "make", "want", "increase")
            SettingTarget.HAPTIC,
            SettingTarget.PROCESSING_HAPTIC,
            SettingTarget.SESSION_END_HAPTIC,
            SettingTarget.ASSISTANT_TONE,
            SettingTarget.REPLY_LENGTH -> false
        }
        return when {
            off && !on -> false
            on && !off -> true
            else -> null
        }
    }

    private fun genericRequestedEnabled(text: String): Boolean? =
        requestedBoolean(text, SettingTarget.HAPTIC)

    private fun hapticEvidence(
        text: String,
        allowTargetOnly: Boolean = false
    ): HapticEvidence {
        val mentionsHaptic = containsHapticConcept(text)
        val processing = containsAny(
            text,
            "processing",
            "while processing",
            "while thinking",
            "while you re thinking",
            "while you are thinking",
            "when thinking",
            "thinking vibration",
            "while working",
            "while you re working",
            "while you are working",
            "when working",
            "heartbeat"
        ) || (allowTargetOnly && containsAny(text, "processing one", "thinking one"))
        val sessionEnd = containsAny(
            text,
            "session end",
            "session ending",
            "conversation end",
            "conversation finish",
            "final vibration",
            "final haptic",
            "terminal vibration",
            "terminal haptic",
            "when you re done",
            "when you are done",
            "when you re finished",
            "when you are finished",
            "when the assistant is done",
            "when the conversation is done",
            "after the conversation"
        ) || (allowTargetOnly && containsAny(
            text,
            "session end one",
            "session ending one",
            "final one",
            "ending one"
        ))
        return HapticEvidence(
            mentionsHaptic = mentionsHaptic || (allowTargetOnly && (processing || sessionEnd)),
            processing = processing,
            sessionEnd = sessionEnd
        )
    }

    private fun containsHapticConcept(text: String): Boolean =
        containsAny(text, "haptic", "vibrat", "heartbeat", "pulse")

    private fun hasReplyNoun(text: String): Boolean = containsAny(
        text,
        "reply",
        "replies",
        "answer",
        "answers",
        "response",
        "responses"
    )

    private fun hasToneRequest(text: String): Boolean = containsAny(
        text,
        "tone",
        "sound",
        "speak",
        "talk",
        "more formal",
        "more friendly",
        "more neutral"
    ) || Regex("(^|\\s)(be|use)(\\s|$)").containsMatchIn(text)

    private fun guidanceSpeech(
        text: String,
        proposedAction: ConversationSettingAction
    ): String {
        if (containsAny(
                text,
                "what settings",
                "which settings",
                "settings can",
                "change settings by voice"
            )
        ) {
            return "You can ask me to change Large Text, High Contrast, processing and " +
                "session-end haptics, Assistant Tone, and Reply Length."
        }
        val haptic = hapticEvidence(text)
        if (haptic.mentionsHaptic && haptic.processing == haptic.sessionEnd) {
            return "There are separate processing and session-end vibration settings. " +
                "You can ask me about either one."
        }
        return when (groundedTarget(text) ?: proposedAction.target()) {
            SettingTarget.LARGE_TEXT ->
                "You can say, 'Turn on large text,' or, 'Turn off large text.'"
            SettingTarget.HIGH_CONTRAST ->
                "High contrast increases screen contrast. You can ask me to turn it on or off."
            SettingTarget.PROCESSING_HAPTIC ->
                "Processing haptic feedback vibrates while I am working. You can ask me to turn it on or off."
            SettingTarget.SESSION_END_HAPTIC ->
                "Session-end haptic feedback vibrates when the conversation finishes. You can ask me to turn it on or off."
            SettingTarget.ASSISTANT_TONE ->
                "You can ask me to use a Friendly, Neutral, or Professional tone."
            SettingTarget.REPLY_LENGTH ->
                "You can ask me to use Short, Normal, or Detailed replies."
            SettingTarget.HAPTIC,
            null -> "Which setting would you like help with?"
        }
    }

    private fun groundedTarget(text: String): SettingTarget? {
        val haptic = hapticEvidence(text)
        return when {
            hasLargeTextTarget(text) -> SettingTarget.LARGE_TEXT
            hasHighContrastTarget(text) -> SettingTarget.HIGH_CONTRAST
            haptic.processing && !haptic.sessionEnd -> SettingTarget.PROCESSING_HAPTIC
            haptic.sessionEnd && !haptic.processing -> SettingTarget.SESSION_END_HAPTIC
            containsAny(text, "tone", "professional", "formal", "friendly", "warmer", "neutral") ->
                SettingTarget.ASSISTANT_TONE
            containsAny(text, "reply", "replies", "answer", "answers", "concise", "detail") ->
                SettingTarget.REPLY_LENGTH
            else -> null
        }
    }

    private fun ConversationSettingAction.target(): SettingTarget? = when (this) {
        ConversationSettingAction.LARGE_TEXT_ON,
        ConversationSettingAction.LARGE_TEXT_OFF -> SettingTarget.LARGE_TEXT
        ConversationSettingAction.HIGH_CONTRAST_ON,
        ConversationSettingAction.HIGH_CONTRAST_OFF -> SettingTarget.HIGH_CONTRAST
        ConversationSettingAction.PROCESSING_HAPTIC_ON,
        ConversationSettingAction.PROCESSING_HAPTIC_OFF -> SettingTarget.PROCESSING_HAPTIC
        ConversationSettingAction.SESSION_END_HAPTIC_ON,
        ConversationSettingAction.SESSION_END_HAPTIC_OFF -> SettingTarget.SESSION_END_HAPTIC
        ConversationSettingAction.ASSISTANT_TONE_FRIENDLY,
        ConversationSettingAction.ASSISTANT_TONE_NEUTRAL,
        ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL -> SettingTarget.ASSISTANT_TONE
        ConversationSettingAction.REPLY_LENGTH_SHORT,
        ConversationSettingAction.REPLY_LENGTH_NORMAL,
        ConversationSettingAction.REPLY_LENGTH_DETAILED -> SettingTarget.REPLY_LENGTH
        ConversationSettingAction.NONE -> null
    }

    private fun clarifyHaptic(enabled: Boolean?) = VoiceSettingsSafetyResult(
        disposition = VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET,
        speech = "Do you mean the processing vibration or the session-end vibration?",
        pendingHapticEnabled = enabled
    )

    private fun clarifySetting() = VoiceSettingsSafetyResult(
        disposition = VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET,
        speech = "Which setting would you like me to change?"
    )

    private fun isBareClarificationAgreement(text: String): Boolean = text in setOf(
        "yes",
        "yeah",
        "correct",
        "that one",
        "the one"
    )

    private fun safetyText(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(text: String, vararg values: String): Boolean =
        values.any(text::contains)

    private fun containsWord(text: String, word: String): Boolean =
        Regex("(^|\\s)${Regex.escape(word)}(\\s|$)").containsMatchIn(text)

    private data class HapticEvidence(
        val mentionsHaptic: Boolean,
        val processing: Boolean,
        val sessionEnd: Boolean
    )

    private enum class SettingTarget {
        LARGE_TEXT,
        HIGH_CONTRAST,
        HAPTIC,
        PROCESSING_HAPTIC,
        SESSION_END_HAPTIC,
        ASSISTANT_TONE,
        REPLY_LENGTH
    }
}
