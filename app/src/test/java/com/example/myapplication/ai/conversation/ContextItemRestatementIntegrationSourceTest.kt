package com.example.myapplication.ai.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContextItemRestatementIntegrationSourceTest {
    private val home =
        File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
    private val policy =
        File(
            "src/main/java/com/example/myapplication/ai/conversation/taskcontext/" +
                "ContextItemRestatementPolicy.kt"
        ).readText()

    @Test
    fun targetedRestatementIsInterceptedBeforeGenericAndModelRouting() {
        val command = home
            .substringAfter("private fun handleVoiceCommand(command: String)")
            .substringBefore("lifecycleScope.launch {")
        val targeted = command.indexOf("handleContextItemRestatement(normalized)")
        val generic = command.indexOf("handleQueryReadingFollowUp(")
        val localClassifier = command.indexOf("conversationIntentClassifier.classify(")

        assertTrue(targeted >= 0)
        assertTrue(targeted < generic)
        assertTrue(targeted < localClassifier)
    }

    @Test
    fun boundedHandlerAndPolicyHaveNoRoomTaskOrConversationAgentCalls() {
        val handler = home
            .substringAfter("private fun handleContextItemRestatement(")
            .substringBefore("private fun executeContextRead(")
        val forbidden = listOf(
            "AppDatabase",
            "taskDao",
            "getRootTasks",
            "getSubtasks",
            "agentOrchestrator",
            "conversationOrchestrator.process(",
            "processContextReadRepair("
        )

        forbidden.forEach {
            assertFalse("handler contains $it", handler.contains(it))
            assertFalse("policy contains $it", policy.contains(it))
        }
        assertTrue(handler.contains("readOnlyTaskContextStore.capture()"))
        assertTrue(handler.contains("ContextItemRestatementPolicy.resolve("))
    }

    @Test
    fun sharedContextReadRecordsAndSpeaksOnceThenOwnsAuthoritativeRepeat() {
        val executor = home
            .substringAfter("private fun executeContextRead(")
            .substringBefore("private fun handleQueryReadingFollowUp(")
        val success = executor.substringAfter("val item = requireNotNull(validation.item)")

        assertTrue(success.contains("ReadOnlyTaskContextResponseRenderer.render("))
        assertEquals(
            1,
            success.split("recordAuthoritativeContextRead(").size - 1
        )
        assertEquals(
            1,
            success.split("authoritativeRepeatState = AuthoritativeRepeatState(").size - 1
        )
        assertEquals(1, success.split("assistantSession.speak(").size - 1)
        assertTrue(success.contains("kind = RepeatableSpeechKind.CONTEXT_READ"))
        assertTrue(success.contains("contextGeneration = taskContextCapture.snapshot.generation"))
        assertFalse(executor.contains("readOnlyTaskContextStore.clear("))
        assertFalse(executor.contains("replaceRecentQueryResults("))
        assertFalse(executor.contains("replaceDailyBriefingResults("))
    }

    @Test
    fun unavailableSelectorClarifiesWithoutRepeatingOrChangingContext() {
        val handler = home
            .substringAfter("private fun handleContextItemRestatement(")
            .substringBefore("private fun executeContextRead(")
        val unavailable = handler
            .substringAfter("ContextItemRestatementDisposition.UNAVAILABLE_SELECTOR,")

        assertTrue(unavailable.contains("ConversationRoute.ASK_CLARIFICATION"))
        assertTrue(unavailable.contains("resolution.clarification"))
        assertTrue(unavailable.contains("listenAgain = true"))
        assertFalse(handler.contains("repeatLastAuthoritativeSpeech("))
        assertFalse(handler.contains("repeatCurrentTaskQueryPage("))
        assertFalse(handler.contains("readOnlyTaskContextStore.clear("))
        assertFalse(handler.contains("replaceRecentQueryResults("))
        assertFalse(handler.contains("replaceDailyBriefingResults("))
    }

    @Test
    fun centralConversationContextReadUsesTheSameExecutor() {
        val branch = home
            .substringAfter("ConversationRoute.CONTEXT_READ -> {")
            .substringBefore("ConversationRoute.CONTEXT_ACTION ->")

        assertTrue(branch.contains("ReadOnlyTaskContextReadValidator.validate("))
        assertTrue(branch.contains("executeContextRead("))
        assertTrue(branch.contains("return@launch"))
    }

    @Test
    fun genericAndPageRepeatExecutorsRemainExactStoredSpeechPaths() {
        val executor = home
            .substringAfter("private fun executeQueryReadingControl(")
            .substringBefore("private fun currentQueryReadingInteractionState()")
        val repeats = home
            .substringAfter("private fun repeatCurrentTaskQueryPage()")
            .substringBefore("private fun endAssistantConversation()")

        assertTrue(
            executor.contains(
                "ConversationQueryReadingMove.REPEAT_LAST -> repeatLastAuthoritativeSpeech()"
            )
        )
        assertTrue(
            executor.contains(
                "ConversationQueryReadingMove.REPEAT_PAGE -> repeatCurrentTaskQueryPage()"
            )
        )
        assertTrue(repeats.contains("assistantSession.speak(pageState.speech"))
        assertTrue(repeats.contains("assistantSession.speak(repeatState.speech"))
        assertFalse(repeats.contains("AppDatabase"))
        assertFalse(repeats.contains("agentOrchestrator"))
    }
}
