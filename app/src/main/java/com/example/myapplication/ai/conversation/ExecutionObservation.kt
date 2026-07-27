package com.example.myapplication.ai.conversation

import org.json.JSONArray
import org.json.JSONObject

enum class ExecutionOperation { CREATE_TASK, QUERY_TASK, DAILY_BRIEFING, UPDATE_TASK, RESCHEDULE_TASK, DELETE_TASK, MARK_DONE, MARK_UNDONE, BREAKDOWN_TASK, NAVIGATION, SYSTEM }
enum class ExecutionOutcome { SUCCESS, PARTIAL_SUCCESS, INFORMATION, NO_RESULTS, NOT_FOUND, AMBIGUOUS, NEEDS_CONFIRMATION, NEEDS_CLARIFICATION, CANCELLED, REJECTED, FAILURE }
enum class RequiredInput { NONE, CONFIRMATION, TASK_CHOICE, EXACT_DATE, EXACT_TIME, TITLE, CHANGE_FIELD, RETRY }
enum class AllowedUserMove { CONFIRM, REJECT, SELECT_OPTION, PROVIDE_DATE, PROVIDE_TIME, PROVIDE_TITLE, CHANGE_FIELD, CANCEL, REQUEST_HELP, RETRY, END_SESSION }
enum class TaskQueryPresentationLevel { COUNT_ONLY, OVERVIEW, DETAILS }
enum class TaskQuerySpeechDetail { BRIEF, BALANCED, DETAILED }
enum class TaskQuerySpeechTone { FRIENDLY, NEUTRAL, PROFESSIONAL }

data class TaskQueryPageObservation(
    val totalTaskCount: Int,
    val pageStartPosition: Int,
    val pageEndPosition: Int,
    val pageNumber: Int,
    val pageCount: Int,
    val pageSize: Int,
    val hasNextPage: Boolean,
    val presentation: TaskQueryPresentationLevel,
    val detailLevel: TaskQuerySpeechDetail,
    val tone: TaskQuerySpeechTone,
    val includeTaskDates: Boolean,
    val temporalLabel: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("total_task_count", totalTaskCount)
        put("page_start_position", pageStartPosition)
        put("page_end_position", pageEndPosition)
        put("page_number", pageNumber)
        put("page_count", pageCount)
        put("page_size", pageSize)
        put("has_next_page", hasNextPage)
        put("presentation", presentation.name)
        put("detail_level", detailLevel.name)
        put("tone", tone.name)
        put("include_task_dates", includeTaskDates)
        put("temporal_label", temporalLabel)
    }
}

data class ObservedTask(
    val title: String,
    val dueDate: String = "",
    val dueTime: String = "",
    val isDone: Boolean = false,
    val subtaskCount: Int = 0,
    val unfinishedSubtaskCount: Int = 0,
    val unfinishedSubtaskTitles: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("due_date", dueDate)
        put("due_time", dueTime)
        put("is_done", isDone)
        put("subtask_count", subtaskCount)
        put("unfinished_subtask_count", unfinishedSubtaskCount)
        put("unfinished_subtask_titles", JSONArray().apply { unfinishedSubtaskTitles.forEach { put(it) } })
    }
}

data class ExecutionObservation(
    val operation: ExecutionOperation,
    val outcome: ExecutionOutcome,
    val taskTitle: String = "",
    val taskCount: Int = 0,
    val overdueTaskCount: Int = 0,
    val todayActiveTaskCount: Int = 0,
    val additionalTodayTaskCount: Int = 0,
    val dateText: String = "",
    val timeText: String = "",
    val detail: String = "",
    val facts: List<String> = emptyList(),
    val tasks: List<ObservedTask> = emptyList(),
    val queryPage: TaskQueryPageObservation? = null,
    val choices: List<String> = emptyList(),
    val planItems: List<String> = emptyList(),
    val requiredInput: RequiredInput = RequiredInput.NONE,
    val allowedUserMoves: List<AllowedUserMove> = emptyList(),
    val listenAgain: Boolean,
    val fallbackSpeech: String,
    val fallbackHint: String = ""
) {
    fun toAgentJson(): String = JSONObject().apply {
        put("operation", operation.name)
        put("outcome", outcome.name)
        put("expected_response_type", outcome.toConversationResponseType().name)
        put("task_title", taskTitle)
        put("task_count", taskCount)
        if (operation == ExecutionOperation.DAILY_BRIEFING) {
            put("overdue_task_count", overdueTaskCount)
            put("today_active_task_count", todayActiveTaskCount)
            put("additional_today_task_count", additionalTodayTaskCount)
        }
        put("date_text", dateText)
        put("time_text", timeText)
        put("detail", detail)
        put("facts", JSONArray().apply { facts.forEach { put(it) } })
        put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
        queryPage?.let { put("query_page", it.toJson()) }
        put("choices", JSONArray().apply { choices.forEach { put(it) } })
        put("plan_items", JSONArray().apply { planItems.forEach { put(it) } })
        put("required_input", requiredInput.name)
        put("allowed_user_moves", JSONArray().apply { allowedUserMoves.forEach { put(it.name) } })
        put("continued_interaction_expected", listenAgain)
    }.toString()
}

fun ExecutionOutcome.toConversationResponseType(): ConversationResponseType = when (this) {
    ExecutionOutcome.SUCCESS -> ConversationResponseType.SUCCESS
    ExecutionOutcome.PARTIAL_SUCCESS -> ConversationResponseType.PARTIAL_SUCCESS
    ExecutionOutcome.INFORMATION, ExecutionOutcome.NO_RESULTS, ExecutionOutcome.NOT_FOUND -> ConversationResponseType.INFORMATION
    ExecutionOutcome.NEEDS_CONFIRMATION -> ConversationResponseType.REQUEST_CONFIRMATION
    ExecutionOutcome.NEEDS_CLARIFICATION, ExecutionOutcome.AMBIGUOUS -> ConversationResponseType.REQUEST_CLARIFICATION
    ExecutionOutcome.CANCELLED -> ConversationResponseType.ACKNOWLEDGEMENT
    ExecutionOutcome.REJECTED, ExecutionOutcome.FAILURE -> ConversationResponseType.ERROR
}
