package com.example.myapplication.ai.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineDiagnosticLoggingContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val voice = mainRoot.resolve("voice/AssistantVoiceSession.kt").readText()
    private val debugLog =
        mainRoot.resolve("diagnostics/DebugDiagnosticLog.kt").readText()
    private val transcriptLog =
        mainRoot.resolve("diagnostics/AssistantTranscriptDiagnosticLogger.kt").readText()
    private val home = mainRoot.resolve("HomeActivity.kt").readText()
    private val laptop =
        mainRoot.resolve("ai/agent/LaptopAgentClient.kt").readText()
    private val agent =
        mainRoot.resolve("ai/agent/AgentOrchestrator.kt").readText()
    private val controller =
        mainRoot.resolve("ai/routine/RoutineDraftController.kt").readText()
    private val batchCreator =
        mainRoot.resolve("ai/routine/RoutineTaskBatchCreator.kt").readText()
    private val conversation =
        mainRoot.resolve("ai/conversation/ConversationOrchestrator.kt").readText()

    @Test
    fun finalVoiceAndTypedInputsUseBuildSensitiveTranscriptPolicyButPartialsDoNot() {
        val finalResults = voice
            .substringAfter("override fun onResults")
            .substringBefore("override fun onPartialResults")
        val partialResults = voice
            .substringAfter("override fun onPartialResults")
            .substringBefore("override fun onEvent")
        val typed = voice
            .substringAfter("fun submitTypedText")
            .substringBefore("fun startVoiceFlow")

        assertTrue(finalResults.contains("requireNotNull(finalRecognizedText)"))
        assertTrue(finalResults.contains("source = \"VOICE\""))
        assertTrue(typed.contains("logUserTranscript(typedText, source = \"TYPED\")"))
        assertFalse(partialResults.contains("logUserTranscript"))
        assertFalse(partialResults.contains("ASSISTANT_TRANSCRIPT"))
        assertTrue(debugLog.contains("if (!BuildConfig.DEBUG) return"))
        assertTrue(voice.contains("AssistantTranscriptDiagnosticLogger.user"))
        assertTrue(transcriptLog.contains("AssistantTranscriptLogPolicy.visibility(BuildConfig.DEBUG)"))
        assertTrue(transcriptLog.contains("AssistantTranscriptVisibility.FULL"))
        assertTrue(transcriptLog.contains("AssistantTranscriptVisibility.REDACTED"))
    }

    @Test
    fun everyDirectAssistantSpeechDeliveryHasExactlyOneTranscriptCall() {
        val panelIdentification = voice
            .substringAfter("private fun speakPanelIdentification(text: String)")
            .substringBefore("private fun isPanelSpeechLifecycleEligible()")
        val conversationalVoice = voice.replace(panelIdentification, "")
        val directSpeechCalls = Regex("""voiceHelper\.speak\(""")
            .findAll(conversationalVoice)
            .count()
        val transcriptCalls = Regex("""logAssistantTranscript\(""")
            .findAll(voice)
            .count() - 1 // private helper declaration

        assertEquals(6, directSpeechCalls)
        assertEquals(directSpeechCalls, transcriptCalls)
        assertTrue(panelIdentification.contains("voiceHelper.speak(identification)"))
        assertFalse(panelIdentification.contains("logAssistantTranscript"))
        assertTrue(transcriptLog.contains("role=ASSISTANT\\ndelivery=SPEAK"))
        assertTrue(transcriptLog.contains("content=REDACTED"))
        assertTrue(transcriptLog.contains("characterCount="))
    }

    @Test
    fun routineRawContentIsDebugOnlyAndHiddenReasoningIsNeverReadOrLogged() {
        val rawBlock = laptop
            .substring(laptop.indexOf("val content ="))
            .substringAfter("if (boundedRoutineExtraction) {")
            .substringBefore("if (content.isBlank())")
        val allProduction = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n", transform = File::readText)

        assertTrue(rawBlock.contains("\"ROUTINE_EXTRACTION_RAW\""))
        assertTrue(rawBlock.contains("\"content=\$content\""))
        assertTrue(rawBlock.contains("DebugDiagnosticLog.longEvent"))
        assertFalse(allProduction.contains("reasoning_content"))
    }

    @Test
    fun parsedExtractionAndEveryStepHaveDiagnostics() {
        assertTrue(agent.contains("\"ROUTINE_EXTRACTION_DEBUG\""))
        assertTrue(agent.contains("routineTitle=\${response.routineTitle}"))
        assertTrue(agent.contains("needClarification=\${response.needClarification}"))
        assertTrue(agent.contains("response.steps.forEachIndexed"))
        assertTrue(agent.contains("\"ROUTINE_EXTRACTION_STEP\""))
        assertTrue(agent.contains("index=\${index + 1}"))
        assertTrue(agent.contains("dateText=\${step.dateText}"))
        assertTrue(agent.contains("timeText=\${step.timeText}"))
    }

    @Test
    fun extractionAdmissionPolicyLogsAdvisoryFlagStructureActionAndReason() {
        assertTrue(controller.contains("\"ROUTINE_EXTRACTION_POLICY\""))
        assertTrue(
            controller.contains(
                "modelNeedClarification=\${extraction.needClarification}"
            )
        )
        assertTrue(controller.contains("structurallyValid=\$structurallyValid"))
        assertTrue(controller.contains("action=\$action"))
        assertTrue(controller.contains("reason=\$reason"))
        assertTrue(controller.contains("\"CONTINUE_ANDROID_COLLECTION\""))
        assertTrue(controller.contains("\"REJECT\""))
        assertTrue(controller.contains("\"MODEL_FLAG_ADVISORY\""))
        assertTrue(controller.contains("\"STRUCTURE_VALID\""))
        assertTrue(controller.contains("\"LOW_CONFIDENCE\""))
        assertTrue(controller.contains("\"INVALID_STEP_COUNT\""))
        assertTrue(controller.contains("\"EMPTY_STEP_TITLE\""))
        assertTrue(controller.contains("\"INVALID_DATE\""))
    }

    @Test
    fun temporalValidationLogsClassificationAndAcceptedOrRejectedClarifications() {
        assertTrue(controller.contains("\"ROUTINE_STEP_VALIDATION\""))
        assertTrue(controller.contains("dateClassification=\${pendingStep.dateClassification.name}"))
        assertTrue(controller.contains("extraction.steps.mapIndexed"))
        assertTrue(controller.contains("\"ROUTINE_SHARED_DATE_VALIDATION\""))
        assertTrue(controller.contains("\"ROUTINE_DATE_CONSTRAINT_CHECK\""))
        assertTrue(controller.contains("satisfiesEveryConstraint="))
        assertTrue(controller.contains("\"ACCEPTED\" else \"REJECTED\""))
        assertTrue(controller.contains("\"ROUTINE_STEP_TIME_VALIDATION\""))
        assertTrue(controller.contains("constraintSatisfied="))
    }

    @Test
    fun followUpMoveResultAndAuthoritativeResponseKindsAreLogged() {
        assertTrue(home.contains("\"ROUTINE_FOLLOW_UP_DEBUG\""))
        assertTrue(home.contains("\"ROUTINE_FOLLOW_UP_RESULT\""))
        assertTrue(home.contains("interpretedMove="))
        assertTrue(home.contains("draftRevision="))
        assertTrue(home.contains("issue="))
        assertTrue(home.contains("\"ROUTINE_RESPONSE_DEBUG\""))
        listOf(
            "EXTRACTION_FAILURE",
            "ASK_SHARED_DATE",
            "ASK_STEP_TIME",
            "PROPOSAL",
            "INVALID_DATE",
            "INVALID_TIME",
            "REVISION_HELP",
            "ALREADY_SAVING",
            "CANCELLED",
            "SAVE_RESULT"
        ).forEach { kind ->
            assertTrue("Missing response kind $kind", home.contains(kind))
        }
        assertTrue(home.contains("text=\$text"))
        assertTrue(home.contains("DebugDiagnosticLog.longEvent"))
    }

    @Test
    fun conversationDecisionIncludesAllAcceptedStructuredFields() {
        assertTrue(conversation.contains("\"CONVERSATION_DECISION_DEBUG\""))
        listOf(
            "route=",
            "task_text=",
            "reply=",
            "context_ref=",
            "context_detail=",
            "context_action=",
            "query_reading_move=",
            "query_presentation_hint=",
            "confidence=",
            "listen_again=",
            "source="
        ).forEach { field ->
            assertTrue("Missing decision field $field", conversation.contains(field))
        }
    }

    @Test
    fun saveTasksDatabaseIdsAndReminderOutcomesAreDebugOnly() {
        assertTrue(batchCreator.contains("\"ROUTINE_SAVE_DEBUG\""))
        assertTrue(batchCreator.contains("phase=BEGIN"))
        assertTrue(batchCreator.contains("\"ROUTINE_SAVE_TASK\""))
        assertTrue(batchCreator.contains("parentTaskId=\${task.parentTaskId}"))
        assertTrue(batchCreator.contains("phase=DATABASE_COMPLETE"))
        assertTrue(batchCreator.contains("insertedIds="))
        assertTrue(batchCreator.contains("\"ROUTINE_REMINDER_DEBUG\""))
        assertTrue(batchCreator.contains("scheduled=\$scheduled"))
        assertTrue(batchCreator.contains("phase=COMPLETE"))
        assertTrue(batchCreator.contains("DebugDiagnosticLog.event"))
        assertTrue(debugLog.contains("BuildConfig.DEBUG"))
    }

    @Test
    fun privacySafeReleaseLogsAndRoutineBehaviourContractsRemain() {
        assertTrue(home.contains("\"ROUTINE_DRAFT\""))
        assertTrue(home.contains("\"ROUTINE_SAVE\""))
        assertTrue(home.contains("taskCount=\${result.taskCount}"))
        assertTrue(laptop.contains("\"ROUTINE_EXTRACTION_HTTP\""))
        assertTrue(laptop.contains("responseChars=\${body.length}"))
        assertTrue(home.contains("routineDraftController.applyExtraction("))
        assertTrue(home.contains("routineDraftController.provideSharedDate(normalized)"))
        assertTrue(home.contains("routineDraftController.provideNextStepTime(normalized)"))
        assertTrue(home.contains("routineDraftController.markSaving()"))
        assertTrue(batchCreator.contains("store.insertRootTasksAtomically(tasks)"))
        assertTrue(batchCreator.contains("reminderScheduler.schedule(task.copy(id = id))"))
    }
}
