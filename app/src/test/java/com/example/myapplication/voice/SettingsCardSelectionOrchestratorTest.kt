package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingAction
import com.example.myapplication.ai.conversation.ConversationSettingTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsCardSelectionOrchestratorTest {
    private val unusedClient = SettingsCardSelectionSemanticClient { _, _, _ ->
        error("Local natural form should not require the agent")
    }
    private val orchestrator = SettingsCardSelectionOrchestrator(unusedClient)

    @Test
    fun naturalToneReplyAndSpeedFormsMapToBoundedActions() {
        assertAction(
            "make it professional",
            ConversationSettingTarget.ASSISTANT_TONE,
            ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL
        )
        assertAction(
            "I want a neutral tone",
            ConversationSettingTarget.ASSISTANT_TONE,
            ConversationSettingAction.ASSISTANT_TONE_NEUTRAL
        )
        assertAction(
            "make the replies shorter",
            ConversationSettingTarget.REPLY_LENGTH,
            ConversationSettingAction.REPLY_LENGTH_SHORT
        )
        assertAction(
            "use detailed replies",
            ConversationSettingTarget.REPLY_LENGTH,
            ConversationSettingAction.REPLY_LENGTH_DETAILED
        )
        assertAction(
            "make it very fast",
            ConversationSettingTarget.SPEECH_SPEED,
            ConversationSettingAction.SPEECH_SPEED_VERY_FAST
        )
        assertAction(
            "can you speak faster",
            ConversationSettingTarget.SPEECH_SPEED,
            ConversationSettingAction.SPEECH_SPEED_FASTER
        )
        assertAction(
            "slower",
            ConversationSettingTarget.SPEECH_SPEED,
            ConversationSettingAction.SPEECH_SPEED_SLOWER
        )
    }

    @Test
    fun optionsCurrentAndCancelAreNonMutatingMoves() {
        assertMove(
            "what options are available?",
            ConversationSettingTarget.ASSISTANT_TONE,
            SettingsCardSelectionMove.ASK_OPTIONS
        )
        assertMove(
            "what is it set to now?",
            ConversationSettingTarget.REPLY_LENGTH,
            SettingsCardSelectionMove.ASK_CURRENT_VALUE
        )
        listOf("cancel", "never mind", "leave it", "don't change it").forEach { text ->
            val resolution = requireNotNull(
                orchestrator.resolveImmediate(text, ConversationSettingTarget.SPEECH_SPEED)
            )
            assertEquals(SettingsCardSelectionMove.CANCEL, resolution.move)
            assertEquals(ConversationSettingAction.NONE, resolution.action)
        }
    }

    @Test
    fun authoritativeOptionsContainOnlySupportedValues() {
        assertEquals(
            listOf("Friendly", "Neutral", "Professional"),
            SettingsCardSelectionAuthority.options(ConversationSettingTarget.ASSISTANT_TONE)
        )
        assertEquals(
            listOf("Short", "Normal", "Detailed"),
            SettingsCardSelectionAuthority.options(ConversationSettingTarget.REPLY_LENGTH)
        )
        assertEquals(
            listOf("Slow", "Normal", "Fast", "Very Fast"),
            SettingsCardSelectionAuthority.options(ConversationSettingTarget.SPEECH_SPEED)
        )
    }

    @Test
    fun explicitWrongTargetAndAgentWrongActionCannotMutateActivatedCard() = runBlocking {
        val explicit = requireNotNull(
            orchestrator.resolveImmediate(
                "set speech speed to fast",
                ConversationSettingTarget.ASSISTANT_TONE
            )
        )
        assertEquals(SettingsCardSelectionMove.WRONG_TARGET, explicit.move)

        val maliciousClient = SettingsCardSelectionSemanticClient { _, _, _ ->
            SettingsCardSelectionResolution(
                SettingsCardSelectionMove.SELECT_VALUE,
                ConversationSettingAction.REPLY_LENGTH_DETAILED
            )
        }
        val validated = SettingsCardSelectionOrchestrator(maliciousClient).resolve(
            "make it more formal",
            ConversationSettingTarget.ASSISTANT_TONE
        )
        assertEquals(SettingsCardSelectionMove.WRONG_TARGET, validated.move)
        assertEquals(ConversationSettingAction.NONE, validated.action)
    }

    @Test
    fun unknownLocalInputDoesNotProposeMutation() {
        assertNull(
            orchestrator.resolveImmediate(
                "something different please",
                ConversationSettingTarget.ASSISTANT_TONE
            )
        )
    }

    private fun assertAction(
        text: String,
        target: ConversationSettingTarget,
        action: ConversationSettingAction
    ) {
        val resolution = requireNotNull(orchestrator.resolveImmediate(text, target))
        assertEquals(SettingsCardSelectionMove.SELECT_VALUE, resolution.move)
        assertEquals(action, resolution.action)
    }

    private fun assertMove(
        text: String,
        target: ConversationSettingTarget,
        move: SettingsCardSelectionMove
    ) {
        val resolution = requireNotNull(orchestrator.resolveImmediate(text, target))
        assertEquals(move, resolution.move)
        assertEquals(ConversationSettingAction.NONE, resolution.action)
    }
}
