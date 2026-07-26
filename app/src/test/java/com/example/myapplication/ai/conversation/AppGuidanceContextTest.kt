package com.example.myapplication.ai.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGuidanceContextTest {
    private val context = AppGuidanceContext(
        currentScreen = "Home",
        assistantPurpose = "Help the user manage scheduled tasks.",
        supportedCapabilities = listOf("Create tasks.", "Query tasks."),
        screenActions = listOf("Open today's tasks."),
        inputMethods = listOf("Use the Talk Assistant button."),
        interactionState = "NONE",
        currentInteraction = "No follow-up is pending.",
        currentInteractionGuidance = listOf("Ask for app guidance."),
        usageExamples = listOf("Say, 'Show my tasks tomorrow.'"),
        limitations = listOf("Task creation opens a form for review.")
    )

    @Test
    fun allNonEmptyFieldsAppearWithStableSectionLabels() {
        val prompt = context.toPromptText()

        listOf(
            "Current screen:",
            "Assistant purpose:",
            "Supported capabilities:",
            "Available screen actions:",
            "Input methods:",
            "Interaction state:",
            "Current interaction:",
            "What the user may say now:",
            "Example commands:",
            "Limitations:"
        ).forEach { label -> assertTrue("Missing label: $label", prompt.contains(label)) }

        listOf(
            context.currentScreen,
            context.assistantPurpose,
            *context.supportedCapabilities.toTypedArray(),
            *context.screenActions.toTypedArray(),
            *context.inputMethods.toTypedArray(),
            context.interactionState,
            context.currentInteraction,
            *context.currentInteractionGuidance.toTypedArray(),
            *context.usageExamples.toTypedArray(),
            *context.limitations.toTypedArray()
        ).forEach { value -> assertTrue("Missing value: $value", prompt.contains(value)) }
    }

    @Test
    fun emptyItemsAndEmptySectionsAreOmitted() {
        val prompt = context.copy(
            supportedCapabilities = listOf("Create tasks.", "", "   "),
            screenActions = emptyList()
        ).toPromptText()

        assertEquals(1, prompt.lines().count { it == "- Create tasks." })
        assertFalse(prompt.contains("- \n"))
        assertFalse(prompt.contains("Available screen actions:"))
    }

    @Test
    fun exactInteractionStateIsSerializedSeparatelyFromNaturalGuidance() {
        listOf("NONE", "QUERY_COUNT", "QUERY_PAGE").forEach { state ->
            val prompt = context.copy(interactionState = state).toPromptText()
            val serializedState = prompt
                .substringAfter("Interaction state:\n")
                .substringBefore("\n")

            assertEquals(state, serializedState)
            assertTrue(prompt.contains("Current interaction:\n${context.currentInteraction}"))
        }
    }

    @Test
    fun trustedContextContainsNoTaskIdentifiersOrRoomTerminology() {
        val prompt = context.toPromptText()

        assertFalse(prompt.contains("task_id", ignoreCase = true))
        assertFalse(prompt.contains("task ID", ignoreCase = true))
        assertFalse(prompt.contains("Room"))
        assertFalse(prompt.contains("TaskEntity"))
        assertFalse(prompt.contains("T1"))
        assertFalse(prompt.contains("Take medicine"))
        assertFalse(prompt.contains("http://"))
        assertFalse(prompt.contains("https://"))
    }

    @Test
    fun outputIsDeterministicAndConciseEnoughForPromptUse() {
        val first = context.toPromptText()
        val second = context.toPromptText()

        assertEquals(first, second)
        assertTrue("Prompt context should remain compact", first.length < 2_000)
    }
}
