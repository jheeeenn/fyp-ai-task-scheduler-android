package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGuidanceCatalogTest {
    private val prompt = AppGuidanceCatalog.homeContext(
        interactionState = "DELETE_CONFIRMATION",
        currentInteraction = "A deletion is waiting for confirmation.",
        currentInteractionGuidance = listOf("Confirm or decline the deletion.")
    ).toPromptText()

    @Test
    fun coversCurrentTaskAndAssistantCapabilities() {
        listOf(
            "Create a task with a title, date, and time",
            "Query and read tasks by date, time, or date range",
            "Edit a matched task's title, date, or time",
            "reschedule, delete after confirmation",
            "mark complete",
            "mark a completed task incomplete",
            "on-demand spoken daily briefing",
            "What should I focus on?",
            "Build a reusable routine",
            "List saved routines",
            "Break a larger task into smaller actionable subtasks",
            "due reminder",
            "later follow-up",
            "final reminder",
            "Long-press the Talk Assistant button to type",
            "double-tap the panel to stop"
        ).forEach { fact -> assertTrue("Missing guidance fact: $fact", prompt.contains(fact)) }

        assertFalse(prompt.contains("Stop Assistant control"))
    }

    @Test
    fun coversCurrentAccessibilityAndAssistantPreferences() {
        listOf(
            "Friendly, Neutral, or Professional",
            "Short, Normal, or Detailed",
            "Large Text",
            "Android's system font scaling",
            "High Contrast",
            "Processing Haptic Feedback",
            "heartbeat or lub-dub vibration",
            "processing ends or speaking begins",
            "Session End Haptic Feedback",
            "conversation has completely finished",
            "Speech Speed",
            "Slow, Normal, Fast, or Very Fast",
            "Set speech speed to fast"
        ).forEach { fact -> assertTrue("Missing preference fact: $fact", prompt.contains(fact)) }
    }

    @Test
    fun recordsGuidanceSafetyAndUnsupportedFeatures() {
        listOf(
            "seven voice-configurable user settings",
            "cannot be changed by voice",
            "no wake word",
            "calendar or email integration",
            "weather, news, traffic",
            "do not recur automatically",
            "does not mutate the stored template",
            "must not claim that an operation occurred"
        ).forEach { fact -> assertTrue("Missing limitation: $fact", prompt.contains(fact)) }

        assertFalse(prompt.contains("Hey Assistant"))
        assertFalse(prompt.contains("automatic habit learning"))
        assertFalse(prompt.contains("formal accessibility-standard compliance is guaranteed"))
    }

    @Test
    fun preservesDynamicCurrentInteractionGuidance() {
        assertTrue(prompt.contains("Interaction state:\nDELETE_CONFIRMATION"))
        assertTrue(prompt.contains("Current interaction:\nA deletion is waiting for confirmation."))
        assertTrue(prompt.contains("What the user may say now:\n- Confirm or decline the deletion."))
    }
}
