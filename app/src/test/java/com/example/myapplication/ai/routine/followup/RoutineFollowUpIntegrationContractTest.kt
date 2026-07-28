package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.conversation.ConversationAgentClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineFollowUpIntegrationContractTest {
    @Test
    fun dedicatedPromptStatesTheBoundedAuthorityAndIncludesNaturalExamples() {
        val prompt = ConversationAgentClient.ROUTINE_FOLLOW_UP_SYSTEM_PROMPT
        listOf(
            "inside an existing Smart Routine Builder draft",
            "do not create, save, update, or delete tasks",
            "do not schedule reminders",
            "do not access Room or task records",
            "Android's supplied routine state is authoritative",
            "Android validates every candidate value",
            "Do not calculate relative dates into final calendar dates",
            "Do not invent a step, date, time, or AM/PM value",
            "CONFIRM only while the state is WAITING_FOR_CONFIRMATION",
            "expected missing date or time is determined by Android",
            "use the first of August 2026",
            "actually make it next Tuesday",
            "use 8:15 AM for that one",
            "that looks right, go ahead",
            "no, make the second one 8:30 PM",
            "could you call the third step charge my phone",
            "say the routine again",
            "add another step",
            "yes no"
        ).forEach { required ->
            assertTrue("Missing prompt contract: $required", prompt.contains(required))
        }
        assertFalse(prompt.contains("insertRootTasksAtomically"))
        assertFalse(prompt.contains("scheduleReminderFromTask"))
    }

    @Test
    fun clientUsesDedicatedStrictPrimaryOnlyRequest() {
        val source = File(
            "src/main/java/com/example/myapplication/ai/conversation/ConversationAgentClient.kt"
        ).readText()
        val method = source
            .substringAfter("override suspend fun interpretRoutineFollowUp")
            .substringBefore("private fun executeConversationRequest")

        assertTrue(method.contains("RequestKind.ROUTINE_FOLLOW_UP_MOVE"))
        assertTrue(source.contains("routineFollowUpMoveResponseFormat()"))
        assertTrue(source.contains("ROUTINE_FOLLOW_UP_TEMPERATURE = 0.0"))
        assertTrue(source.contains("ROUTINE_FOLLOW_UP_MAX_TOKENS = 128"))
        assertTrue(source.contains("ROUTINE_MOVE_AGENT_RAW"))
        assertTrue(source.contains("catch (e: CancellationException)"))
        assertFalse(method.contains("conversationDecisionResponseFormat"))
        assertFalse(method.contains("TaskAgent"))
        assertFalse(method.contains("retry"))
    }

    @Test
    fun homeIsLocalFirstRevalidatesCandidatesAndGuardsSemanticDelivery() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt")
            .readText()
        val followUp = source
            .substringAfter("private fun handleRoutineFollowUp")
            .substringBefore("private fun handleRoutineDraftUpdate")
        val semantic = source
            .substringAfter("private fun launchRoutineSemanticFollowUp")
            .substringBefore("private fun applyResolvedRoutineMove")
        val apply = source
            .substringAfter("private fun applyResolvedRoutineMove")
            .substringBefore("private fun speakStateAppropriateRoutineClarification")

        assertTrue(followUp.contains("resolveImmediate"))
        assertTrue(followUp.contains("routineDraftController.provideSharedDate(normalized)"))
        assertTrue(followUp.contains("routineDraftController.provideNextStepTime(normalized)"))
        assertTrue(followUp.contains("afterRawSharedDate"))
        assertTrue(followUp.contains("afterRawStepTime"))
        assertTrue(semantic.contains("isAssistantRequestCurrent(requestToken)"))
        assertTrue(semantic.contains("RoutineFollowUpDeliveryGuard.shouldDeliver"))
        assertTrue(semantic.contains("capturedRevision"))
        assertTrue(semantic.contains("currentRevision"))
        assertTrue(semantic.contains("DROP_STALE"))
        assertTrue(semantic.contains("catch (e: CancellationException)"))
        assertTrue(apply.contains("provideSharedDate(move.value)"))
        assertTrue(apply.contains("provideNextStepTime(move.value)"))
        assertTrue(apply.contains("changeStepTime(move.stepIndex, move.value)"))
        assertTrue(apply.contains("changeStepTitle(move.stepIndex, move.value)"))
        assertTrue(apply.contains("savePendingRoutine()"))
        assertFalse(semantic.contains("insertRootTasksAtomically"))
        assertFalse(semantic.contains("scheduleReminderFromTask"))
    }

    @Test
    fun stateSpecificDateSpeechDistinguishesMissingFromConstrainedDates() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt")
            .readText()
        val speech = source
            .substringAfter("private fun sharedDateInvalidSpeech")
            .substringBefore("private fun logRoutineMoveLocal")

        assertTrue(
            speech.contains(
                "Please provide one exact future date, such as 4 August 2026."
            )
        )
        assertTrue(
            speech.contains(
                "Please provide one exact date that satisfies the routine's date constraints."
            )
        )
    }
}
