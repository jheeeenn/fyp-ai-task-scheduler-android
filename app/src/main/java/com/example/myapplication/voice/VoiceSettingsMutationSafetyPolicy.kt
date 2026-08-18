package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingAction

enum class VoiceSettingTarget {
    LARGE_TEXT,
    HIGH_CONTRAST,
    PROCESSING_HAPTIC,
    SESSION_END_HAPTIC,
    ASSISTANT_TONE,
    REPLY_LENGTH
}

data class VoiceSettingConversationFocus(val target: VoiceSettingTarget)

enum class VoiceSettingClarificationScope {
    ANY_BOOLEAN_SETTING,
    HAPTIC_SETTING
}

data class PendingVoiceSettingClarification(
    val requestedEnabled: Boolean,
    val scope: VoiceSettingClarificationScope
)

/** Session-only Android authority. It is deliberately not preference-backed. */
class VoiceSettingConversationContext {
    var focus: VoiceSettingConversationFocus? = null
        private set
    var pendingClarification: PendingVoiceSettingClarification? = null
        private set

    fun focus(target: VoiceSettingTarget) {
        focus = VoiceSettingConversationFocus(target)
    }

    fun retain(clarification: PendingVoiceSettingClarification?) {
        pendingClarification = clarification
    }

    fun clearPending() {
        pendingClarification = null
    }

    fun clear() {
        focus = null
        pendingClarification = null
    }
}

fun ConversationSettingAction.voiceSettingTarget(): VoiceSettingTarget? = when (this) {
    ConversationSettingAction.LARGE_TEXT_ON,
    ConversationSettingAction.LARGE_TEXT_OFF -> VoiceSettingTarget.LARGE_TEXT
    ConversationSettingAction.HIGH_CONTRAST_ON,
    ConversationSettingAction.HIGH_CONTRAST_OFF -> VoiceSettingTarget.HIGH_CONTRAST
    ConversationSettingAction.PROCESSING_HAPTIC_ON,
    ConversationSettingAction.PROCESSING_HAPTIC_OFF -> VoiceSettingTarget.PROCESSING_HAPTIC
    ConversationSettingAction.SESSION_END_HAPTIC_ON,
    ConversationSettingAction.SESSION_END_HAPTIC_OFF -> VoiceSettingTarget.SESSION_END_HAPTIC
    ConversationSettingAction.ASSISTANT_TONE_FRIENDLY,
    ConversationSettingAction.ASSISTANT_TONE_NEUTRAL,
    ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL -> VoiceSettingTarget.ASSISTANT_TONE
    ConversationSettingAction.REPLY_LENGTH_SHORT,
    ConversationSettingAction.REPLY_LENGTH_NORMAL,
    ConversationSettingAction.REPLY_LENGTH_DETAILED -> VoiceSettingTarget.REPLY_LENGTH
    ConversationSettingAction.NONE -> null
}

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
    val groundedTarget: VoiceSettingTarget? = null,
    val pendingClarification: PendingVoiceSettingClarification? = null
)

/**
 * Final Android mutation veto. ConversationSessionMemory provides semantic conversational context;
 * this policy accepts only a bounded Android-owned target focus as mutation authority.
 */
object VoiceSettingsMutationSafetyPolicy {
    fun evaluate(
        normalizedUtterance: String,
        proposedAction: ConversationSettingAction,
        currentFocus: VoiceSettingConversationFocus? = null
    ): VoiceSettingsSafetyResult {
        val text = safetyText(normalizedUtterance)
        val utteranceTarget = groundedTarget(text)
        if (proposedAction == ConversationSettingAction.NONE || text.isBlank()) {
            return clarifySetting(groundedTarget = utteranceTarget)
        }

        if (isClearGuidanceQuestion(text)) {
            return VoiceSettingsSafetyResult(
                disposition = VoiceSettingsSafetyDisposition.GUIDANCE_ONLY,
                speech = guidanceSpeech(text, proposedAction),
                groundedTarget = utteranceTarget
            )
        }

        val haptic = hapticEvidence(text)
        if (haptic.mentionsHaptic && haptic.processing == haptic.sessionEnd) {
            return clarifyHaptic(requestedBoolean(text, null))
        }

        val proposedTarget = proposedAction.voiceSettingTarget() ?: return clarifySetting()
        val authoritativeTarget = utteranceTarget ?: currentFocus?.target?.takeIf {
            hasBoundedContextReference(text)
        }
        if (authoritativeTarget == null || authoritativeTarget != proposedTarget) {
            return clarifySetting(
                groundedTarget = utteranceTarget,
                pending = if (utteranceTarget == null && currentFocus == null) {
                    requestedBoolean(text, null)?.let(::pendingAnyBoolean)
                } else {
                    null
                }
            )
        }

        if (!isRequestedValueGrounded(text, proposedAction, proposedTarget)) {
            return clarifySetting(groundedTarget = utteranceTarget)
        }

        return VoiceSettingsSafetyResult(
            disposition = VoiceSettingsSafetyDisposition.ALLOW,
            authorizedAction = proposedAction,
            groundedTarget = utteranceTarget
        )
    }

