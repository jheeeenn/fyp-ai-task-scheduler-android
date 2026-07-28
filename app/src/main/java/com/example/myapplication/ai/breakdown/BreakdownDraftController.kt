package com.example.myapplication.ai.breakdown

import com.example.myapplication.ai.temporal.PendingTemporalClarification
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.diagnostics.DebugDiagnosticLog

class BreakdownDraftController {
    var state: BreakdownDraftState = BreakdownDraftState.NONE
        private set

    var draft: PendingBreakdownDraft? = null
        private set

    private var generationCounter = 0L
    private var saveGenerationCounter = 0L
    private var activeSaveGeneration: Long? = null
    private var candidateIds: List<Long> = emptyList()
    private var scheduleConfirmationGranted = false

    fun beginDraft(
        parentTitle: String,
        proposedSubtasks: List<String>,
        originalRequest: String,
        dateText: String?,
        timeText: String?
    ): BreakdownDraftUpdate {
        check(state != BreakdownDraftState.SAVING) {
            "A confirmed task breakdown is already being saved"
        }
        val validation = BreakdownPlanValidator.validate(parentTitle, proposedSubtasks)
        if (validation is BreakdownPlanValidationResult.Rejected) {
            reset(invalidateGeneration = true)
            return BreakdownDraftUpdate.Rejected(validation.reason)
        }
        val titles = (validation as BreakdownPlanValidationResult.Accepted).titles
        generationCounter += 1
        candidateIds = emptyList()
        activeSaveGeneration = null
        scheduleConfirmationGranted = false
        state = BreakdownDraftState.RESOLVING_TARGET
        draft = PendingBreakdownDraft(
            mode = null,
            parentTaskId = null,
            parentTitle = parentTitle.trim(),
            proposedSubtasks = titles,
            dateText = dateText?.trim()?.takeIf(String::isNotEmpty),
            timeText = timeText?.trim()?.takeIf(String::isNotEmpty),
            originalRequest = originalRequest,
            revision = 1L,
            generation = generationCounter
        )
        logDraft()
        return BreakdownDraftUpdate.Resolving(requireNotNull(draft))
    }

    fun applyNewRoot(generation: Long): BreakdownDraftUpdate {
        val current = requireCurrent(generation, BreakdownDraftState.RESOLVING_TARGET)
            ?: return stale()
        draft = current.copy(
            mode = BreakdownDraftMode.NEW_ROOT,
            parentTaskId = null
        )
        candidateIds = emptyList()
        state = BreakdownDraftState.WAITING_FOR_CONFIRMATION
        logDraft()
        return BreakdownDraftUpdate.Review(requireNotNull(draft))
    }

    fun applyAmbiguousTargets(
        generation: Long,
        candidates: List<TaskEntity>
    ): BreakdownDraftUpdate {
        val current = requireCurrent(generation, BreakdownDraftState.RESOLVING_TARGET)
            ?: return stale()
        val eligible = candidates.filter { it.parentTaskId == null && !it.isDone }
        if (eligible.size < 2) return BreakdownDraftUpdate.ParentChanged
        candidateIds = eligible.map(TaskEntity::id)
        draft = current.copy(mode = null, parentTaskId = null)
        state = BreakdownDraftState.CHOOSING_TARGET
        logDraft()
        return BreakdownDraftUpdate.ChoosingTarget(
            choices = eligible.map(TaskEntity::title),
            draft = requireNotNull(draft)
        )
    }

    fun resolveTargetChoice(
        userText: String,
        activeRootTasks: List<TaskEntity>
    ): TaskEntity? {
        if (state != BreakdownDraftState.CHOOSING_TARGET) return null
        val candidates = activeRootTasks.filter {
            it.id in candidateIds && it.parentTaskId == null && !it.isDone
        }
        return BreakdownTargetResolver.selectCandidate(userText, candidates)
    }

    fun applyExistingRoot(
        generation: Long,
        parent: TaskEntity,
        hasSubtasks: Boolean
    ): BreakdownDraftUpdate {
        val current = draft
        if (current == null ||
            current.generation != generation ||
            state !in setOf(
                BreakdownDraftState.RESOLVING_TARGET,
                BreakdownDraftState.CHOOSING_TARGET
            )
        ) {
            return stale()
        }
        if (parent.parentTaskId != null || parent.isDone) {
            return BreakdownDraftUpdate.ParentChanged
        }
        if (hasSubtasks) {
            reset(invalidateGeneration = true)
            logSaveResult("ALREADY_HAS_SUBTASKS")
            return BreakdownDraftUpdate.AlreadyHasSubtasks
        }
        candidateIds = emptyList()
        scheduleConfirmationGranted = false
        draft = current.copy(
            mode = BreakdownDraftMode.EXISTING_ROOT,
            parentTaskId = parent.id,
            parentTitle = parent.title,
            dateText = parent.dueDate,
            timeText = parent.dueTime
        )
        state = BreakdownDraftState.WAITING_FOR_CONFIRMATION
        logDraft()
        return BreakdownDraftUpdate.Review(requireNotNull(draft))
    }

