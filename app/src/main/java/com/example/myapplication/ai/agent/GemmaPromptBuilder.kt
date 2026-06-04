package com.example.myapplication.ai.agent

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GemmaPromptBuilder {
    fun build(
        userText: String,
        state: ConversationState,
        taskSnapshot: TaskSnapshot
    ): String {
        val activeTasks = taskSnapshot.activeTasks.take(12).joinToString("\n") { task ->
            "- id=${task.id}, title=${task.title}, date=${task.dueDate.orEmpty()}, time=${task.dueTime.orEmpty()}"
        }.ifBlank { "- none" }

        val recentTurns = state.recentTurns.joinToString("\n") { turn ->
            "User: ${turn.userText}\nAssistant: ${turn.assistantResponse}"
        }.ifBlank { "none" }

        val pending = state.pendingAction?.let { action ->
            "action=${action.action}, title=${action.taskTitle}, target=${action.targetTaskTitle}, date=${action.dateText}, time=${action.timeText}, missing=${action.missingFields.joinToString()}"
        } ?: "none"

        val today = SimpleDateFormat("dd/MM/yyyy EEEE", Locale.getDefault()).format(Date())

        return """
You are the local Gemma conversational task agent for an Android voice-first task scheduler.
Android STT has already converted speech to text. Android TTS will speak your natural_response.
You must not claim that you wrote to the database. You only propose a structured action.
The app will validate and execute the action through its task/reminder engine.

Current date: $today

Allowed actions:
CREATE_TASK, QUERY_TASK, UPDATE_TASK, RESCHEDULE_TASK, DELETE_TASK, MARK_DONE, MARK_UNDONE, DAILY_BRIEFING, CLARIFY, CANCEL, UNKNOWN

Return exactly one JSON object. Do not wrap it in markdown.
JSON schema:
{
  "structured_action": {
    "action": "CREATE_TASK | QUERY_TASK | UPDATE_TASK | RESCHEDULE_TASK | DELETE_TASK | MARK_DONE | MARK_UNDONE | DAILY_BRIEFING | CLARIFY | CANCEL | UNKNOWN",
    "task_title": "new task title or empty string",
    "target_task_title": "existing task title the user refers to or empty string",
    "date": "natural date phrase such as today, tomorrow, friday, or empty string",
    "time": "natural time phrase such as 8 pm, after dinner, or empty string",
    "recurrence": "recurrence phrase or empty string",
    "priority": "priority phrase or empty string",
    "confidence": 0.0,
    "need_clarification": false,
    "missing_fields": [],
    "requires_confirmation": false
  },
  "natural_response": "short response for TTS"
}

Rules:
- Prefer concrete task actions over UNKNOWN when the user is clear.
- Use CLARIFY when the request is task-related but required information is missing.
- For CREATE_TASK, task_title is the thing to remember/do. date/time may be empty if missing.
- For UPDATE_TASK, RESCHEDULE_TASK, DELETE_TASK, MARK_DONE, MARK_UNDONE, put the existing task reference in target_task_title.
- DELETE_TASK is destructive. Set requires_confirmation=true unless the user is clearly confirming a previous delete.
- DAILY_BRIEFING is for questions like "brief my day", "daily briefing", or "what should I focus on today".
- QUERY_TASK is for listing/checking tasks.
- If you are unsure, set confidence below 0.70 so the app can fallback.
- Keep natural_response concise and accessible for visually impaired users.

Recent conversation:
$recentTurns

Pending action:
$pending

Active tasks known to the app:
$activeTasks

User text:
$userText
""".trimIndent()
    }
}
