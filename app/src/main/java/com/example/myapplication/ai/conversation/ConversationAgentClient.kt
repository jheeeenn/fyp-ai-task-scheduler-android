package com.example.myapplication.ai.conversation

import android.content.Context
import android.util.Log
import com.example.myapplication.SettingsActivity
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

class ConversationAgentClient(
    context: Context? = null,
    private val endpointUrl: String = SettingsActivity.DEFAULT_LM_STUDIO_ENDPOINT,
    private val modelId: String = "google/gemma-4-e2b"
) {
    private val appContext = context?.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun process(userText: String, memorySnapshot: String, appContextSummary: String): String =
        withContext(Dispatchers.IO) {
            val userPrompt = """
Memory snapshot:
$memorySnapshot

App context:
$appContextSummary

User text:
$userText
""".trimIndent()

            val payload = JSONObject().apply {
                put("model", modelId)
                put("temperature", 0.2)
                put("max_tokens", 160)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }

            val requestEndpointUrl = getEndpointUrl()
            Log.d("CONVO_AGENT_CONFIG", "Using LM Studio endpoint: $requestEndpointUrl")

            val request = Request.Builder()
                .url(requestEndpointUrl)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    Log.d("CONVO_AGENT", "HTTP ${response.code}: $body")

                    if (!response.isSuccessful) {
                        throw IOException("LM Studio HTTP ${response.code}: $body")
                    }

                    val choices = JSONObject(body).optJSONArray("choices")
                    if (choices == null || choices.length() == 0) {
                        throw IOException("LM Studio response missing choices[0].message.content")
                    }

                    choices
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("CONVO_AGENT", "LM Studio request timed out", e)
                throw IOException("LM Studio request timed out", e)
            } catch (e: java.io.InterruptedIOException) {
                Log.e("CONVO_AGENT", "LM Studio request timed out or was interrupted", e)
                throw IOException("LM Studio request timed out or was interrupted", e)
            }
        }

    private fun getEndpointUrl(): String {
        val configuredEndpoint = appContext
            ?.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(SettingsActivity.KEY_LM_STUDIO_ENDPOINT, endpointUrl)

        return configuredEndpoint?.takeIf { it.isNotBlank() } ?: endpointUrl
    }

    companion object {
        private val SYSTEM_PROMPT = """
You are ONLY a Conversation Orchestrator Agent for a voice-first Android task scheduling app for visually impaired users.

You are NOT the task command parser.
You are NOT allowed to output task-agent fields.
Never output these fields: natural_response, action, task_title, target_task_title, date, time, recurrence, priority, missing_fields, requires_confirmation, plan.

Your only job is to decide how the app should route the user's utterance.

Return ONLY one valid compact JSON object with EXACTLY these fields:
route, task_text, reply, confidence, listen_again

Allowed route values:
TASK_COMMAND
DIRECT_REPLY
ASK_CLARIFICATION
END_SESSION
UNKNOWN

Output format example:
{"route":"DIRECT_REPLY","task_text":"","reply":"Hello. I can help you manage your tasks by voice.","confidence":0.95,"listen_again":true}

Rules:
- For TASK_COMMAND, set task_text to the user's original task-related request and set reply to an empty string.
- For DIRECT_REPLY, set task_text to an empty string and provide a short natural spoken reply.
- For ASK_CLARIFICATION, ask one short clarification question.
- For END_SESSION, provide a short closing reply and set listen_again to false.
- For UNKNOWN, guide the user back to task scheduling in one short sentence.
- Do not claim that a task was created, deleted, updated, rescheduled, completed, or saved.
- Do not execute actions.
- Do not include markdown.
- Do not include explanations outside JSON.
- The first character of your response must be { and the last character must be }.
""".trimIndent()
    }
}
