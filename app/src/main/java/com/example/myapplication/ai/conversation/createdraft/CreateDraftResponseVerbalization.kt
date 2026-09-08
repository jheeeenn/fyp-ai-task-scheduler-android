package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.TaskFormScheduleValueRenderer
import com.example.myapplication.ai.conversation.AllowedUserMove
import com.example.myapplication.ai.conversation.ConversationResponse
import com.example.myapplication.ai.conversation.ConversationResponseType
import com.example.myapplication.ai.conversation.ExecutionOperation
import com.example.myapplication.ai.conversation.ExecutionOutcome
import com.example.myapplication.ai.conversation.RequiredInput
import com.example.myapplication.ai.conversation.ResponseAct
import com.example.myapplication.ai.conversation.ResponseMeaningDetail
import com.example.myapplication.ai.conversation.ResponseVerbalizationContract
import com.example.myapplication.ai.conversation.ResponseVerbalizationPlan
import com.example.myapplication.ai.conversation.ResponseVerbalizationTone
import com.example.myapplication.ai.conversation.ResponseVerbalizationVerbosity
import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftReadTarget
import com.example.myapplication.voice.CreateTaskDialogState

/** Builds presentation-only plans from Android-owned Create Task draft state. */
object CreateDraftResponseVerbalization {
    fun readOrNull(
        target: CreateDraftReadTarget,
        title: String?,
        date: String?,
        time: String?,
        state: CreateTaskDialogState,
        fallbackSpeech: String,
        tone: ResponseVerbalizationTone,
        verbosity: ResponseVerbalizationVerbosity
    ): ResponseVerbalizationPlan? {
        val allFacts = protectedFacts(title, date, time)
        val required = when (target) {
            CreateDraftReadTarget.TITLE -> setOf(ResponseVerbalizationPlan.TASK_TITLE)
            CreateDraftReadTarget.DATE -> setOf(ResponseVerbalizationPlan.DATE_TEXT)
            CreateDraftReadTarget.TIME -> setOf(ResponseVerbalizationPlan.TIME_TEXT)
            CreateDraftReadTarget.SCHEDULE -> setOf(
                ResponseVerbalizationPlan.DATE_TEXT,
                ResponseVerbalizationPlan.TIME_TEXT
            )
            CreateDraftReadTarget.SUMMARY -> CREATE_DRAFT_FACTS
        }
        if (!allFacts.keys.containsAll(required)) return null
        val interaction = readInteraction(state)
        return plan(
            outcome = interaction.outcome,
            responseType = interaction.responseType,
            responseAct = interaction.responseAct,
            meaningDetail = when (target) {
                CreateDraftReadTarget.TITLE -> ResponseMeaningDetail.CREATE_DRAFT_READ_TITLE
                CreateDraftReadTarget.DATE -> ResponseMeaningDetail.CREATE_DRAFT_READ_DATE
                CreateDraftReadTarget.TIME -> ResponseMeaningDetail.CREATE_DRAFT_READ_TIME
                CreateDraftReadTarget.SCHEDULE -> ResponseMeaningDetail.CREATE_DRAFT_READ_SCHEDULE
                CreateDraftReadTarget.SUMMARY -> ResponseMeaningDetail.CREATE_DRAFT_READ_SUMMARY
            },
            requiredInput = interaction.requiredInput,
            allowedUserMoves = interaction.allowedUserMoves,
            continuedInteractionExpected = interaction.continuedInteractionExpected,
            protectedValues = allFacts.filterKeys { it in required },
            requiredPlaceholders = required,
            optionalPlaceholders = emptySet(),
            fallbackSpeech = fallbackSpeech,
            tone = tone,
            verbosity = verbosity
        )
    }

    fun updateConfirmationOrNull(
        updatedField: CreateDraftField?,
        title: String?,
        date: String?,
        time: String?,
        fallbackSpeech: String,
        tone: ResponseVerbalizationTone,
        verbosity: ResponseVerbalizationVerbosity
    ): ResponseVerbalizationPlan? {
        val facts = completeProtectedFactsOrNull(title, date, time) ?: return null
        return confirmationPlan(
            meaningDetail = when (updatedField) {
                CreateDraftField.TITLE -> ResponseMeaningDetail.CREATE_DRAFT_UPDATE_TITLE
                CreateDraftField.DATE -> ResponseMeaningDetail.CREATE_DRAFT_UPDATE_DATE
                CreateDraftField.TIME -> ResponseMeaningDetail.CREATE_DRAFT_UPDATE_TIME
                null -> ResponseMeaningDetail.CREATE_DRAFT_UPDATE_SCHEDULE
            },
            facts = facts,
            fallbackSpeech = fallbackSpeech,
            tone = tone,
            verbosity = verbosity
        )
    }