    /** Finds one of the six targets only from trustworthy words in this turn. */
    fun groundedTarget(normalizedUtterance: String): VoiceSettingTarget? =
        groundedTarget(safetyText(normalizedUtterance), allowTargetOnly = true)

    /** Retains only a direction that Android can read from a targetless clarification request. */
    fun inferPendingClarification(
        normalizedUtterance: String,
        currentFocus: VoiceSettingConversationFocus? = null
    ): VoiceSettingsSafetyResult? {
        val text = safetyText(normalizedUtterance)
        if (text.isBlank() || isClearGuidanceQuestion(text)) return null
        val requestedEnabled = requestedBoolean(text, null) ?: return null
        val haptic = hapticEvidence(text)
        if (haptic.mentionsHaptic && haptic.processing == haptic.sessionEnd) {
            return clarifyHaptic(requestedEnabled)
        }
        if (groundedTarget(text) == null && currentFocus == null) {
            return clarifySetting(pending = pendingAnyBoolean(requestedEnabled))
        }
        return null
    }

    /** Resolves a target-only follow-up using a previously Android-grounded on/off value. */
    fun evaluateClarification(
        normalizedUtterance: String,
        pending: PendingVoiceSettingClarification
    ): VoiceSettingsSafetyResult? {
        val text = safetyText(normalizedUtterance)
        val target = if (pending.scope == VoiceSettingClarificationScope.HAPTIC_SETTING) {
            hapticClarificationTarget(text) ?: groundedTarget(text, allowTargetOnly = true)
        } else {
            groundedTarget(text, allowTargetOnly = true)
        }
        val targetAllowed = when (pending.scope) {
            VoiceSettingClarificationScope.ANY_BOOLEAN_SETTING -> target in BOOLEAN_TARGETS
            VoiceSettingClarificationScope.HAPTIC_SETTING -> target in HAPTIC_TARGETS
        }
        if (targetAllowed && target != null) {
            val currentDirection = requestedBoolean(text, target)
            if (currentDirection != null && currentDirection != pending.requestedEnabled) {
                return null
            }
            return VoiceSettingsSafetyResult(
                disposition = VoiceSettingsSafetyDisposition.ALLOW,
                authorizedAction = booleanAction(target, pending.requestedEnabled),
                groundedTarget = target
            )
        }

        val haptic = hapticEvidence(text, allowTargetOnly = true)
        return when {
            pending.scope == VoiceSettingClarificationScope.HAPTIC_SETTING &&
                (haptic.mentionsHaptic || isBareClarificationAgreement(text)) ->
                clarifyHaptic(pending.requestedEnabled)
            isBareClarificationAgreement(text) -> clarifySetting(pending = pending)
            else -> null
        }
    }

    /** True only for a short answer that plausibly belongs to the active haptic choice. */
    fun shouldRetainClarification(
        normalizedUtterance: String,
        pending: PendingVoiceSettingClarification
    ): Boolean {
        if (pending.scope != VoiceSettingClarificationScope.HAPTIC_SETTING) return false
        val text = safetyText(normalizedUtterance)
        if (text.isBlank() || text.split(' ').size > MAX_HAPTIC_ANSWER_WORDS) return false
        if (requestedBoolean(text, groundedTarget(text, allowTargetOnly = true)) != null) {
            return false
        }
        if (containsAny(
                text,
                "task", "schedule", "reminder", "create", "delete", "show", "list", "read",
                "complete", "reschedule", "high contrast", "large text", "reply", "tone"
            )
        ) return false
        return containsAny(
            text,
            "processing", "session", "first", "second", "haptic", "vibrat", "one", "yes", "yeah"
        )
    }

