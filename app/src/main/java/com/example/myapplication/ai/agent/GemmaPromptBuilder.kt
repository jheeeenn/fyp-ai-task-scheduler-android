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
        val today = SimpleDateFormat("dd/MM/yyyy EEEE", Locale.getDefault()).format(Date())

        return """
You are a task scheduling command parser for a voice-first Android app.

Current date: $today

Return only one JSON object.
No markdown.
No explanation.

Allowed actions:
CREATE_TASK, QUERY_TASK, UPDATE_TASK, RESCHEDULE_TASK, DELETE_TASK, MARK_DONE, MARK_UNDONE, DAILY_BRIEFING, CLARIFY, CANCEL, UNKNOWN

JSON format:
{
  "structured_action": {
    "action": "CREATE_TASK",
    "task_title": "",
    "target_task_title": "",
    "date": "",
    "time": "",
    "recurrence": "",
    "priority": "",
    "confidence": 0.0,
    "need_clarification": false,
    "missing_fields": [],
    "requires_confirmation": false
  },
  "natural_response": ""
}

Rules:
- For creating a reminder or task, use CREATE_TASK.
- For checking tasks, use QUERY_TASK.
- For changing date or time of an existing task, use RESCHEDULE_TASK.
- For deleting a task, use DELETE_TASK and requires_confirmation=true.
- For completing a task, use MARK_DONE.
- For unclear task-related requests, use CLARIFY.
- Keep natural_response short and suitable for text-to-speech.
- Do not say the task has been saved. Say "I prepared..." instead.

User text: "$userText"
""".trimIndent()
    }
}