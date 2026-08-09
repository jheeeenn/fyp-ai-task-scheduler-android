package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEndSessionSafetyPolicyTest {
    @Test
    fun questionLikeUtterancesCannotAuthorizeModelEndSession() {
        listOf(
            "Is that all?",
            "Is that all",
            "Are those all the tasks?",
            "Is that everything?"
        ).forEach {
            assertTrue(it, ConversationEndSessionSafetyPolicy.shouldRejectModelEndSession(it))
        }
    }

    @Test
    fun naturalDeclarativeClosingsRemainAllowed() {
        listOf(
            "That's all.",
            "I think I'm done for now.",
            "I'm done for now.",
            "That will be all.",
            "I don't need anything else."
        ).forEach {
            assertFalse(it, ConversationEndSessionSafetyPolicy.shouldRejectModelEndSession(it))
        }
    }
}