    fun retryClarification(
        pending: PendingVoiceSettingClarification
    ): VoiceSettingsSafetyResult = when (pending.scope) {
        VoiceSettingClarificationScope.HAPTIC_SETTING ->
            clarifyHaptic(pending.requestedEnabled)
        VoiceSettingClarificationScope.ANY_BOOLEAN_SETTING ->
            clarifySetting(pending = pending)
    }

    private fun isRequestedValueGrounded(
        text: String,
        action: ConversationSettingAction,
        target: VoiceSettingTarget
    ): Boolean = when (action) {
        ConversationSettingAction.LARGE_TEXT_ON,
        ConversationSettingAction.HIGH_CONTRAST_ON,
        ConversationSettingAction.PROCESSING_HAPTIC_ON,
        ConversationSettingAction.SESSION_END_HAPTIC_ON -> requestedBoolean(text, target) == true
        ConversationSettingAction.LARGE_TEXT_OFF,
        ConversationSettingAction.HIGH_CONTRAST_OFF,
        ConversationSettingAction.PROCESSING_HAPTIC_OFF,
        ConversationSettingAction.SESSION_END_HAPTIC_OFF -> requestedBoolean(text, target) == false
        ConversationSettingAction.ASSISTANT_TONE_FRIENDLY ->
            hasSettingValueRequest(text) && containsAny(text, "friendly", "warmer", "warm tone")
        ConversationSettingAction.ASSISTANT_TONE_NEUTRAL ->
            hasSettingValueRequest(text) && containsWord(text, "neutral")
        ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL ->
            hasSettingValueRequest(text) && containsAny(text, "professional", "formal")
        ConversationSettingAction.REPLY_LENGTH_SHORT ->
            hasSettingValueRequest(text) && containsAny(text, "short", "shorter", "brief", "concise")
        ConversationSettingAction.REPLY_LENGTH_NORMAL ->
            hasSettingValueRequest(text) && containsAny(text, "normal", "default")
        ConversationSettingAction.REPLY_LENGTH_DETAILED ->
            hasSettingValueRequest(text) && containsAny(text, "detailed", "more detail", "longer", "thorough")
        ConversationSettingAction.NONE -> false
    }

    private fun isClearGuidanceQuestion(text: String): Boolean =
        text.startsWith("how ") || text.startsWith("what ") || text.startsWith("where ") ||
            text.startsWith("tell me about ") || text.startsWith("explain ") ||
            text.startsWith("can i ") || text.startsWith("could i ") ||
            text.startsWith("am i able to ") || text.startsWith("do you know how ")

    private fun groundedTarget(text: String, allowTargetOnly: Boolean = false): VoiceSettingTarget? {
        val haptic = hapticEvidence(text, allowTargetOnly)
        return when {
            hasLargeTextTarget(text) -> VoiceSettingTarget.LARGE_TEXT
            hasHighContrastTarget(text) -> VoiceSettingTarget.HIGH_CONTRAST
            haptic.processing && !haptic.sessionEnd -> VoiceSettingTarget.PROCESSING_HAPTIC
            haptic.sessionEnd && !haptic.processing -> VoiceSettingTarget.SESSION_END_HAPTIC
            containsAny(text, "tone", "tones", "professional", "formal", "friendly", "warmer", "neutral") ->
                VoiceSettingTarget.ASSISTANT_TONE
            containsAny(
                text,
                "reply", "replies", "reply length", "reply lengths", "answer", "answers",
                "response", "responses", "concise", "more detail"
            ) -> VoiceSettingTarget.REPLY_LENGTH
            else -> null
        }
    }

    private fun hasLargeTextTarget(text: String): Boolean = containsAny(
        text,
        "large text", "larger text", "bigger text", "big text", "text larger", "text bigger",
        "smaller text", "text smaller", "text size"
    )

    private fun hasHighContrastTarget(text: String): Boolean = containsWord(text, "contrast")

