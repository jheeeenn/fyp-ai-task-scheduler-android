package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantExitInterpreterTest {
    @Test
    fun completeNaturalClosingsEndLocally() {
        listOf(
            "That's all.",
            "That is all.",
            "Okay, that's all.",
            "OK, that's all.",
            "No, that's all.",
            "Nothing else.",
            "I'm done.",
            "That's enough.",
            "Stop listening.",
            "I don't need anything else.",
            "I'm finished for now."
        ).forEach { utterance ->
            assertTrue(utterance, AssistantExitInterpreter.isExitUtterance(utterance))
        }
    }

    @Test
    fun questionsTaskRequestsAndContradictionsDoNotEndLocally() {
        listOf(
            "Is that all?",
            "Show all tasks.",
            "That's all the tasks for tomorrow?",
            "No, that's all wrong.",
            "Create a task called That's All",
            "Delete the task titled Nothing Else"
        ).forEach { utterance ->
            assertFalse(utterance, AssistantExitInterpreter.isExitUtterance(utterance))
        }
    }

    @Test
    fun bareNoIsOnlyAFollowUpExit() {
        assertFalse(AssistantExitInterpreter.isExitUtterance("no"))
        assertTrue(AssistantExitInterpreter.isFollowUpExitUtterance("no"))
    }

    @Test
    fun bareThanksRemainAvailableForNormalConversationRouting() {
        assertFalse(AssistantExitInterpreter.isExitUtterance("Thanks."))
        assertFalse(AssistantExitInterpreter.isExitUtterance("Thank you."))
        assertTrue(AssistantExitInterpreter.isExitUtterance("No thanks."))
    }
}
