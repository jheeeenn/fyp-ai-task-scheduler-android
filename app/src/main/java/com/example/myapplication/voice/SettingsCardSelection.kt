package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.conversation.ConversationSettingAction
import com.example.myapplication.ai.conversation.ConversationSettingTarget
import kotlinx.coroutines.CancellationException

enum class SettingsCardSelectionMove {
    SELECT_VALUE,
    ASK_OPTIONS,
    ASK_CURRENT_VALUE,
    CANCEL,
    WRONG_TARGET,
    UNKNOWN
}

data class SettingsCardSelectionResolution(
    val move: SettingsCardSelectionMove,
    val action: ConversationSettingAction = ConversationSettingAction.NONE
)

fun interface SettingsCardSelectionSemanticClient {
    suspend fun interpret(
        userText: String,
        authoritativeTarget: ConversationSettingTarget,
        authoritativeOptions: List<String>
    ): SettingsCardSelectionResolution
}

class ConversationAgentSettingsSelectionClient(
    private val client: ConversationAgentClient,
    private val parser: ConversationDecisionParser = ConversationDecisionParser()
) : SettingsCardSelectionSemanticClient {
    override suspend fun interpret(
        userText: String,
        authoritativeTarget: ConversationSettingTarget,
        authoritativeOptions: List<String>
    ): SettingsCardSelectionResolution {
        val raw = client.process(
            userText = userText,
            memorySnapshot = "Local Settings card selection. No cross-setting mutation is allowed.",
            appContextSummary = """
                Android authoritative target: ${authoritativeTarget.name}
                Android authoritative options: ${authoritativeOptions.joinToString()}
                Interpret only a SETTINGS_ACTION for that target. Android validates the target and action.
                If no bounded value is clear, return UNKNOWN. Do not invent values.
            """.trimIndent()
        )
        val decision = parser.parse(raw)
        if (decision.route != ConversationRoute.SETTINGS_ACTION) {
            return SettingsCardSelectionResolution(SettingsCardSelectionMove.UNKNOWN)
        }
        if (decision.settingTarget != authoritativeTarget) {
            return SettingsCardSelectionResolution(SettingsCardSelectionMove.WRONG_TARGET)
        }
        return SettingsCardSelectionAuthority.validate(
            authoritativeTarget,
            decision.settingAction
        )
    }
}

object SettingsCardSelectionAuthority {
    val toneOptions = listOf("Friendly", "Neutral", "Professional")
    val replyLengthOptions = listOf("Short", "Normal", "Detailed")
    val speechSpeedOptions = listOf("Slow", "Normal", "Fast", "Very Fast")

    fun options(target: ConversationSettingTarget): List<String> = when (target) {
        ConversationSettingTarget.ASSISTANT_TONE -> toneOptions
        ConversationSettingTarget.REPLY_LENGTH -> replyLengthOptions
        ConversationSettingTarget.SPEECH_SPEED -> speechSpeedOptions
        else -> emptyList()
    }

    fun validate(
        authoritativeTarget: ConversationSettingTarget,
        action: ConversationSettingAction
    ): SettingsCardSelectionResolution {
        val actionTarget = targetFor(action)
        return if (actionTarget == authoritativeTarget) {
            SettingsCardSelectionResolution(SettingsCardSelectionMove.SELECT_VALUE, action)
        } else if (actionTarget != ConversationSettingTarget.NONE) {
            SettingsCardSelectionResolution(SettingsCardSelectionMove.WRONG_TARGET)
        } else {
            SettingsCardSelectionResolution(SettingsCardSelectionMove.UNKNOWN)
        }
    }

    fun targetFor(action: ConversationSettingAction): ConversationSettingTarget = when (action) {
        ConversationSettingAction.ASSISTANT_TONE_FRIENDLY,
        ConversationSettingAction.ASSISTANT_TONE_NEUTRAL,
        ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL ->
            ConversationSettingTarget.ASSISTANT_TONE
        ConversationSettingAction.REPLY_LENGTH_SHORT,
        ConversationSettingAction.REPLY_LENGTH_NORMAL,
        ConversationSettingAction.REPLY_LENGTH_DETAILED ->
            ConversationSettingTarget.REPLY_LENGTH
        ConversationSettingAction.SPEECH_SPEED_SLOW,
        ConversationSettingAction.SPEECH_SPEED_NORMAL,
        ConversationSettingAction.SPEECH_SPEED_FAST,
        ConversationSettingAction.SPEECH_SPEED_VERY_FAST,
        ConversationSettingAction.SPEECH_SPEED_FASTER,
        ConversationSettingAction.SPEECH_SPEED_SLOWER ->
            ConversationSettingTarget.SPEECH_SPEED
        ConversationSettingAction.LARGE_TEXT_ON,
        ConversationSettingAction.LARGE_TEXT_OFF -> ConversationSettingTarget.LARGE_TEXT
        ConversationSettingAction.HIGH_CONTRAST_ON,
        ConversationSettingAction.HIGH_CONTRAST_OFF -> ConversationSettingTarget.HIGH_CONTRAST
        ConversationSettingAction.PROCESSING_HAPTIC_ON,
        ConversationSettingAction.PROCESSING_HAPTIC_OFF ->
            ConversationSettingTarget.PROCESSING_HAPTIC
        ConversationSettingAction.SESSION_END_HAPTIC_ON,
        ConversationSettingAction.SESSION_END_HAPTIC_OFF ->
            ConversationSettingTarget.SESSION_END_HAPTIC
        ConversationSettingAction.NONE -> ConversationSettingTarget.NONE
    }
}