    private fun requestedBoolean(text: String, target: VoiceSettingTarget?): Boolean? {
        val off = containsAny(
            text,
            "turn off", "switch off", "disable", "deactivate", "stop", "do not", "don t", "without"
        ) || EXPLICIT_BACK_OFF_REQUEST.containsMatchIn(text) ||
            CONTEXT_OFF_REQUEST.containsMatchIn(text) || (target == VoiceSettingTarget.LARGE_TEXT && containsAny(
            text, "smaller text", "decrease text size", "reduce text size"
        ))
        val on = containsAny(
            text,
            "turn on", "switch on", "enable", "activate", "start", "open"
        ) || EXPLICIT_BACK_ON_REQUEST.containsMatchIn(text) ||
            CONTEXT_ON_REQUEST.containsMatchIn(text) || when (target) {
            VoiceSettingTarget.LARGE_TEXT -> containsAny(
                text, "large text", "larger text", "bigger text", "text larger", "text bigger",
                "text size", "increase text size"
            ) && containsAny(text, "use", "make", "want", "increase")
            VoiceSettingTarget.HIGH_CONTRAST -> containsAny(
                text, "more contrast", "stronger contrast", "contrast stronger", "increase contrast",
                "high contrast"
            ) && containsAny(text, "use", "make", "want", "increase")
            else -> false
        }
        return when {
            off && !on -> false
            on && !off -> true
            else -> null
        }
    }

    private fun hasSettingValueRequest(text: String): Boolean = containsAny(
        text, "use", "be ", "make", "keep", "give", "set", "switch", "change", "go back"
    )

    private fun hasBoundedContextReference(text: String): Boolean =
        containsWord(text, "it") || containsWord(text, "that") || containsWord(text, "one")

    private fun hapticEvidence(text: String, allowTargetOnly: Boolean = false): HapticEvidence {
        val processing = containsAny(
            text,
            "processing", "while processing", "while thinking", "while you re thinking",
            "while you are thinking", "when thinking", "thinking vibration", "while working",
            "while you re working", "while you are working", "when working", "heartbeat"
        ) || (allowTargetOnly && containsAny(text, "processing one", "thinking one"))
        val sessionEnd = containsAny(
            text,
            "session end", "session ending", "conversation end", "conversation finish",
            "session vibration", "session haptic", "end vibration", "end haptic",
            "ending vibration", "ending haptic",
            "final vibration", "final haptic", "terminal vibration", "terminal haptic",
            "when you re done", "when you are done", "when you re finished",
            "when you are finished", "when the assistant is done", "when the conversation is done",
            "after the conversation"
        ) || (allowTargetOnly && containsAny(
            text, "session end one", "session ending one", "final one", "ending one"
        ))
        return HapticEvidence(
            mentionsHaptic = containsAny(text, "haptic", "vibrat", "heartbeat", "pulse") ||
                (allowTargetOnly && (processing || sessionEnd)),
            processing = processing,
            sessionEnd = sessionEnd
        )
    }

    private fun hapticClarificationTarget(text: String): VoiceSettingTarget? = when (text) {
        in PROCESSING_HAPTIC_CLARIFICATION_ANSWERS -> VoiceSettingTarget.PROCESSING_HAPTIC
        in SESSION_END_HAPTIC_CLARIFICATION_ANSWERS -> VoiceSettingTarget.SESSION_END_HAPTIC
        else -> null
    }

    private fun guidanceSpeech(text: String, proposedAction: ConversationSettingAction): String {
        if (containsAny(text, "what settings", "which settings", "settings can", "change settings by voice")) {
            return "You can ask me to change Large Text, High Contrast, processing and " +
                "session-end haptics, Assistant Tone, and Reply Length, or ask what they are currently set to."
        }
        val haptic = hapticEvidence(text)
        if (haptic.mentionsHaptic && haptic.processing == haptic.sessionEnd) {
            return "There are separate processing and session-end vibration settings. " +
                "You can ask me about either one."
        }
        return when (groundedTarget(text) ?: proposedAction.voiceSettingTarget()) {
            VoiceSettingTarget.LARGE_TEXT ->
                "You can say, 'Turn on large text,' or, 'Turn off large text.'"
            VoiceSettingTarget.HIGH_CONTRAST ->
                "High contrast increases screen contrast. You can ask me to turn it on or off."
            VoiceSettingTarget.PROCESSING_HAPTIC ->
                "Processing haptic feedback vibrates while I am working. You can ask me to turn it on or off."
            VoiceSettingTarget.SESSION_END_HAPTIC ->
                "Session-end haptic feedback vibrates when the conversation finishes. You can ask me to turn it on or off."
            VoiceSettingTarget.ASSISTANT_TONE ->
                "You can ask me to use a Friendly, Neutral, or Professional tone."
            VoiceSettingTarget.REPLY_LENGTH ->
                "You can ask me to use Short, Normal, or Detailed replies."
            null -> "Which setting would you like help with?"
        }
    }

