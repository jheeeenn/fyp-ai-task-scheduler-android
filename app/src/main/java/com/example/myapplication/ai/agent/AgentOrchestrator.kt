package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.data.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentOrchestrator(
    private val gemmaLocalClient: GemmaLocalClient,
    private val promptBuilder: GemmaPromptBuilder,
    private val responseParser: AgentResponseParser,
    private val actionValidator: ActionValidator,
    private val stateManager: ConversationStateManager,
    private val taskDao: TaskDao,
    private val actionExecutor: TaskActionExecutor,
    private val fallbackExecutor: suspend (AiParsedCommand, String, String?) -> Unit,
    private val fallbackParser: suspend (String) -> AiParsedCommand
) {
    suspend fun process(userText: String): AgentExecutionResult {
        return try {
            handlePendingConfirmation(userText)?.let { return it }

            val snapshot = loadTaskSnapshot()
            val prompt = promptBuilder.build(
                userText = userText,
                state = stateManager.currentState(),
                taskSnapshot = snapshot
            )

            val raw = gemmaLocalClient.generate(prompt)
            Log.d("AGENT_GEMMA_RAW", raw)

            val parsedResponse = responseParser.parse(raw)
                ?: return fallback(userText, "Gemma returned invalid JSON")
            val response = enforceDestructiveConfirmation(parsedResponse)

            val validation = actionValidator.validate(response)
            if (!validation.isValid && validation.shouldFallback) {
                return fallback(userText, validation.reason ?: "Gemma validation failed")
            }

            val executionResult = actionExecutor.execute(userText, response, validation)
            if (executionResult.usedFallback) {
                return fallback(userText, executionResult.reason ?: "Gemma execution requested fallback")
            }

            stateManager.recordAgentResponse(userText, response)
            executionResult
        } catch (e: Exception) {
            Log.e("AGENT_ORCHESTRATOR", "Gemma-first path failed, falling back", e)
            fallback(userText, e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun handlePendingConfirmation(userText: String): AgentExecutionResult? {
        val pending = stateManager.currentState().pendingAction ?: return null
        val normalized = userText.trim().lowercase()
        val isYes = normalized in setOf("yes", "yeah", "yep", "confirm", "delete it", "do it", "sure")
        val isNo = normalized in setOf("no", "nope", "cancel", "stop", "do not", "don't")

        if (!isYes && !isNo) return null

        if (isNo) {
            stateManager.clearPending()
            val response = AgentResponse(
                structuredAction = StructuredAction(action = AgentActionType.CANCEL, confidence = 1f),
                naturalResponse = "Okay, I cancelled that."
            )
            val result = actionExecutor.execute(userText, response, ValidationResult(isValid = true))
            stateManager.recordAgentResponse(userText, response)
            return result
        }

        val confirmed = pending.copy(requiresConfirmation = false, confidence = maxOf(pending.confidence, 0.90f))
        stateManager.clearPending()
        val response = AgentResponse(
            structuredAction = confirmed,
            naturalResponse = "Okay, I’ll continue."
        )
        val validation = actionValidator.validate(response)
        val result = actionExecutor.execute(userText, response, validation)
        stateManager.recordAgentResponse(userText, response)
        return result
    }

    private fun enforceDestructiveConfirmation(response: AgentResponse): AgentResponse {
        val action = response.structuredAction
        val pendingDelete = stateManager.currentState().pendingAction?.action == AgentActionType.DELETE_TASK
        if (action.action != AgentActionType.DELETE_TASK || action.requiresConfirmation || pendingDelete) {
            return response
        }

        val target = action.targetTaskTitle ?: action.taskTitle ?: "that task"
        return response.copy(
            structuredAction = action.copy(requiresConfirmation = true),
            naturalResponse = "Deleting $target cannot be undone. Should I delete it?"
        )
    }

    private suspend fun loadTaskSnapshot(): TaskSnapshot {
        return withContext(Dispatchers.IO) {
            TaskSnapshot(
                activeTasks = taskDao.getActiveTasks(),
                allTasks = taskDao.getAll()
            )
        }
    }

    private suspend fun fallback(userText: String, reason: String): AgentExecutionResult {
        Log.d("AGENT_FALLBACK", reason)
        stateManager.recordFallback(userText, reason)
        val fallbackCommand = fallbackParser(userText)
        fallbackExecutor(fallbackCommand, userText, null)
        return AgentExecutionResult(
            handled = true,
            usedFallback = true,
            reason = reason
        )
    }
}