    fun saveConfirmationOrNull(
        title: String?,
        date: String?,
        time: String?,
        fallbackSpeech: String,
        tone: ResponseVerbalizationTone,
        verbosity: ResponseVerbalizationVerbosity
    ): ResponseVerbalizationPlan? {
        val facts = completeProtectedFactsOrNull(title, date, time) ?: return null
        return confirmationPlan(
            meaningDetail = ResponseMeaningDetail.CREATE_DRAFT_SAVE_CONFIRMATION,
            facts = facts,
            fallbackSpeech = fallbackSpeech,
            tone = tone,
            verbosity = verbosity
        )
    }

    fun saveResult(
        title: String,
        date: String,
        time: String,
        reminderScheduled: Boolean,
        fallbackSpeech: String,
        tone: ResponseVerbalizationTone,
        verbosity: ResponseVerbalizationVerbosity
    ): ResponseVerbalizationPlan {
        val facts = requireNotNull(completeProtectedFactsOrNull(title, date, time))
        val outcome = if (reminderScheduled) {
            ExecutionOutcome.SUCCESS
        } else {
            ExecutionOutcome.PARTIAL_SUCCESS
        }
        return plan(
            outcome = outcome,
            responseType = if (reminderScheduled) {
                ConversationResponseType.SUCCESS
            } else {
                ConversationResponseType.PARTIAL_SUCCESS
            },
            responseAct = ResponseAct.REPORT_RESULT,
            meaningDetail = if (reminderScheduled) {
                ResponseMeaningDetail.CREATE_DRAFT_SAVE_SUCCESS
            } else {
                ResponseMeaningDetail.CREATE_DRAFT_SAVE_PARTIAL_REMINDER_FAILURE
            },
            requiredInput = RequiredInput.NONE,
            allowedUserMoves = emptyList(),
            continuedInteractionExpected = false,
            protectedValues = facts,
            requiredPlaceholders = emptySet(),
            optionalPlaceholders = facts.keys,
            fallbackSpeech = fallbackSpeech,
            tone = tone,
            verbosity = verbosity
        )
    }

    private fun confirmationPlan(
        meaningDetail: ResponseMeaningDetail,
        facts: Map<String, String>,
        fallbackSpeech: String,
        tone: ResponseVerbalizationTone,
        verbosity: ResponseVerbalizationVerbosity
    ) = plan(
        outcome = ExecutionOutcome.NEEDS_CONFIRMATION,
        responseType = ConversationResponseType.REQUEST_CONFIRMATION,
        responseAct = ResponseAct.ASK_CONFIRMATION,
        meaningDetail = meaningDetail,
        requiredInput = RequiredInput.CONFIRMATION,
        allowedUserMoves = listOf(
            AllowedUserMove.CONFIRM,
            AllowedUserMove.REJECT,
            AllowedUserMove.CHANGE_FIELD,
            AllowedUserMove.CANCEL,
            AllowedUserMove.REQUEST_HELP
        ),
        continuedInteractionExpected = true,
        protectedValues = facts,
        requiredPlaceholders = facts.keys,
        optionalPlaceholders = emptySet(),
        fallbackSpeech = fallbackSpeech,
        tone = tone,
        verbosity = verbosity
    )

    private fun plan(
        outcome: ExecutionOutcome,
        responseType: ConversationResponseType,
        responseAct: ResponseAct,
        meaningDetail: ResponseMeaningDetail,
        requiredInput: RequiredInput,
        allowedUserMoves: List<AllowedUserMove>,
        continuedInteractionExpected: Boolean,
        protectedValues: Map<String, String>,
        requiredPlaceholders: Set<String>,
        optionalPlaceholders: Set<String>,
        fallbackSpeech: String,
        tone: ResponseVerbalizationTone,
        verbosity: ResponseVerbalizationVerbosity
    ) = ResponseVerbalizationPlan(
        operation = ExecutionOperation.CREATE_TASK,
        outcome = outcome,
        responseType = responseType,
        tone = tone,
        verbosity = verbosity,
        responseAct = responseAct,
        contract = ResponseVerbalizationContract.CREATE_DRAFT_PRESENTATION,
        requiredInput = requiredInput,
        allowedUserMoves = allowedUserMoves,
        continuedInteractionExpected = continuedInteractionExpected,
        protectedValues = protectedValues,
        requiredPlaceholders = requiredPlaceholders,
        optionalPlaceholders = optionalPlaceholders,
        deterministicResponse = ConversationResponse(
            speech = fallbackSpeech,
            hint = "",
            responseType = responseType,
            source = "android_deterministic"
        ),
        meaningDetail = meaningDetail
    )

