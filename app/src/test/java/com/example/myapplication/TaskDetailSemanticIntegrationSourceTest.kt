package com.example.myapplication

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDetailSemanticIntegrationSourceTest {
    private val main = File("src/main/java/com/example/myapplication")
    private val activity = main.resolve("TaskDetailActivity.kt").readText()
    private val client = main.resolve("ai/conversation/ConversationAgentClient.kt").readText()
    private val schema = main.resolve("ai/schema/AgentResponseSchemas.kt").readText()
    private val orchestrator = main.resolve(
        "ai/conversation/taskdetailedit/TaskDetailEditSemanticOrchestrator.kt"
    ).readText()

    @Test
    fun everySubstantiveFieldResponseUsesConversationAgentAndProcessingState() {
        val fieldFlow = activity.substringAfter("private fun handleFieldResponse")
            .substringBefore("private fun applySemanticFieldResolution")
        assertTrue(activity.contains("TaskDetailEditSemanticOrchestrator("))
        assertTrue(activity.contains("semanticClient = ConversationAgentClient(this)"))
        assertTrue(fieldFlow.contains("taskDetailEditSemanticOrchestrator.resolve("))
        assertTrue(fieldFlow.contains("fieldResolver.resolveTitle(text)"))
        assertTrue(fieldFlow.contains("fieldResolver.resolveDate(text"))
        assertTrue(fieldFlow.contains("fieldResolver.resolveTime(text"))
        assertTrue(fieldFlow.contains("assistantSession.getBottomSheet()?.setProcessingState()"))
    }

    @Test
    fun safetyConfirmationsRemainDeterministicAndManualEditorsRemainModelFree() {
        val dispatch = activity.substringAfter("override fun onAssistantFinalText(text: String)")
            .substringBefore("private fun handleFieldResponse")
        val confirmation = activity.substringAfter("private fun handleConfirmationResponse")
            .substringBefore("private fun handleConfirmationYes")
        val manual = activity.substringAfter("private fun showManualTitleEditor")
            .substringBefore("private fun renderCurrentDraft")
        assertTrue(confirmation.contains("TaskDetailConfirmationInterpreter.interpret(text)"))
        assertFalse(confirmation.contains("taskDetailEditSemanticOrchestrator"))
        assertTrue(dispatch.contains("WAITING_FOR_SAVE_CONFIRMATION ->"))
        assertTrue(dispatch.contains("handleSaveConfirmationResponse(text)"))
        listOf(
            "WAITING_FOR_HOME_CONFIRMATION",
            "WAITING_FOR_BACK_CONFIRMATION",
            "WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION",
            "WAITING_FOR_DELETE_DISCARD_CONFIRMATION"
        ).forEach { state -> assertTrue(dispatch.contains(state)) }
        assertTrue(dispatch.contains("handleConfirmationResponse(text)"))
        assertTrue(manual.contains("DatePickerDialog("))
        assertTrue(manual.contains("TimePickerDialog("))
        assertFalse(manual.contains("ConversationAgentClient"))
        assertFalse(manual.contains("taskDetailEditSemanticOrchestrator"))
    }

    @Test
    fun clientUsesDedicatedStrictLowTemperatureBoundedRequestAndDiagnostics() {
        assertTrue(client.contains("RequestKind.TASK_DETAIL_EDIT_MOVE"))
        assertTrue(client.contains("TASK_DETAIL_EDIT_TEMPERATURE = 0.0"))
        assertTrue(client.contains("TASK_DETAIL_EDIT_MAX_TOKENS = 144"))
        assertTrue(client.contains("AgentResponseSchemas.taskDetailEditMoveResponseFormat()"))
        assertTrue(client.contains("TASK_DETAIL_EDIT_AGENT_SCHEMA"))
        assertTrue(client.contains("TASK_DETAIL_EDIT_AGENT_HTTP"))
        assertTrue(schema.contains("name = \"task_detail_edit_move\""))
        val boundedLog = orchestrator.substringAfter("private fun boundedLog(")
            .substringBefore("private fun moveFor")
        listOf(
            "target=", "state=", "move=", "source=", "confidence=", "agentAttempted=",
            "reason=", "draftRevision=", "generation="
        ).forEach { assertTrue(boundedLog.contains(it)) }
        assertFalse(boundedLog.contains("userText"))
        assertFalse(boundedLog.contains("currentDueDate"))
        assertFalse(boundedLog.contains("currentDueTime"))
        assertFalse(boundedLog.contains("title"))
    }

    @Test
    fun staleGuardsAreCheckedBeforeAnySemanticProposalCanMutateDraft() {
        val fieldFlow = activity.substringAfter("private fun handleFieldResponse")
            .substringBefore("private fun applySemanticFieldResolution")
        assertTrue(fieldFlow.indexOf("guard.isCurrent") < fieldFlow.indexOf("applySemanticFieldResolution(resolution)"))
        assertTrue(fieldFlow.contains("activityStopped || isFinishing || isDestroyed"))
        assertTrue(activity.contains("editInteractionGeneration += 1L"))
    }

    @Test
    fun saveConfirmationUsesSemanticGuardBeforeApplyingAnyConversationalAct() {
        val flow = activity.substringAfter("private fun handleSaveConfirmationResponse")
            .substringBefore("private fun applySaveConfirmationSemanticResolution")

        assertTrue(flow.contains("TaskDetailEditRequestGuard("))
        assertTrue(flow.contains("controller.draft.revision != interactionRevision"))
        assertTrue(flow.contains("!controller.isCurrent(claim)"))
        assertTrue(flow.contains("requestedField = null"))
        assertTrue(flow.contains("TaskDetailEditAgentContext.saveConfirmationMoves()"))
        assertTrue(flow.contains("taskDetailEditSemanticOrchestrator.resolveImmediate(text, context)"))
        assertTrue(flow.contains("taskDetailEditSemanticOrchestrator.resolve("))
        assertTrue(flow.indexOf("guard.isCurrent") <
            flow.indexOf("applySaveConfirmationSemanticResolution(resolution)"))
        assertTrue(flow.contains("activityStopped || isFinishing || isDestroyed"))
        assertFalse(flow.contains("TaskDetailConfirmationInterpreter"))
        assertFalse(flow.contains("dao."))
        assertFalse(flow.contains("ReminderHelper"))
    }

    @Test
    fun saveReadIsObservationalAndKeepsTheExistingConfirmationAuthority() {
        val apply = activity.substringAfter("private fun applySaveConfirmationSemanticResolution")
            .substringBefore("private fun applySaveConfirmationCorrection")
        val read = apply.substringAfter("is TaskDetailEditProposal.ReadDraft -> {")
            .substringBefore("is TaskDetailEditProposal.Title ->")

        assertTrue(read.contains("assistantSession.expectConfirmation()"))
        assertTrue(read.contains("TaskDetailEditSpeechRenderer.readDraftAndConfirm("))
        assertTrue(read.contains("draft = controller.draft"))
        assertFalse(read.contains("freezeSaveClaim()"))
        assertFalse(read.contains("changeTitle("))
        assertFalse(read.contains("changeSchedule("))
        assertFalse(read.contains("renderCurrentDraft()"))
        assertFalse(read.contains("clearLocalInteraction()"))
        assertFalse(read.contains("performAuthoritativeSave("))
        assertFalse(read.contains("dao."))
        assertFalse(read.contains("ReminderHelper"))
    }

    @Test
    fun saveCorrectionUsesAndroidValidationThenRefreshesTheSaveClaim() {
        val apply = activity.substringAfter("private fun applySaveConfirmationSemanticResolution")
            .substringBefore("private fun interactionFor(")
        val fieldApply = activity.substringAfter("private fun applyValidatedFieldResult")
            .substringBefore("private fun requestedField")
        val refresh = activity.substringAfter("private fun refreshSaveConfirmation")
            .substringBefore("private fun repeatSaveConfirmation")

        assertTrue(apply.contains("is TaskDetailEditProposal.RequestField -> startFieldEdit("))
        assertTrue(apply.contains("resumeSaveConfirmation = true"))
        assertTrue(apply.contains("fieldResolver.validateProposedTitle"))
        assertTrue(apply.contains("fieldResolver.resolveScheduleProposal"))
        assertTrue(apply.contains("controller.changeTitle("))
        assertTrue(apply.contains("controller.changeSchedule("))
        assertTrue(apply.contains("renderCurrentDraft()"))
        assertTrue(apply.contains("refreshSaveConfirmation("))
        assertFalse(apply.contains("performAuthoritativeSave("))
        assertFalse(apply.contains("dao."))
        assertFalse(apply.contains("ReminderHelper"))
        assertTrue(fieldApply.contains("resumeSaveConfirmationAfterFieldEdit"))
        assertTrue(fieldApply.contains("refreshSaveConfirmation("))
        assertTrue(refresh.contains("controller.freezeSaveClaim()"))
        assertTrue(refresh.contains("beginConfirmation("))
        assertTrue(refresh.contains("WAITING_FOR_SAVE_CONFIRMATION"))
    }

    @Test
    fun pastSameDayEditsHaveNoTomorrowClarificationPath() {
        val editing = main.resolve("TaskDetailEditing.kt").readText()
        assertFalse(editing.contains("PastSameDayTime"))
        assertFalse(activity.contains("WAITING_FOR_PAST_TIME_CONFIRMATION"))
        assertFalse(activity.contains("beginPastTimeClarification"))
        assertFalse(orchestrator.contains("PastSameDayTime"))
        assertTrue(editing.contains("TemporalUseCase.UPDATE"))
    }

    @Test
    fun manualTaskDetailsStructureIsPreservedWhileColorsUseThemeTokens() {
        val layout = File("src/main/res/layout/activity_task_detail.xml").readText()
        listOf(
            "detailTitleSurface",
            "detailDateSurface",
            "detailTimeSurface",
            "detailSubtaskProgressSurface",
            "btnReadAll",
            "btnToggleDone",
            "btnSaveChanges",
            "btnDeleteTask"
        ).forEach { assertTrue(layout.contains("@+id/$it")) }
        assertTrue(layout.contains("?attr/appColorTextPrimaryDark"))
        assertTrue(layout.contains("?attr/appColorTextSecondaryDark"))
    }

    @Test
    fun semanticPatchIntroducesNoDatabaseOrMigrationAuthority() {
        val semanticRoot = main.resolve("ai/conversation/taskdetailedit")
        val semanticSource = semanticRoot.walkTopDown()
            .filter(File::isFile)
            .joinToString("\n") { it.readText() }
        assertFalse(semanticSource.contains("AppDatabase"))
        assertFalse(semanticSource.contains("TaskDao"))
        assertFalse(semanticSource.contains("ReminderHelper"))
        assertFalse(semanticSource.contains("Migration("))
    }
}
