package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand

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
            AiIntent.CREATE_TASK.name -> {
                if (command.taskTitle.isNullOrBlank()) {
                    fail("CREATE_TASK requires taskTitle")
                }
            }

            AiIntent.BREAKDOWN_TASK.name -> {
                if (command.taskTitle.isNullOrBlank()) {
                    fail("BREAKDOWN_TASK requires taskTitle")
                }

                if (command.plan.size !in 2..4) {
                    fail("BREAKDOWN_TASK requires 2 to 4 subtasks")
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

        Log.d("TASK_AGENT_VALIDATE", "Accepted action=${command.intent}, confidence=${command.confidence}")
        return command
    }

    private fun fail(message: String): Nothing {
        Log.e("TASK_AGENT_VALIDATE", message)
        throw TaskAgentValidationException(message)
    }
}
