package com.example.myapplication

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateTaskDialogState
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
    fun initialExplicitTitleCommandContinuesToTheNextMissingStep() {
        assertFalse(
            isCreateDraftFieldReplacement(
                field = CreateDraftField.TITLE,
                state = CreateTaskDialogState.WAITING_FOR_TITLE,
                pendingReplacementField = null,
                hasTitle = false,
                hasSelectedDate = false,
                hasSelectedTime = false
            )
        )

        val fieldChangeBody = source
            .substringAfter("private fun handleFieldChange")
            .substringBefore("private fun handleProvidedField")
        val titleBody = source
            .substringAfter("private fun applyProvidedTitle")
            .substringBefore("private fun applyProvidedDate")
        val confirmationBody = source
            .substringAfter("private fun returnToSaveConfirmation")
            .substringBefore("private fun isDraftCompleteForSaveConfirmation")

        assertTrue(fieldChangeBody.contains("pendingReplacementField = if (replacingField) field else null"))
        assertTrue(titleBody.contains("moveToNextMissingStep()"))
        assertTrue(confirmationBody.contains("if (!isDraftCompleteForSaveConfirmation())"))
        assertTrue(confirmationBody.indexOf("moveToNextMissingStep()") < confirmationBody.indexOf("WAITING_FOR_SAVE_CONFIRMATION"))
    }

    @Test
    fun explicitTitleCommandAtSaveConfirmationRemainsAReplacement() {
        assertTrue(
            isCreateDraftFieldReplacement(
                field = CreateDraftField.TITLE,
                state = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
                pendingReplacementField = null,
                hasTitle = true,
                hasSelectedDate = true,
                hasSelectedTime = true
            )
        )

        val titleBody = source
            .substringAfter("private fun applyProvidedTitle")
            .substringBefore("private fun applyProvidedDate")
        assertTrue(titleBody.contains("returnToSaveConfirmation(CreateDraftField.TITLE)"))
    }

    @Test
    fun pendingMarkerPreservesTwoTurnTitleReplacement() {
        val firstTurnIsReplacement = isCreateDraftFieldReplacement(
            field = CreateDraftField.TITLE,
            state = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = true
        )
        assertTrue(firstTurnIsReplacement)

        assertTrue(
            isCreateDraftFieldReplacement(
                field = CreateDraftField.TITLE,
                state = CreateTaskDialogState.WAITING_FOR_TITLE,
                pendingReplacementField = CreateDraftField.TITLE,
                hasTitle = true,
                hasSelectedDate = true,
                hasSelectedTime = true
            )
        )

        val fieldChangeBody = source
            .substringAfter("private fun handleFieldChange")
            .substringBefore("private fun handleProvidedField")
        val providedFieldBody = source
            .substringAfter("private fun handleProvidedField")
            .substringBefore("private fun applyProvidedTitle")
        assertTrue(fieldChangeBody.contains("CreateTaskDialogState.WAITING_FOR_TITLE"))
        assertTrue(providedFieldBody.contains("pendingReplacementField == field"))
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
