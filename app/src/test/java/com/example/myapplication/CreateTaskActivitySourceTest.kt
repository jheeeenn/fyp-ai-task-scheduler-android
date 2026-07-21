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
    fun voiceInputUsesLocalFirstResolverAndCentralMoveHandler() {
        assertTrue(source.contains("private val createDraftMoveInterpreter = CreateDraftMoveInterpreter()"))
        assertTrue(source.contains("private lateinit var createDraftSemanticOrchestrator: CreateDraftSemanticOrchestrator"))
        assertTrue(voiceHandler.contains("createDraftSemanticOrchestrator.resolveLocal(normalized, capturedState)"))
        assertTrue(voiceHandler.contains("handleCreateDraftMove(localResult.move)"))
        assertTrue(voiceHandler.contains("requestCreateDraftFallback(normalized, capturedState, fallbackReason)"))
        assertTrue(moveHandler.contains("CreateDraftMove.ConfirmSave ->"))
        assertTrue(moveHandler.contains("is CreateDraftMove.ChangeField ->"))
        assertTrue(moveHandler.contains("CreateDraftMove.Unknown ->"))
    }

    @Test
    fun createWorkflowUsesBoundedConversationFallbackWithoutTaskAgent() {
        assertTrue(source.contains("ConversationAgentClient(this)"))
        assertTrue(source.contains("CreateDraftSemanticOrchestrator("))
        listOf(
            "LaptopAgentClient",
            "AgentOrchestrator",
            "TaskAgentClient"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
        assertFalse(source.contains("ConversationOrchestrator("))
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
    fun fallbackResultsUseTheSameAndroidMoveHandler() {
        val fallbackBody = source
            .substringAfter("private fun requestCreateDraftFallback")
            .substringBefore("private fun logCreateMoveResolution")
        assertTrue(fallbackBody.contains("createDraftSemanticOrchestrator.resolve("))
        assertTrue(fallbackBody.contains("handleCreateDraftMove(result.move)"))
        assertFalse(fallbackBody.contains("applyTitle("))
        assertFalse(fallbackBody.contains("applySpokenDate("))
        assertFalse(fallbackBody.contains("applySpokenTime("))
        assertFalse(fallbackBody.contains("saveTask()"))
    }

    @Test
    fun onlyUnresolvedTemporalCandidatesRequestFallback() {
        val triggerBody = source
            .substringAfter("private fun fallbackReasonFor")
            .substringBefore("private fun classifyTemporalCandidate")
        val classifierBody = source
            .substringAfter("private fun classifyTemporalCandidate")
            .substringBefore("private fun isUnresolvedUnspecifiedCorrection")

        assertTrue(triggerBody.contains("== CreateDraftCandidateStatus.UNRESOLVED"))
        assertTrue(triggerBody.contains("CreateDraftFallbackReason.TEMPORAL_UNRESOLVED"))
        assertTrue(classifierBody.contains("TemporalActionPolicy.validateClarification"))
        assertTrue(classifierBody.contains("CreateDraftCandidateStatus.REJECTED_BY_POLICY"))
        assertFalse(triggerBody.contains("REJECTED_BY_POLICY)"))
    }

    @Test
    fun staleFallbackResultsAreDiscardedAndConcurrentInputIsIgnored() {
        val fallbackBody = source
            .substringAfter("private fun requestCreateDraftFallback")
            .substringBefore("private fun logCreateMoveResolution")
        assertTrue(source.contains("private var isResolvingCreateDraftMove = false"))
        assertTrue(source.contains("private var createDraftResolutionGeneration = 0L"))
        assertTrue(voiceHandler.contains("if (isResolvingCreateDraftMove)"))
        assertTrue(fallbackBody.contains("requestGeneration != createDraftResolutionGeneration || dialogState != capturedState"))
        assertTrue(fallbackBody.contains("STALE_RESULT_DISCARDED"))
    }

    @Test
    fun fallbackFailureRecoversWithoutDirectDraftMutation() {
        val fallbackBody = source
            .substringAfter("private fun requestCreateDraftFallback")
            .substringBefore("private fun logCreateMoveResolution")
        val unknownBranch = moveHandler
            .substringAfter("CreateDraftMove.Unknown ->")
            .substringBefore("}")
        assertTrue(fallbackBody.contains("handleCreateDraftMove(result.move)"))
        assertTrue(unknownBranch.contains("recoverFromUnknownMove()"))
        assertFalse(fallbackBody.contains("pendingTaskState.title ="))
        assertFalse(fallbackBody.contains("selectedDate ="))
        assertFalse(fallbackBody.contains("selectedTime ="))
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
