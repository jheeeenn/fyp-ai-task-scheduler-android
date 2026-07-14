package com.example.myapplication.ai.agent

import android.content.Context
import android.util.Log
import com.example.myapplication.SettingsActivity
import com.example.myapplication.ai.schema.AgentResponseSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TaskAgentResponseException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class LaptopAgentClient(
    context: Context? = null,
    private val endpointUrl: String = SettingsActivity.DEFAULT_TASK_AGENT_ENDPOINT,
    private val modelId: String = "google/gemma-4-e2b"
) {
    private val appContext = context?.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    suspend fun process(normalizedText: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", modelId)
            put("temperature", 0.0)
            put("max_tokens", 512)
            put("stream", false)
            put("response_format", AgentResponseSchemas.taskAgentResponseFormat())
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", normalizedText)
                })
            })
        }

        Log.d("TASK_AGENT_SCHEMA", "Structured TaskAgentResponse schema enabled")

        val requestEndpointUrl = getEndpointUrl()
        Log.d("LAPTOP_AGENT_CONFIG", "Using Task Agent endpoint: $requestEndpointUrl")

        val request = Request.Builder()
            .url(requestEndpointUrl)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d("LAPTOP_AGENT", "HTTP ${response.code}: $body")

                if (!response.isSuccessful) {
                    throw IOException("LM Studio HTTP ${response.code}: $body")
                }

                val root = JSONObject(body)
                val choices = root.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    throw IOException("LM Studio response missing choices[0].message.content")
                }

                val choice = choices.getJSONObject(0)
                val message = choice.getJSONObject("message")
                val content = message.optString("content", "")
                val reasoningChars = message.optString("reasoning_content", "").length
                val finishReason = choice.optString("finish_reason", "")

                if (content.isBlank()) {
                    val lengthMessage = if (finishReason == "length") {
                        " Task Agent exhausted its output token budget before producing structured content."
                    } else {
                        ""
                    }
                    val errorMessage = "Task Agent returned blank content. " +
                            "finishReason=$finishReason, reasoningChars=$reasoningChars.$lengthMessage"
                    Log.e("LAPTOP_AGENT", errorMessage)
                    throw TaskAgentResponseException(errorMessage)
                }

                content
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("LAPTOP_AGENT", "LM Studio request timed out", e)
            throw IOException("LM Studio request timed out", e)
        } catch (e: java.io.InterruptedIOException) {
            Log.e("LAPTOP_AGENT", "LM Studio request timed out or was interrupted", e)
            throw IOException("LM Studio request timed out or was interrupted", e)
        }
    }

    private fun getEndpointUrl(): String {
        val prefs = appContext
            ?.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

        val configuredEndpoint = if (prefs?.contains(SettingsActivity.KEY_TASK_AGENT_ENDPOINT) == true) {
            prefs.getString(SettingsActivity.KEY_TASK_AGENT_ENDPOINT, null)
        } else {
            null
        }
        if (!configuredEndpoint.isNullOrBlank()) return configuredEndpoint

        val legacyEndpoint = prefs?.getString(SettingsActivity.KEY_LM_STUDIO_ENDPOINT, null)
        if (!legacyEndpoint.isNullOrBlank()) return legacyEndpoint

        return endpointUrl
    }

    companion object {
        private val SYSTEM_PROMPT = """
You are a strict JSON task-command parser for an Android task scheduling app.

Return only one valid compact JSON object. No markdown. No explanation.

The JSON object must contain these fields:
natural_response, action, task_title, target_task_title, date, time, recurrence, priority, confidence, need_clarification, missing_fields, requires_confirmation, plan.

Allowed actions:
CREATE_TASK, QUERY_TASK, RESCHEDULE_TASK, UPDATE_TASK, DELETE_TASK, MARK_DONE, MARK_UNDONE, BREAKDOWN_TASK, UNKNOWN.

Action rules:
Use CREATE_TASK for new tasks or reminders.
Use QUERY_TASK when the user asks what tasks they have.
Use RESCHEDULE_TASK when the user changes the date or time of an existing task.
Use UPDATE_TASK when the user edits an existing task.
Use DELETE_TASK when the user wants to remove an existing task.
Use MARK_DONE when the user says a task is finished or completed.
Use MARK_UNDONE when the user wants to reopen a completed task.
Use UNKNOWN only when the request is not about task scheduling.
Use BREAKDOWN_TASK only when the user asks to break down, split, divide, or plan a large task into smaller subtasks.

Field rules:
For CREATE_TASK, put the new task name in task_title and keep target_task_title empty.
For RESCHEDULE_TASK, UPDATE_TASK, DELETE_TASK, MARK_DONE, and MARK_UNDONE, put the existing task name in target_task_title and keep task_title empty.
For QUERY_TASK, keep task_title and target_task_title empty unless the user asks about one specific task.
Use empty string for unknown text fields.
Do not invent dates, times, recurrence, or priority.
For BREAKDOWN_TASK, put the large task name in task_title and keep target_task_title empty.

Date and time rules:
If the user gives a date phrase, copy it into the date field exactly as spoken where possible.
Examples of date phrases: today, tomorrow, day after tomorrow, this Monday, next Friday, 28 June, June 28, 28/06/2026.
Do not convert explicit dates to another format. Android will normalize the date.
If no date is given, date must be empty.
If the user gives a time, extract it.
Use 24-hour time when possible, such as "21:00".
For RESCHEDULE_TASK, date may be empty if the user only changes the time.
For QUERY_TASK, copy the complete date or date-range phrase into date and the complete time or time-range phrase into time.
The Task Agent does not need database access to classify or extract a QUERY_TASK request.
Never respond that you cannot access the user's task list.
When a user asks what tasks they have for any date, date range, or time range, return QUERY_TASK.
Android will access and filter the database after extraction.
UNKNOWN is only for genuinely non-task requests.
For QUERY_TASK, do not calculate real dates from relative phrases; Android performs calendar resolution and task filtering.
For QUERY_TASK, keep both date and time empty only when no temporal restriction was supplied.
For QUERY_TASK, never access, request, or filter the task database.
QUERY_TASK examples:
User: "What tasks do I have next week?" -> date="next week", time=""
User: "What tasks do I have next week in the morning?" -> date="next week", time="morning"
User: "What tasks do I have between 20 July and 25 July?" -> date="between 20 July and 25 July", time=""
User: "What tasks do I have tomorrow after 6 PM?" -> date="tomorrow", time="after 6 PM"
User: "What overdue tasks do I have?" -> date="overdue", time=""
User: "What tasks do I have this week?" -> action=QUERY_TASK, date="this week", time=""
User: "Do I have any task this month?" -> action=QUERY_TASK, date="this month", time=""
User: "What tasks do I have next month?" -> action=QUERY_TASK, date="next month", time=""
User: "What upcoming tasks do I have?" -> action=QUERY_TASK, date="upcoming", time=""

Recurrence and priority rules:
recurrence must be empty unless the user clearly says the task repeats.
Allowed recurrence values: "", DAILY, WEEKLY, MONTHLY, YEARLY.
priority must be empty unless the user clearly says low priority, medium priority, high priority, urgent, or important.
Allowed priority values: "", LOW, MEDIUM, HIGH.

Confidence rules:
For clear task commands, confidence should be high, usually 0.8 to 1.0.
Use confidence below 0.6 only when the request is unclear or not task-related.

Safety rules:
requires_confirmation must be true for DELETE_TASK.
requires_confirmation must be false for CREATE_TASK, QUERY_TASK, UPDATE_TASK, RESCHEDULE_TASK, MARK_DONE, and MARK_UNDONE.
missing_fields must be an empty array unless the app cannot safely continue without asking the user.
For RESCHEDULE_TASK, do not put "date" in missing_fields when the user only changes the time.

Plan rules:
For CREATE_TASK, QUERY_TASK, UPDATE_TASK, RESCHEDULE_TASK, DELETE_TASK, MARK_DONE, and MARK_UNDONE, plan must be an empty array.
For BREAKDOWN_TASK, plan must contain 2 to 4 short actionable subtask titles.
For BREAKDOWN_TASK, date, time, recurrence, and priority should usually be empty unless the user clearly provides them.
""".trimIndent()
    }
}