class SettingsCardSelectionOrchestrator(
    private val semanticClient: SettingsCardSelectionSemanticClient
) {
    fun resolveImmediate(
        userText: String,
        target: ConversationSettingTarget
    ): SettingsCardSelectionResolution? {
        val text = TextNormalizer.normalize(userText)
        if (text.isBlank()) return SettingsCardSelectionResolution(SettingsCardSelectionMove.UNKNOWN)
        if (CANCEL_PHRASES.any { text == it || text.contains(it) }) {
            return SettingsCardSelectionResolution(SettingsCardSelectionMove.CANCEL)
        }
        if (OPTION_PHRASES.any(text::contains)) {
            return SettingsCardSelectionResolution(SettingsCardSelectionMove.ASK_OPTIONS)
        }
        if (CURRENT_PHRASES.any(text::contains)) {
            return SettingsCardSelectionResolution(SettingsCardSelectionMove.ASK_CURRENT_VALUE)
        }
        explicitTarget(text)?.let { spokenTarget ->
            if (spokenTarget != target) {
                return SettingsCardSelectionResolution(SettingsCardSelectionMove.WRONG_TARGET)
            }
        }
        localAction(text, target)?.let {
            return SettingsCardSelectionAuthority.validate(target, it)
        }
        return null
    }

    suspend fun resolve(
        userText: String,
        target: ConversationSettingTarget
    ): SettingsCardSelectionResolution {
        resolveImmediate(userText, target)?.let { return it }
        val resolution = try {
            semanticClient.interpret(
                userText,
                target,
                SettingsCardSelectionAuthority.options(target)
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SettingsCardSelectionResolution(SettingsCardSelectionMove.UNKNOWN)
        }
        return if (resolution.move == SettingsCardSelectionMove.SELECT_VALUE) {
            SettingsCardSelectionAuthority.validate(target, resolution.action)
        } else {
            resolution
        }
    }

    private fun localAction(
        text: String,
        target: ConversationSettingTarget
    ): ConversationSettingAction? = when (target) {
        ConversationSettingTarget.ASSISTANT_TONE -> when {
            text.contains("professional") -> ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL
            text.contains("neutral") -> ConversationSettingAction.ASSISTANT_TONE_NEUTRAL
            text.contains("friendly") -> ConversationSettingAction.ASSISTANT_TONE_FRIENDLY
            else -> null
        }
        ConversationSettingTarget.REPLY_LENGTH -> when {
            text.contains("detailed") || text.contains("more detail") ||
                text.contains("longer") -> ConversationSettingAction.REPLY_LENGTH_DETAILED
            text.contains("short") || text.contains("brief") ->
                ConversationSettingAction.REPLY_LENGTH_SHORT
            text.contains("normal") -> ConversationSettingAction.REPLY_LENGTH_NORMAL
            else -> null
        }
        ConversationSettingTarget.SPEECH_SPEED -> when {
            text.contains("very fast") -> ConversationSettingAction.SPEECH_SPEED_VERY_FAST
            text.contains("faster") || text.contains("speed up") ->
                ConversationSettingAction.SPEECH_SPEED_FASTER
            text.contains("slower") || text.contains("slow down") ->
                ConversationSettingAction.SPEECH_SPEED_SLOWER
            text.contains("normal") -> ConversationSettingAction.SPEECH_SPEED_NORMAL
            text.contains("fast") -> ConversationSettingAction.SPEECH_SPEED_FAST
            text.contains("slow") -> ConversationSettingAction.SPEECH_SPEED_SLOW
            else -> null
        }
        else -> null
    }

    private fun explicitTarget(text: String): ConversationSettingTarget? = when {
        text.contains("speech speed") || text.contains("speaking speed") ->
            ConversationSettingTarget.SPEECH_SPEED
        text.contains("reply length") || text.contains("response length") ->
            ConversationSettingTarget.REPLY_LENGTH
        text.contains("assistant tone") || text.contains("tone") ->
            ConversationSettingTarget.ASSISTANT_TONE
        else -> null
    }

    private companion object {
        val CANCEL_PHRASES = setOf(
            "cancel",
            "never mind",
            "nevermind",
            "leave it",
            "don't change it",
            "do not change it"
        )
        val OPTION_PHRASES = setOf(
            "what are the choices",
            "what options",
            "options are available",
            "what can i choose",
            "what values"
        )
        val CURRENT_PHRASES = setOf(
            "what is it set to",
            "what's it set to",
            "what am i using",
            "current value",
            "using now"
        )
    }
}
