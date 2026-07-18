package com.example.myapplication.ai.conversation

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

class ConversationAgentResponseException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

open class ConversationAgentClient(
    context: Context? = null,
    private val endpointUrl: String = SettingsActivity.DEFAULT_CONVERSATION_AGENT_ENDPOINT,
    private val modelId: String = "google/gemma-4-e2b"
) {
    private val appContext = context?.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    open suspend fun process(userText: String, memorySnapshot: String, appContextSummary: String): String =
        withContext(Dispatchers.IO) {
            val userPrompt = """
Memory snapshot:
$memorySnapshot

App context:
$appContextSummary

User text:
$userText
""".trimIndent()

            executeConversationRequest(userPrompt, RequestKind.ROUTING)
        }

    open suspend fun processRepair(userText: String, appContextSummary: String): String =
        withContext(Dispatchers.IO) {
            val repairPrompt = """
App context:
$appContextSummary

User text:
$userText

Return the routing decision using only the required ConversationDecision schema. Do not output task-agent fields. Do not explain your reasoning.
""".trimIndent()

            executeConversationRequest(repairPrompt, RequestKind.ROUTING)
        }

    open suspend fun respondToObservation(observationJson: String, memorySnapshot: String, appContextSummary: String): String =
        withContext(Dispatchers.IO) {
            val userPrompt = """
Memory snapshot:
$memorySnapshot

App context:
$appContextSummary

Authoritative ExecutionObservation JSON:
$observationJson
""".trimIndent()
            executeConversationRequest(userPrompt, RequestKind.RESPONSE)
        }

    private fun executeConversationRequest(userPrompt: String, kind: RequestKind): String {
        val payload = JSONObject().apply {
            put("model", modelId)
            put("temperature", if (kind == RequestKind.ROUTING) ROUTING_TEMPERATURE else RESPONSE_TEMPERATURE)
            put("max_tokens", if (kind == RequestKind.ROUTING) 256 else RESPONSE_MAX_TOKENS)
            put("stream", false)
            put(
                "response_format",
                if (kind == RequestKind.ROUTING) AgentResponseSchemas.conversationDecisionResponseFormat() else AgentResponseSchemas.conversationResponseResponseFormat()
            )
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", if (kind == RequestKind.ROUTING) SYSTEM_PROMPT else RESPONSE_SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        Log.d(
            "CONVO_AGENT_SCHEMA",
            if (kind == RequestKind.ROUTING) "Structured ConversationDecision schema enabled" else "Structured ConversationResponse schema enabled"
        )

        val requestEndpointUrl = getEndpointUrl()
        Log.d(
            "CONVO_AGENT_CONFIG",
            "Using Conversation Agent endpoint: $requestEndpointUrl"
        )

        val request = Request.Builder()
            .url(requestEndpointUrl)
            .post(
                payload.toString()
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d("CONVO_AGENT", "HTTP ${response.code}: $body")

                if (!response.isSuccessful) {
                    throw IOException(
                        "LM Studio HTTP ${response.code}: $body"
                    )
                }

                val choices = JSONObject(body).optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    throw IOException(
                        "LM Studio response missing choices[0].message.content"
                    )
                }

                val choice = choices.getJSONObject(0)
                val message = choice.getJSONObject("message")

                val content = message.optString("content", "")
                val reasoningChars =
                    message.optString("reasoning_content", "").length
                val finishReason =
                    choice.optString("finish_reason", "")

                if (content.isBlank()) {
                    val lengthMessage =
                        if (finishReason == "length") {
                            " Conversation Agent exhausted its output token " +
                                    "budget before producing structured content."
                        } else {
                            ""
                        }

                    val errorMessage =
                        "Conversation Agent returned blank content. " +
                                "finishReason=$finishReason, " +
                                "reasoningChars=$reasoningChars.$lengthMessage"

                    Log.e("CONVO_AGENT", errorMessage)
                    throw ConversationAgentResponseException(errorMessage)
                }

                content
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("CONVO_AGENT", "LM Studio request timed out", e)
            throw IOException("LM Studio request timed out", e)
        } catch (e: java.io.InterruptedIOException) {
            Log.e(
                "CONVO_AGENT",
                "LM Studio request timed out or was interrupted",
                e
            )
            throw IOException(
                "LM Studio request timed out or was interrupted",
                e
            )
        }
    }

    private fun getEndpointUrl(): String {
        val prefs = appContext
            ?.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

        val configuredEndpoint = if (prefs?.contains(SettingsActivity.KEY_CONVERSATION_AGENT_ENDPOINT) == true) {
            prefs.getString(SettingsActivity.KEY_CONVERSATION_AGENT_ENDPOINT, null)
        } else {
            null
        }
        if (!configuredEndpoint.isNullOrBlank()) return configuredEndpoint

        val legacyEndpoint = prefs?.getString(SettingsActivity.KEY_LM_STUDIO_ENDPOINT, null)
        if (!legacyEndpoint.isNullOrBlank()) return legacyEndpoint

        return endpointUrl
    }

    private enum class RequestKind { ROUTING, RESPONSE }

    companion object {
        const val ROUTING_TEMPERATURE = 0.0
        const val RESPONSE_TEMPERATURE = 0.35
        const val RESPONSE_MAX_TOKENS = 128
        val RESPONSE_SYSTEM_PROMPT = """
You are the response-writing part of the Conversation Agent.
Android has already interpreted and executed the task operation.
The ExecutionObservation is trusted and authoritative.
Do not reinterpret the user's command.
Do not add facts that are absent from the observation.
Do not change titles, dates, times, counts, options or outcomes.
Do not claim success unless outcome is SUCCESS or PARTIAL_SUCCESS.
For NOT_FOUND, say that the requested task could not be found.
For AMBIGUOUS, mention only the supplied choices.
For NEEDS_CONFIRMATION, ask one clear confirmation question.
For NEEDS_CLARIFICATION, clearly tell the user what value is required.
When requiredInput is present, tell the user what they may say next.
Keep speech concise and suitable for TTS.
Prefer one or two short sentences.
Avoid long introductions.
Avoid visual phrases such as “as shown on screen”.
Do not mention JSON, schemas, agents, Android internals or databases.
Do not output markdown.
Return only the required ConversationResponse JSON.
""".trimIndent()

        private val SYSTEM_PROMPT = """
You are the Conversation Orchestrator Agent in a centralized multi-agent task scheduling app for visually impaired users.

Every user utterance is sent to you first.
You decide whether to answer directly or delegate to a specialized task agent.

You are NOT the task command extraction agent.
You are NOT allowed to output task-agent fields.
Never output these fields:
natural_response, action, task_title, target_task_title, date, time, recurrence, priority, missing_fields, requires_confirmation, plan.

Return ONLY one valid compact JSON object.
The JSON object must contain EXACTLY these fields:
route, task_text, reply, confidence, listen_again

Allowed route values:
TASK_COMMAND
DIRECT_REPLY
ASK_CLARIFICATION
END_SESSION
UNKNOWN

Route rules:
- Use DIRECT_REPLY for greetings, small talk, thanks, app capability questions, or general help.
- Use TASK_COMMAND only when the user wants to create, query, update, reschedule, delete, mark done/undone, or break down a task.
- Use ASK_CLARIFICATION when the user seems to want a task action but the request is too unclear to pass safely to the task agent.
- Use END_SESSION when the user wants to stop or exit the assistant.
- Use UNKNOWN for unsupported off-topic requests.

Output examples:
User: hello
{"route":"DIRECT_REPLY","task_text":"","reply":"Hello. I can help you manage your tasks by voice.","confidence":0.95,"listen_again":true}

User: how are you
{"route":"DIRECT_REPLY","task_text":"","reply":"I am ready to help you manage your tasks. What would you like to do?","confidence":0.95,"listen_again":true}

User: what can you do
{"route":"DIRECT_REPLY","task_text":"","reply":"I can help you create, check, reschedule, delete, complete, and break down tasks by voice.","confidence":0.95,"listen_again":true}

User: what tasks do i have today
{"route":"TASK_COMMAND","task_text":"what tasks do i have today","reply":"","confidence":0.95,"listen_again":true}

User: remind me to take medicine tomorrow at 6 pm
{"route":"TASK_COMMAND","task_text":"remind me to take medicine tomorrow at 6 pm","reply":"","confidence":0.95,"listen_again":true}

User: bye
{"route":"END_SESSION","task_text":"","reply":"Okay, stopping the assistant.","confidence":0.95,"listen_again":false}

Rules:
- For TASK_COMMAND, copy the user's task-related request into task_text and keep reply empty.
- For DIRECT_REPLY, keep task_text empty and provide a short natural spoken reply.
- For ASK_CLARIFICATION, ask one short clarification question.
- For END_SESSION, set listen_again to false.
- Do not claim that a task was created, deleted, updated, rescheduled, completed, or saved.
- Do not execute actions.
- Do not include markdown.
- Do not include explanations outside JSON.
- The first character of your response must be { and the last character must be }.
""".trimIndent()
    }
}
