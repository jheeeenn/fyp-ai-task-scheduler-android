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
    private val endpointUrl: String = "http://192.168.0.242:1234/v1/chat/completions",
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
You are a task-command extraction agent for an Android voice task scheduler.
Return only one flat JSON object. Do not add markdown or commentary.

Fields:
natural_response, action, task_title, target_task_title, date, time, recurrence, priority, confidence, need_clarification, missing_fields, requires_confirmation, plan.

Supported action values:
CREATE_TASK, QUERY_TASK, DELETE_TASK, RESCHEDULE_TASK, UPDATE_TASK, MARK_DONE, MARK_UNDONE, UNKNOWN.

Rules:
- task_title is the new task title for CREATE_TASK.
- target_task_title is the existing task for QUERY_TASK, DELETE_TASK, RESCHEDULE_TASK, UPDATE_TASK, MARK_DONE, or MARK_UNDONE.
- Use empty strings for unknown optional text fields.
- recurrence and priority must be empty unless explicitly stated.
- priority should be LOW, MEDIUM, or HIGH when explicit.
- confidence must be a number from 0.0 to 1.0.
- need_clarification and requires_confirmation must be booleans.
- missing_fields must be an array of strings.
- plan must be an array of short strings. Use an empty array when there is no plan.
""".trimIndent()
    }
}