    private fun booleanAction(target: VoiceSettingTarget, enabled: Boolean): ConversationSettingAction =
        when (target) {
            VoiceSettingTarget.LARGE_TEXT -> if (enabled) ConversationSettingAction.LARGE_TEXT_ON else ConversationSettingAction.LARGE_TEXT_OFF
            VoiceSettingTarget.HIGH_CONTRAST -> if (enabled) ConversationSettingAction.HIGH_CONTRAST_ON else ConversationSettingAction.HIGH_CONTRAST_OFF
            VoiceSettingTarget.PROCESSING_HAPTIC -> if (enabled) ConversationSettingAction.PROCESSING_HAPTIC_ON else ConversationSettingAction.PROCESSING_HAPTIC_OFF
            VoiceSettingTarget.SESSION_END_HAPTIC -> if (enabled) ConversationSettingAction.SESSION_END_HAPTIC_ON else ConversationSettingAction.SESSION_END_HAPTIC_OFF
            VoiceSettingTarget.ASSISTANT_TONE,
            VoiceSettingTarget.REPLY_LENGTH -> ConversationSettingAction.NONE
        }

    private fun clarifyHaptic(enabled: Boolean?) = VoiceSettingsSafetyResult(
        disposition = VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET,
        speech = "Do you mean the processing vibration or the session-end vibration?",
        pendingClarification = enabled?.let {
            PendingVoiceSettingClarification(it, VoiceSettingClarificationScope.HAPTIC_SETTING)
        }
    )

    private fun clarifySetting(
        groundedTarget: VoiceSettingTarget? = null,
        pending: PendingVoiceSettingClarification? = null
    ) = VoiceSettingsSafetyResult(
        disposition = VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET,
        speech = "Which setting would you like me to change?",
        groundedTarget = groundedTarget,
        pendingClarification = pending
    )

    private fun pendingAnyBoolean(enabled: Boolean) = PendingVoiceSettingClarification(
        enabled,
        VoiceSettingClarificationScope.ANY_BOOLEAN_SETTING
    )

    private fun isBareClarificationAgreement(text: String): Boolean = text in setOf(
        "yes", "yeah", "correct", "that one", "the one"
    )

    private fun safetyText(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(text: String, vararg values: String): Boolean = values.any(text::contains)

    private fun containsWord(text: String, word: String): Boolean =
        Regex("(^|\\s)${Regex.escape(word)}(\\s|$)").containsMatchIn(text)

    private data class HapticEvidence(
        val mentionsHaptic: Boolean,
        val processing: Boolean,
        val sessionEnd: Boolean
    )

    private val BOOLEAN_TARGETS = setOf(
        VoiceSettingTarget.LARGE_TEXT,
        VoiceSettingTarget.HIGH_CONTRAST,
        VoiceSettingTarget.PROCESSING_HAPTIC,
        VoiceSettingTarget.SESSION_END_HAPTIC
    )
    private val HAPTIC_TARGETS = setOf(
        VoiceSettingTarget.PROCESSING_HAPTIC,
        VoiceSettingTarget.SESSION_END_HAPTIC
    )
    private val PROCESSING_HAPTIC_CLARIFICATION_ANSWERS = setOf(
        "processing", "the processing", "processing one", "the processing one",
        "first", "first one", "the first one"
    )
    private val SESSION_END_HAPTIC_CLARIFICATION_ANSWERS = setOf(
        "session", "the session", "session one", "the session one",
        "session end", "the session end", "session end one", "the session end one",
        "second", "second one", "the second one"
    )
    private const val MAX_HAPTIC_ANSWER_WORDS = 5
    private val CONTEXT_ON_REQUEST = Regex(
        "\\b(?:turn|switch)\\s+(?:it|that|the one)(?:\\s+back)?\\s+on\\b"
    )

    private val EXPLICIT_BACK_ON_REQUEST = Regex(
        "\\bturn\\s+back\\s+on\\b|\\bturn(?:\\s+[a-z0-9]+){1,8}\\s+back\\s+on\\b"
    )

    private val EXPLICIT_BACK_OFF_REQUEST = Regex(
        "\\bturn\\s+back\\s+off\\b|\\bturn(?:\\s+[a-z0-9]+){1,8}\\s+back\\s+off\\b"
    )
    private val CONTEXT_OFF_REQUEST = Regex(
        "\\b(?:turn|switch)\\s+(?:it|that|the one)(?:\\s+back)?\\s+off\\b"
    )
}
