package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.taskcontext.ContextFocusCarryForwardPolicy
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextReadValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextStore
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskDetailDeleteContextContinuityTest {
    private val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun taskDetailPublicationProducesOneT1WithoutRoomIdInPrompt() {
        val store = ReadOnlyTaskContextStore()
        store.replaceTaskDetailResult(
            TaskEntity(
                id = 987654321L,
                title = "take breakfast",
                dueDate = "19/08/2026",
                dueTime = "8:00 AM"
            )
        )

        val capture = store.capture()

        assertEquals(TaskContextScope.TASK_DETAIL, capture.snapshot.scope)
        assertEquals(1, capture.snapshot.items.size)
        assertEquals("T1", capture.snapshot.items.single().ref)
        assertFalse(capture.promptText.contains("987654321"))
        assertFalse(capture.promptText.contains("task_id", ignoreCase = true))
        assertEquals(987654321L, store.resolveRef("T1", capture.snapshot.generation))
    }

    @Test
    fun deleteConfirmationIsEligibleOnlyForReadOnlyContextPaths() {
        val restatementHandler = home
            .substringAfter("private fun handleContextItemRestatement(")
            .substringBefore("private fun handleContextItemRead(")
        val readHandler = home
            .substringAfter("private fun handleContextItemRead(")
            .substringBefore("private fun executeContextRead(")
        val requestFlow = home
            .substringAfter("val taskContextCapture = readOnlyTaskContextStore.capture()")
            .substringBefore("val contextFocus = conversationOrchestrator.contextFocusForSnapshot(")

        assertTrue(restatementHandler.contains("HomeFollowUpContext.DELETE_CONFIRMATION"))
        assertTrue(readHandler.contains("HomeFollowUpContext.DELETE_CONFIRMATION"))
        assertTrue(requestFlow.contains("HomeFollowUpContext.DELETE_CONFIRMATION"))
        assertFalse(readHandler.contains("confirmPendingDelete()"))
        assertFalse(readHandler.contains("ContextActionDecisionValidator"))
        assertFalse(restatementHandler.contains("confirmPendingDelete()"))
    }

    @Test
    fun schemaFailureTriesValidatedFocusedReadBeforeGenericFailureSpeech() {
        val catchBlock = home
            .substringAfter("catch (e: ConversationOrchestratorException)")
            .substringBefore("if (\n                    conversationDecision.route == ConversationRoute.CONTEXT_READ")

        val focusFallback = catchBlock.indexOf("ContextFocusCarryForwardPolicy.resolve(")
        val validator = catchBlock.indexOf("ReadOnlyTaskContextReadValidator.validate(")
        val deleteFallback = catchBlock.indexOf("ContextDeleteFailureFallbackPolicy.resolve(")
        val genericSpeech = catchBlock.indexOf("assistantSession.speak(")

        assertTrue(focusFallback >= 0)
        assertTrue(validator > focusFallback)
        assertTrue(deleteFallback > validator)
        assertTrue(genericSpeech > deleteFallback)
        assertTrue(catchBlock.contains("isResultInteraction = isResultInteraction"))
        assertTrue(catchBlock.contains("currentGeneration = readOnlyTaskContextStore.currentGeneration()"))
        assertFalse(catchBlock.contains("resolveRef("))
        assertFalse(catchBlock.contains("task.id"))
    }

    @Test
    fun focusedTitleReadValidatesWhileMissingFocusCannotInventT1() {
        val store = ReadOnlyTaskContextStore()
        store.replaceTaskDetailResult(TaskEntity(id = 41L, title = "take breakfast"))
        val capture = store.capture()
        val item = capture.snapshot.items.single()
        val focus = ConversationContextFocus(
            available = true,
            ref = item.ref,
            generation = capture.snapshot.generation,
            detail = ConversationContextDetail.SUMMARY,
            title = item.title
        )

        val decision = ContextFocusCarryForwardPolicy.resolve(
            normalizedText = "what is the task title",
            focus = focus,
            capturedSnapshot = capture.snapshot,
            isResultInteraction = true
        )
        val validation = decision?.let {
            ReadOnlyTaskContextReadValidator.validate(
                decision = it,
                capturedSnapshot = capture.snapshot,
                currentGeneration = store.currentGeneration(),
                normalizedText = "what is the task title"
            )
        }

        assertNotNull(decision)
        assertEquals(ConversationRoute.CONTEXT_READ, decision?.route)
        assertEquals("T1", decision?.contextRef)
        assertEquals(ConversationContextDetail.TITLE, decision?.contextDetail)
        assertTrue(validation?.isValid == true)
        assertNull(
            ContextFocusCarryForwardPolicy.resolve(
                normalizedText = "what is the task title",
                focus = null,
                capturedSnapshot = capture.snapshot,
                isResultInteraction = true
            )
        )
    }
}