    private fun protectedFacts(
        title: String?,
        date: String?,
        time: String?
    ): Map<String, String> = buildMap {
        title?.trim()?.takeIf(String::isNotEmpty)?.let {
            put(ResponseVerbalizationPlan.TASK_TITLE, it)
        }
        date?.trim()?.takeIf(String::isNotEmpty)?.let {
            put(ResponseVerbalizationPlan.DATE_TEXT, TaskFormScheduleValueRenderer.date(it))
        }
        time?.trim()?.takeIf(String::isNotEmpty)?.let {
            put(ResponseVerbalizationPlan.TIME_TEXT, TaskFormScheduleValueRenderer.time(it))
        }
    }

    private fun completeProtectedFactsOrNull(
        title: String?,
        date: String?,
        time: String?
    ): Map<String, String>? = protectedFacts(title, date, time)
        .takeIf { it.keys == CREATE_DRAFT_FACTS }

    private fun readInteraction(state: CreateTaskDialogState): ReadInteraction = when (state) {
        CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> ReadInteraction(
            outcome = ExecutionOutcome.NEEDS_CONFIRMATION,
            responseType = ConversationResponseType.REQUEST_CONFIRMATION,
            responseAct = ResponseAct.ASK_CONFIRMATION,
            requiredInput = RequiredInput.CONFIRMATION,
            allowedUserMoves = listOf(
                AllowedUserMove.CONFIRM,
                AllowedUserMove.REJECT,
                AllowedUserMove.CHANGE_FIELD,
                AllowedUserMove.CANCEL,
                AllowedUserMove.REQUEST_HELP
            ),
            continuedInteractionExpected = true
        )
        CreateTaskDialogState.READY_TO_SAVE -> ReadInteraction(
            outcome = ExecutionOutcome.INFORMATION,
            responseType = ConversationResponseType.INFORMATION,
            responseAct = ResponseAct.REPORT_INFORMATION,
            requiredInput = RequiredInput.NONE,
            allowedUserMoves = emptyList(),
            continuedInteractionExpected = false
        )
        else -> {
            val requiredInput = when (state) {
                CreateTaskDialogState.IDLE,
                CreateTaskDialogState.WAITING_FOR_TITLE -> RequiredInput.TITLE
                CreateTaskDialogState.WAITING_FOR_DATE -> RequiredInput.EXACT_DATE
                CreateTaskDialogState.WAITING_FOR_TIME -> RequiredInput.EXACT_TIME
                CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> RequiredInput.CHANGE_FIELD
                else -> error("Handled above")
            }
            ReadInteraction(
                outcome = ExecutionOutcome.NEEDS_CLARIFICATION,
                responseType = ConversationResponseType.REQUEST_CLARIFICATION,
                responseAct = ResponseAct.REPORT_AND_REQUEST_INPUT,
                requiredInput = requiredInput,
                allowedUserMoves = allowedMovesFor(requiredInput),
                continuedInteractionExpected = true
            )
        }
    }

    private fun allowedMovesFor(requiredInput: RequiredInput): List<AllowedUserMove> = buildList {
        add(
            when (requiredInput) {
                RequiredInput.TITLE -> AllowedUserMove.PROVIDE_TITLE
                RequiredInput.EXACT_DATE -> AllowedUserMove.PROVIDE_DATE
                RequiredInput.EXACT_TIME -> AllowedUserMove.PROVIDE_TIME
                RequiredInput.CHANGE_FIELD -> AllowedUserMove.CHANGE_FIELD
                else -> error("Unsupported Create Task read continuation")
            }
        )
        add(AllowedUserMove.CANCEL)
        add(AllowedUserMove.REQUEST_HELP)
    }

    private data class ReadInteraction(
        val outcome: ExecutionOutcome,
        val responseType: ConversationResponseType,
        val responseAct: ResponseAct,
        val requiredInput: RequiredInput,
        val allowedUserMoves: List<AllowedUserMove>,
        val continuedInteractionExpected: Boolean
    )

    private val CREATE_DRAFT_FACTS = setOf(
        ResponseVerbalizationPlan.TASK_TITLE,
        ResponseVerbalizationPlan.DATE_TEXT,
        ResponseVerbalizationPlan.TIME_TEXT
    )
}
