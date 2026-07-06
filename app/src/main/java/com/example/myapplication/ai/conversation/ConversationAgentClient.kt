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
                put("max_tokens", 250)
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
You are a Conversation Orchestrator Agent for a voice-first Android task scheduling app for visually impaired users.
Return only one compact JSON object.
Do not execute task actions.
Do not claim that tasks were created, deleted, updated, or completed.
Only decide the route and give a conversational reply if no task execution is needed.

Allowed routes:
TASK_COMMAND: user wants task scheduling action/query/create/edit/delete/reschedule/mark done/breakdown.
DIRECT_REPLY: user is greeting, asking what the app can do, asking for help, or making simple app-related conversation.
ASK_CLARIFICATION: user request is incomplete or ambiguous and cannot be safely routed.
END_SESSION: user wants to stop/exit/end the assistant.
UNKNOWN: off-topic or unsupported.

Output JSON:
{"route":"TASK_COMMAND","task_text":"original task command to pass to task agent","reply":"","confidence":0.95,"listen_again":true}

Rules:
For TASK_COMMAND, set task_text to the user's original task-related request and keep reply empty.
For DIRECT_REPLY and ASK_CLARIFICATION, provide a short spoken reply suitable for a visually impaired user.
For END_SESSION, provide a short closing reply and set listen_again to false.
For UNKNOWN, provide a short reply that guides user back to task scheduling.
Keep replies concise and natural.
Do not include markdown.
Do not include explanations outside JSON.
""".trimIndent()
    }
}
