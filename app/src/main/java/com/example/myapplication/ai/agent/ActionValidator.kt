package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.TaskQueryPresentation

class TaskAgentValidationException(message: String) : Exception(message)

class ActionValidator {
    fun validate(command: AiParsedCommand): AiParsedCommand {
        val supportedActions = setOf(
            AiIntent.CREATE_TASK.name,
            AiIntent.QUERY_TASK.name,
            AiIntent.DELETE_TASK.name,
            AiIntent.RESCHEDULE_TASK.name,
            AiIntent.UPDATE_TASK.name,
            AiIntent.MARK_DONE.name,
            AiIntent.MARK_UNDONE.name,
            AiIntent.BREAKDOWN_TASK.name
        )

        if (command.intent !in supportedActions) {
            fail("Unsupported task-agent action '${command.intent}'")
        }

        if (command.confidence < 0.60f) {
            fail("Task-agent confidence ${command.confidence} is below 0.60")
        }

        when (command.intent) {
            AiIntent.QUERY_TASK.name -> {
                if (command.queryPresentation == TaskQueryPresentation.NONE) {
                    fail("QUERY_TASK requires a query presentation")
                }
            }

            AiIntent.CREATE_TASK.name -> {
                if (command.taskTitle.isNullOrBlank()) {
                    fail("CREATE_TASK requires taskTitle")
                }
            }

            AiIntent.BREAKDOWN_TASK.name -> {
                if (command.taskTitle.isNullOrBlank()) {
                    fail("BREAKDOWN_TASK requires taskTitle")
                }
            }

            AiIntent.DELETE_TASK.name,
            AiIntent.RESCHEDULE_TASK.name,
            AiIntent.UPDATE_TASK.name,
            AiIntent.MARK_DONE.name,
            AiIntent.MARK_UNDONE.name -> {
                if (command.targetTaskTitle.isNullOrBlank() && command.targetDateText.isNullOrBlank() && command.targetTimeText.isNullOrBlank() && command.dateText.isNullOrBlank() && command.timeText.isNullOrBlank()) {
                    fail("${command.intent} requires targetTaskTitle or temporal constraint")
                }
            }
        }

        if (
            command.intent != AiIntent.QUERY_TASK.name &&
            command.queryPresentation != TaskQueryPresentation.NONE
        ) {
            fail("${command.intent} requires queryPresentation=NONE")
        }

        Log.d("TASK_AGENT_VALIDATE", "Accepted action=${command.intent}, confidence=${command.confidence}")
        return command
    }

    private fun fail(message: String): Nothing {
        Log.e("TASK_AGENT_VALIDATE", message)
        throw TaskAgentValidationException(message)
    }
}
