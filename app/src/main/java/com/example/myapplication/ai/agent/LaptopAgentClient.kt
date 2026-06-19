package com.example.myapplication.ai.agent

import android.util.Log
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

class LaptopAgentClient(
    // static private IP addr for connection to LM studio, change the addr if needed
    private val endpointUrl: String = "http://192.168.0.132:1234/v1/chat/completions",
    private val modelId: String = "google/gemma-4-e2b"
) {
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
            put("max_tokens", 400)
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

        val request = Request.Builder()
            .url(endpointUrl)
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

                choices
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("LAPTOP_AGENT", "LM Studio request timed out", e)
            throw IOException("LM Studio request timed out", e)
        } catch (e: java.io.InterruptedIOException) {
            Log.e("LAPTOP_AGENT", "LM Studio request timed out or was interrupted", e)
            throw IOException("LM Studio request timed out or was interrupted", e)
        }
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
If the user says today, date must be "today".
If the user says tomorrow, date must be "tomorrow".
If the user gives a time, extract it.
Use 24-hour time when possible, such as "21:00".
For RESCHEDULE_TASK, date may be empty if the user only changes the time.

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
