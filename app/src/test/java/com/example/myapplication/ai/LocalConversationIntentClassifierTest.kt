package com.example.myapplication.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalConversationIntentClassifierTest {
    @Test
    fun combinedScheduleQuestionsDeferToSemanticRouting() {
        assertTrue(
            LocalConversationIntentClassifier.shouldDeferToSemanticRouting(
                "what is the date and time"
            )
        )
        assertTrue(
            LocalConversationIntentClassifier.shouldDeferToSemanticRouting(
                "what day and what time is it scheduled"
            )
        )
        assertTrue(
            LocalConversationIntentClassifier.shouldDeferToSemanticRouting(
                "when is this task"
            )
        )
    }

    @Test
    fun singleScheduleDetailsDoNotTriggerCombinedAbstention() {
        assertFalse(LocalConversationIntentClassifier.shouldDeferToSemanticRouting("what is the date"))
        assertFalse(LocalConversationIntentClassifier.shouldDeferToSemanticRouting("what is the time"))
        assertFalse(LocalConversationIntentClassifier.shouldDeferToSemanticRouting("read the task"))
    }

    @Test
    fun onlyHighConfidenceNonUnknownResultsMayExecuteLocally() {
        assertFalse(
            LocalConversationIntentClassifier.shouldExecuteLocally(
                ConversationIntentResult(ConversationIntent.CONFIRM_NO, 0.79f)
            )
        )
        assertTrue(
            LocalConversationIntentClassifier.shouldExecuteLocally(
                ConversationIntentResult(ConversationIntent.CONFIRM_NO, 0.80f)
            )
        )
        assertFalse(
            LocalConversationIntentClassifier.shouldExecuteLocally(
                ConversationIntentResult(ConversationIntent.UNKNOWN, 1.0f)
            )
        )
    }
}
