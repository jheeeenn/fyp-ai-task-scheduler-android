package com.example.myapplication

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTaskSemanticIntegrationSourceTest {
    private val edit = File("src/main/java/com/example/myapplication/EditTaskActivity.kt").readText()
    private val client = File(
        "src/main/java/com/example/myapplication/ai/conversation/ConversationAgentClient.kt"
    ).readText()
    private val semanticRoot = File(
        "src/main/java/com/example/myapplication/ai/conversation/taskedit"
    )
    private val orchestrator = semanticRoot.resolve("EditTaskSemanticOrchestrator.kt").readText()

    @Test
    fun realAssistantInputUsesTheBoundedEditSemanticPath() {
        val finalText = edit.substringAfter("override fun onAssistantFinalText(text: String)")
            .substringBefore("override fun onAssistantCancelled()")
        val voiceFlow = edit.substringAfter("private fun handleVoiceInput(text: String)")
            .substringBefore("private fun handleRelativeTemporalProposalInput(")

        assertTrue(finalText.contains("handleVoiceInput(text)"))
        assertTrue(voiceFlow.contains("requestEditTaskSemanticResolution(text)"))
        assertTrue(edit.contains("EditTaskSemanticOrchestrator("))
        assertTrue(edit.contains("semanticClient = ConversationAgentClient(this)"))
        assertTrue(client.contains(": CreateDraftSemanticClient"))
        assertTrue(client.contains("EditTaskSemanticClient"))
        assertTrue(client.contains("RequestKind.EDIT_TASK_MOVE"))
    }

    @Test
    fun semanticMovesDelegateToExistingAndroidAuthorityMethods() {
        val handler = edit.substringAfter("private fun handleEditTaskSemanticMove(")
            .substringBefore("private fun applySemanticTitleChange(")
        val title = edit.substringAfter("private fun applySemanticTitleChange(")
            .substringBefore("private fun applySemanticTemporalChange(")
        val temporal = edit.substringAfter("private fun applySemanticTemporalChange(")
            .substringBefore("private fun requestSemanticFieldChange(")

        assertTrue(handler.contains("saveTask(relativeSession.revision)"))
        assertTrue(handler.contains("saveTask()"))
        assertTrue(handler.contains("requestVoiceDeleteConfirmation()"))
        assertTrue(title.contains("etTaskTitle.setText(candidate)"))
        assertTrue(title.contains("askToSaveChanges()"))
        assertTrue(temporal.contains("applyProposedTemporalChange("))
        assertTrue(temporal.contains("askToSaveChanges()"))
        assertFalse(handler.contains("AppDatabase"))
        assertFalse(handler.contains("ReminderHelper"))
        assertFalse(handler.contains("performConfirmedDelete"))
    }

    @Test
    fun activeRelativeProposalRoutesGeneralEditsWithoutDiscardingItsSchedule() {
        val voice = edit.substringAfter("private fun handleVoiceInput(text: String)")
            .substringBefore("private fun requestEditTaskSemanticResolution(")
        val controls = edit.substringAfter("private fun handleRelativeTemporalProposalInput(")
            .substringBefore("private fun processRelativeTemporalCorrection(")
        val routing = edit.substringAfter("private fun routeEditTaskSemanticMove(")
            .substringBefore("private fun handleEditTaskSemanticMove(")
        val title = edit.substringAfter("private fun applySemanticTitleChange(")
            .substringBefore("private fun applySemanticTemporalChange(")
        val savePrompt = edit.substringAfter("private fun askToSaveChanges()")
            .substringBefore("private fun authoritativeSnapshotMatches")

        assertTrue(voice.contains("handleRelativeTemporalProposalInput(normalized)"))
        assertTrue(voice.contains("RelativeTemporalProposalState.ACTIVE"))
        assertTrue(voice.contains("requestEditTaskSemanticResolution(text)"))
        assertTrue(controls.contains(") ?: return false"))
        assertTrue(routing.contains("route=EDIT_SEMANTIC move="))
        assertTrue(routing.contains("route=RELATIVE_TEMPORAL_CORRECTION"))
        assertTrue(routing.contains("processRelativeTemporalCorrection("))
        assertFalse(title.contains("relativeTemporalSession?.cancel()"))
        assertFalse(title.contains("setSelectedSchedule("))
        assertTrue(title.contains("askToSaveChanges()"))
        assertTrue(savePrompt.contains("session.currentProposal"))
        assertTrue(savePrompt.contains("taskTitle = etTaskTitle.text.toString().trim()"))
    }

    @Test
    fun semanticDeleteStillUsesExistingConfirmationWithoutDirectDelete() {
        val handler = edit.substringAfter("private fun handleEditTaskSemanticMove(")
            .substringBefore("private fun applySemanticTitleChange(")
        val deleteRequest = edit.substringAfter("private fun requestVoiceDeleteConfirmation()")
            .substringBefore("private fun handlePendingDeleteConfirmation(")

        assertTrue(handler.contains("EditTaskSemanticMove.DELETE -> requestVoiceDeleteConfirmation()"))
        assertTrue(deleteRequest.contains("waitingForDeleteConfirmation = true"))
        assertTrue(deleteRequest.contains("expectConfirmation()"))
        assertFalse(deleteRequest.contains("performConfirmedDelete("))
    }

    @Test
    fun invalidSemanticTemporalValueCannotBypassAndroidTemporalValidation() {
        val semanticTemporal = edit.substringAfter("private fun applySemanticTemporalChange(")
            .substringBefore("private fun requestSemanticFieldChange(")
        val androidTemporal = edit.substringAfter("private fun applyProposedTemporalChange(")
            .substringBefore("private fun applyTemporalResolution(")

        val validation = semanticTemporal.indexOf("applyProposedTemporalChange(")
        val rejected = semanticTemporal.indexOf("if (!changed)")
        val savePrompt = semanticTemporal.indexOf("askToSaveChanges()")
        assertTrue(validation >= 0)
        assertTrue(rejected > validation)
        assertTrue(savePrompt > rejected)
        assertTrue(androidTemporal.contains("temporalResolver.resolve("))
        assertTrue(androidTemporal.contains("TemporalActionPolicy.evaluate("))
        assertTrue(androidTemporal.contains("TemporalPolicyResult.Unresolved"))
        assertTrue(androidTemporal.contains("TemporalPolicyResult.InvalidPastSchedule"))
        assertTrue(androidTemporal.contains("return false"))
    }

    @Test
    fun semanticCancellationCannotSaveOrDelete() {
        val cancel = edit.substringAfter("private fun cancelSemanticEditInteraction()")
            .substringBefore("private fun recoverFromUnknownEditSemanticMove()")

        assertTrue(cancel.contains("relativeTemporalSession?.cancel()"))
        assertTrue(cancel.contains("clearPendingEditCollection()"))
        assertFalse(cancel.contains("saveTask("))
        assertFalse(cancel.contains("performConfirmedDelete("))
        assertFalse(cancel.contains("AppDatabase"))
    }

    @Test
    fun pendingFieldNoLongerStoresTheWholeNormalizedInstruction() {
        assertFalse(edit.contains("etTaskTitle.setText(normalized)"))
        assertFalse(edit.contains("handlePendingFieldValue(normalized)"))
        assertTrue(edit.contains("EditTaskSemanticMove.CHANGE_TITLE -> applySemanticTitleChange"))
        assertTrue(edit.contains("EditTaskSemanticMove.REQUEST_DATE_CHANGE"))
        assertTrue(orchestrator.contains("title = decision.title.trim()"))
    }

    @Test
    fun asyncSemanticResponseIsBoundToLifecycleAndCapturedDraftState() {
        val request = edit.substringAfter("private fun requestEditTaskSemanticResolution(")
            .substringBefore("private fun captureEditTaskAgentContext(")
        val current = edit.substringAfter("private fun isEditSemanticRequestCurrent(")
            .substringBefore("private fun logEditSemanticResolution(")
        val stop = edit.substringAfter("override fun onStop()")
            .substringBefore("override fun onDestroy()")

        assertTrue(request.contains("val capturedAuthority = captureEditSemanticAuthority()"))
        assertTrue(request.contains("isEditSemanticRequestCurrent("))
        assertTrue(request.contains("STALE_RESPONSE_DISCARDED"))
        assertTrue(current.contains("capturedAuthority == currentAuthority"))
        assertTrue(current.contains("EditTaskRelativeProposalRoutingPolicy.isCurrentProposal("))
        assertTrue(current.contains("canRunEditAssistantCallback()"))
        assertTrue(current.contains("!isEditSaveInFlight"))
        assertTrue(current.contains("!isEditDeleteInFlight"))
        assertTrue(edit.contains("draftRevision = editDraftRevision"))
        assertTrue(edit.contains("private fun markEditDraftChanged()"))
        assertTrue(edit.contains("etTaskTitle.addTextChangedListener"))
        assertTrue(
            edit.substringAfter("private fun setExactDate(")
                .substringBefore("private fun acceptExactMinute(")
                .contains("markEditDraftChanged()")
        )
        assertTrue(
            edit.substringAfter("private fun setExactMinute(")
                .substringBefore("private fun setSelectedSchedule(")
                .contains("markEditDraftChanged()")
        )
        assertTrue(stop.contains("invalidateEditSemanticResolution()"))
    }

    @Test
    fun semanticPackageHasNoDatabaseOrExecutionPipeline() {
        val source = semanticRoot.listFiles()
            .orEmpty()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        listOf(
            "AppDatabase.getInstance",
            "taskDao()",
            "ReminderHelper",
            "AgentOrchestrator",
            "LaptopAgentClient",
            "TaskMatcher",
            "startActivity("
        ).forEach { forbidden -> assertFalse(forbidden, source.contains(forbidden)) }
    }

    @Test
    fun developerTransportRemainsTheExistingAssistantVoiceSessionBoundary() {
        assertTrue(edit.contains("interactionMode = DeveloperTestSession.interactionMode()"))
        assertTrue(edit.contains("transcriptObserver = DeveloperTestSession::recordTranscript"))
        assertTrue(edit.contains("assistantSession.submitTypedText(text, clearConversation = false)"))
        assertFalse(edit.contains("DeveloperTestSession.submit"))
    }
}
