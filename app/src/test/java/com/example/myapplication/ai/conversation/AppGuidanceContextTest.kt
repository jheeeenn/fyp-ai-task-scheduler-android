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
            "Interaction priority:",
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
    fun activeInteractionPrecedesGenericCatalogAndCarriesPriorityRule() {
        val prompt = context.copy(
            interactionState = "DELETE_CONFIRMATION",
            currentInteraction = "One deletion is waiting for confirmation.",
            currentInteractionGuidance = listOf("Say yes or no.")
        ).toPromptText()

        val priority = prompt.indexOf("Interaction priority:\nACTIVE")
        val state = prompt.indexOf("Interaction state:\nDELETE_CONFIRMATION")
        val current = prompt.indexOf("Current interaction:\nOne deletion is waiting")
        val nextMoves = prompt.indexOf("What the user may say now:\n- Say yes or no.")
        val capabilities = prompt.indexOf("Supported capabilities:")

        assertTrue(priority >= 0)
        assertTrue(state > priority)
        assertTrue(current > state)
        assertTrue(nextMoves > current)
        assertTrue(capabilities > nextMoves)
        assertTrue(prompt.contains("Interaction priority rule:"))
        assertTrue(prompt.contains("before general application guidance"))
    }

    @Test
    fun noPendingInteractionMarksNoneAndKeepsGeneralGuidanceAvailable() {
        val prompt = context.toPromptText()

        assertTrue(prompt.contains("Interaction priority:\nNONE"))
        assertFalse(prompt.contains("Interaction priority rule:"))
        assertTrue(prompt.contains("Supported capabilities:\n- Create tasks."))
        assertTrue(prompt.contains("Example commands:\n- Say, 'Show my tasks tomorrow.'"))
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
