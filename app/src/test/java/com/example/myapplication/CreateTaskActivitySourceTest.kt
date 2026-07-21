package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreateTaskActivitySourceTest {
    private val source = File("src/main/java/com/example/myapplication/CreateTaskActivity.kt").readText()
    private val voiceHandler = source
        .substringAfter("private fun handleVoiceCommand")
        .substringBefore("private fun openDatePicker")
    private val moveHandler = source
        .substringAfter("private fun handleCreateDraftMove")
        .substringBefore("private fun handleFieldChange")
    private val saveBody = source
        .substringAfter("private fun saveTask")
        .substringBefore("private fun formatDateForSpeech")

    @Test
    fun voiceInputDelegatesToInitializedInterpreterAndCentralMoveHandler() {
        assertTrue(source.contains("private val createDraftMoveInterpreter = CreateDraftMoveInterpreter()"))
        assertTrue(voiceHandler.contains("createDraftMoveInterpreter.interpret(normalized, dialogState)"))
        assertTrue(voiceHandler.contains("handleCreateDraftMove(move)"))
        assertTrue(moveHandler.contains("CreateDraftMove.ConfirmSave ->"))
        assertTrue(moveHandler.contains("is CreateDraftMove.ChangeField ->"))
        assertTrue(moveHandler.contains("CreateDraftMove.Unknown ->"))
    }

    @Test
    fun createWorkflowDoesNotUseConversationOrTaskAgents() {
        listOf(
            "ConversationAgentClient",
            "ConversationOrchestrator",
            "LaptopAgentClient",
            "AgentOrchestrator"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
    }

    @Test
    fun saveTaskRemainsTheOnlyRoomInsertionAuthority() {
        assertEquals(1, Regex("dao\\.insert\\(").findAll(source).count())
        assertTrue(saveBody.contains("dao.insert("))
        assertTrue(saveBody.contains("scheduleReminder("))
        assertTrue(moveHandler.contains("saveTask()"))
    }

    @Test
    fun unknownConfirmationDoesNotApplyOrOverwriteTheTitle() {
        val unknownBranch = moveHandler
            .substringAfter("CreateDraftMove.Unknown ->")
            .substringBefore("}")
        assertTrue(unknownBranch.contains("recoverFromUnknownMove()"))
        assertFalse(unknownBranch.contains("applyTitle("))
    }

    @Test
    fun titleChangesUseStructuredFieldAndExtractedValue() {
        val fieldChangeBody = source
            .substringAfter("private fun handleFieldChange")
            .substringBefore("private fun handleProvidedField")
        val titleBody = source
            .substringAfter("private fun applyProvidedTitle")
            .substringBefore("private fun applyProvidedDate")

        assertTrue(fieldChangeBody.contains("CreateTaskDialogState.WAITING_FOR_TITLE"))
        assertTrue(fieldChangeBody.contains("responseManager.askChangeTitle()"))
        assertTrue(fieldChangeBody.contains("handleProvidedField(field, value)"))
        assertTrue(titleBody.contains("applyTitle(value)"))
        assertFalse(titleBody.contains("rawCommand"))
    }

    @Test
    fun dateAndTimeChangesRetainAndroidTemporalResolution() {
        assertTrue(source.contains("applySpokenDate(value, replacingConstraint = replacingField)"))
        assertTrue(source.contains("applySpokenTime(timeCandidate, replacingConstraint = replacingField)"))
        assertTrue(source.contains("TemporalExpressionResolver()"))
        assertTrue(source.contains("TemporalActionPolicy.evaluate"))
    }

    @Test
    fun cancellationAndDeterministicResponsesDoNotSave() {
        val cancelBody = source
            .substringAfter("private fun cancelCreateDraft")
            .substringBefore("private fun provideCreateDraftHelp")
        assertTrue(cancelBody.contains("resetTaskDraftState()"))
        assertTrue(cancelBody.contains("responseManager.cancelCreate()"))
        assertTrue(cancelBody.contains("finish()"))
        assertFalse(cancelBody.contains("saveTask()"))
        assertFalse(cancelBody.contains("dao.insert("))
        assertTrue(source.contains("responseManager.saveConfirmationHelp()"))
        assertTrue(source.contains("responseManager.correctionNotUnderstood()"))
    }

    @Test
    fun oldCompetingPhraseInterpretationWasRemoved() {
        listOf(
            "handleFollowUpInput",
            "tryApplyInlineCorrection",
            "extractInlineCorrectionValue",
            "looksLikeReasonableTitle",
            "private fun isYes",
            "private fun isNo"
        ).forEach { removed -> assertFalse(source.contains(removed)) }
    }
}
