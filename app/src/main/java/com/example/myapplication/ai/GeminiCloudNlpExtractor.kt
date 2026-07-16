package com.example.myapplication.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Deprecated("GeminiCloudNlpExtractor is legacy FYP1 cloud extraction. FYP2 production must not call Gemini.")
class GeminiCloudNlpExtractor
constructor(
    private val apiKey: String
) : CloudNlpExtractor {

    private val client = OkHttpClient()

    override suspend fun extract(normalizedText: String): AiParsedCommand {
        return try {
            withContext(Dispatchers.IO) {
                // selecting gemini model here via url
                val url =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                //Log.d("LEGACY_CLOUD_URL", url)

                val schema = JSONObject().apply {
                    put("type", "OBJECT")

                    put("properties", JSONObject().apply {
                        put("intent", JSONObject().apply {
                            put("type", "STRING")
                            put("enum", JSONArray().apply {
                                put("CREATE_TASK")
                                put("UPDATE_TASK")
                                put("DELETE_TASK")
                                put("QUERY_TASK")
                                put("RESCHEDULE_TASK")
                                put("UNKNOWN")
                            })
                        })

                        put("taskTitle", JSONObject().apply { put("type", "STRING") })
                        put("dateText", JSONObject().apply { put("type", "STRING") })
                        put("timeText", JSONObject().apply { put("type", "STRING") })
                        put("recurrence", JSONObject().apply { put("type", "STRING") })
                        put("priority", JSONObject().apply { put("type", "STRING") })
                        put("confidence", JSONObject().apply { put("type", "NUMBER") })
                        put("source", JSONObject().apply { put("type", "STRING") })
                    })

                    put("required", JSONArray().apply {
                        put("intent")
                        put("source")
                    })
                }

                val prompt = """
You are an information extraction engine for a voice-first task scheduling assistant for visually impaired users.

Your job is to convert a spoken scheduling command into structured JSON.

Return only valid JSON.

Field meanings:
- intent: one of CREATE_TASK, UPDATE_TASK, DELETE_TASK, QUERY_TASK, RESCHEDULE_TASK, UNKNOWN
- taskTitle: the actual task or activity the user wants to remember or do
- dateText: the date expression, such as today, tomorrow, friday
- timeText: the time expression, such as 8 pm, after dinner, before meeting, evening
- If the user gives a semantic time phrase like "after dinner" or "when i get home", keep it in timeText exactly as a natural-language time phrase. Do not invent an exact clock time.
- recurrence: recurrence phrase if any
- priority: priority phrase if any
- source: always "cloud"
- confidence: number between 0 and 1

Important extraction rules:
- Do not put the task action into timeText.
- Do not put time phrases into taskTitle.
- For commands like "remind me after dinner to take medicine":
  - taskTitle = "take medicine"
  - timeText = "after dinner"
  - dateText = "today"
- For commands like "remind me when i get home to call doctor":
  - taskTitle = "call doctor"
  - timeText = "when i get home"
- If the date is not explicitly stated but the phrase implies the same day, use "today".
- Keep extracted text concise and meaningful.

Example 1:
Input: remind me after dinner to take medicine
Output:
{
  "intent": "CREATE_TASK",
  "taskTitle": "take medicine",
  "dateText": "today",
  "timeText": "after dinner",
  "recurrence": "",
  "priority": "",
  "source": "cloud",
  "confidence": 0.9
}

Example 2:
Input: remind me when i get home to call doctor
Output:
{
  "intent": "CREATE_TASK",
  "taskTitle": "call doctor",
  "dateText": "today",
  "timeText": "when i get home",
  "recurrence": "",
  "priority": "",
  "source": "cloud",
  "confidence": 0.9
}

User command:
"$normalizedText"
""".trimIndent()

                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })

                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("responseSchema", schema)
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(
                        payload.toString()
                            .toRequestBody("application/json".toMediaType())
                    )
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()

                Log.d("LEGACY_CLOUD_RAW", body)

                if (!response.isSuccessful) {
                    Log.e("LEGACY_CLOUD_HTTP", "HTTP ${response.code}: $body")
                    return@withContext AiParsedCommand(
                        intent = AiIntent.UNKNOWN.name,
                        confidence = 0.0f,
                        source = "cloud_error"
                    )
                }

                val root = JSONObject(body)
                val candidates = root.optJSONArray("candidates")

                if (candidates == null || candidates.length() == 0) {
                    Log.e("LEGACY_CLOUD_EMPTY", "No candidates returned")
                    return@withContext AiParsedCommand(
                        intent = AiIntent.UNKNOWN.name,
                        confidence = 0.0f,
                        source = "cloud_empty"
                    )
                }

                val text = candidates
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                Log.d("LEGACY_CLOUD_TEXT", text)

                val parsed = JSONObject(text)

                val result = AiParsedCommand(
                    intent = parsed.optString("intent", AiIntent.UNKNOWN.name),
                    taskTitle = parsed.optString("taskTitle").takeIf { it.isNotBlank() },
                    dateText = parsed.optString("dateText").takeIf { it.isNotBlank() },
                    timeText = parsed.optString("timeText").takeIf { it.isNotBlank() },
                    recurrence = parsed.optString("recurrence").takeIf { it.isNotBlank() },
                    priority = parsed.optString("priority").takeIf { it.isNotBlank() },
                    confidence = parsed.optDouble("confidence", 0.6).toFloat(),
                    source = parsed.optString("source", "cloud")
                )

                val validated = fillImplicitDate(validateCloudResult(result))

                Log.d(
                    "LEGACY_CLOUD_PARSED",
                    "intent=${validated.intent}, title=${validated.taskTitle}, date=${validated.dateText}, time=${validated.timeText}, source=${validated.source}, confidence=${validated.confidence}"
                )

                validated
            }
        } catch (e: Exception) {
            Log.e("LEGACY_CLOUD_EXCEPTION", "Gemini extractor crashed", e)
            AiParsedCommand(
                intent = AiIntent.UNKNOWN.name,
                confidence = 0.0f,
                source = "cloud_exception"
            )
        }
    } // end of extract()

    private fun validateCloudResult(result: AiParsedCommand): AiParsedCommand {
        var title = result.taskTitle
        var date = result.dateText
        var time = result.timeText

        // If Gemini put too much text into timeText and taskTitle is missing,
        // try to recover title from phrases like "after dinner to take medicine"
        if (title.isNullOrBlank() && !time.isNullOrBlank()) {
            val lower = time.lowercase()

            if (lower.contains(" to ")) {
                val parts = time.split(" to ", limit = 2)
                if (parts.size == 2) {
                    time = parts[0].trim()
                    title = parts[1].trim()
                }
            }

            // Extra cleanup for strange wording
            title = title?.replace(Regex("\\bconfidential\\b", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("\\bmedication\\b", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()

            time = time?.replace(Regex("\\s+"), " ")?.trim()

            if (title.isNullOrBlank()) {
                title = null
            }
            if (time.isNullOrBlank()) {
                time = null
            }
        }

        return result.copy(
            taskTitle = title,
            dateText = date,
            timeText = time
        )
    }

    private fun fillImplicitDate(result: AiParsedCommand): AiParsedCommand {
        val time = result.timeText?.lowercase() ?: return result
        val date = result.dateText

        if (date.isNullOrBlank()) {
            if (
                time.contains("after dinner") ||
                time.contains("before dinner") ||
                time.contains("after lunch") ||
                time.contains("after work") ||
                time.contains("tonight") ||
                time.contains("this evening") ||
                time.contains("when i get home") ||
                time.contains("when i arrive home")
            ) {
                return result.copy(dateText = "today")
            }
        }

        return result
    }
}