    fun applyRevision(
        expectedGeneration: Long,
        expectedRevision: Long,
        proposedSubtasks: List<String>
    ): BreakdownDraftUpdate {
        val current = draft
        if (state != BreakdownDraftState.WAITING_FOR_CONFIRMATION ||
            current == null ||
            current.generation != expectedGeneration ||
            current.revision != expectedRevision
        ) {
            return stale()
        }
        val validation = BreakdownPlanValidator.validate(
            current.parentTitle,
            proposedSubtasks
        )
        if (validation is BreakdownPlanValidationResult.Rejected) {
            return BreakdownDraftUpdate.Rejected(validation.reason)
        }
        val titles = (validation as BreakdownPlanValidationResult.Accepted).titles
        scheduleConfirmationGranted = false
        draft = current.copy(
            proposedSubtasks = titles,
            revision = current.revision + 1
        )
        state = BreakdownDraftState.WAITING_FOR_CONFIRMATION
        logDraft()
        return BreakdownDraftUpdate.Review(requireNotNull(draft))
    }

    fun startScheduleCollection(
        clarification: PendingTemporalClarification
    ): Boolean {
        val current = draft ?: return false
        if (state != BreakdownDraftState.WAITING_FOR_CONFIRMATION ||
            current.mode != BreakdownDraftMode.NEW_ROOT
        ) {
            return false
        }
        scheduleConfirmationGranted = true
        draft = current.copy(temporalClarification = clarification)
        state = BreakdownDraftState.COLLECTING_SCHEDULE
        logDraft()
        return true
    }

    fun updateTemporalClarification(
        clarification: PendingTemporalClarification?
    ): Boolean {
        val current = draft ?: return false
        if (state != BreakdownDraftState.COLLECTING_SCHEDULE) return false
        draft = current.copy(temporalClarification = clarification)
        logDraft()
        return true
    }

    fun updateProposedSchedule(dateText: String?, timeText: String?): Boolean {
        val current = draft ?: return false
        if (state != BreakdownDraftState.COLLECTING_SCHEDULE) return false
        draft = current.copy(
            dateText = dateText ?: current.dateText,
            timeText = timeText ?: current.timeText
        )
        logDraft()
        return true
    }

    fun markSaving(
        exactDate: String? = null,
        exactTime: String? = null
    ): PendingBreakdownSave? {
        val current = draft ?: return null
        val canCommit = when (state) {
            BreakdownDraftState.WAITING_FOR_CONFIRMATION -> true
            BreakdownDraftState.COLLECTING_SCHEDULE -> scheduleConfirmationGranted
            else -> false
        }
        if (!canCommit || current.mode == null) return null

        val committed = current.copy(
            dateText = exactDate ?: current.dateText,
            timeText = exactTime ?: current.timeText,
            temporalClarification = null
        )
        if (committed.mode == BreakdownDraftMode.NEW_ROOT &&
            (committed.dateText.isNullOrBlank() || committed.timeText.isNullOrBlank())
        ) {
            return null
        }
        saveGenerationCounter += 1
        activeSaveGeneration = saveGenerationCounter
        draft = committed
        state = BreakdownDraftState.SAVING
        logDraft()
        return PendingBreakdownSave(saveGenerationCounter, committed)
    }

    fun completeSaving(saveGeneration: Long): Boolean {
        if (state != BreakdownDraftState.SAVING ||
            activeSaveGeneration != saveGeneration
        ) {
            logSaveResult("STALE")
            return false
        }
        reset(invalidateGeneration = true)
        return true
    }

    fun clear(): Boolean {
        if (state == BreakdownDraftState.SAVING) return false
        if (state != BreakdownDraftState.NONE) {
            logSaveResult("CANCELLED")
        }
        reset(invalidateGeneration = true)
        return true
    }

    fun discard(result: String): Boolean {
        if (state == BreakdownDraftState.SAVING) return false
        logSaveResult(result)
        reset(invalidateGeneration = true)
        return true
    }

    fun isCurrent(generation: Long, revision: Long): Boolean {
        val current = draft
        return current != null &&
            current.generation == generation &&
            current.revision == revision
    }

    private fun requireCurrent(
        generation: Long,
        requiredState: BreakdownDraftState
    ): PendingBreakdownDraft? =
        draft?.takeIf { it.generation == generation && state == requiredState }

    private fun stale(): BreakdownDraftUpdate.Stale {
        logSaveResult("STALE")
        return BreakdownDraftUpdate.Stale
    }

    private fun reset(invalidateGeneration: Boolean) {
        if (invalidateGeneration) generationCounter += 1
        state = BreakdownDraftState.NONE
        draft = null
        candidateIds = emptyList()
        activeSaveGeneration = null
        scheduleConfirmationGranted = false
        logDraft()
    }

    private fun logDraft() {
        DebugDiagnosticLog.event(
            "BREAKDOWN_DRAFT",
            "state=${state.name}\n" +
                "mode=${draft?.mode?.name.orEmpty()}\n" +
                "revision=${draft?.revision ?: 0}\n" +
                "proposedSubtaskCount=${draft?.proposedSubtasks?.size ?: 0}"
        )
    }

    private fun logSaveResult(result: String) {
        DebugDiagnosticLog.event("BREAKDOWN_SAVE_RESULT", "result=$result")
    }
}
