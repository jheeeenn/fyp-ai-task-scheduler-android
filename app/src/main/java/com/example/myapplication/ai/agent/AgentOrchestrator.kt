package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.routine.RoutineExtractionResponse
import com.example.myapplication.ai.routine.RoutineExtractionResponseParser
import kotlinx.coroutines.CancellationException

class TaskAgentProcessingException(message: String, cause: Throwable) : Exception(message, cause)

class AgentOrchestrator(
    private val laptopAgentClient: LaptopAgentClient,
    private val taskAgentResponseParser: TaskAgentResponseParser,
    private val taskActionNormalizer: TaskActionNormalizer,
    private val actionValidator: ActionValidator,
    private val contextActionExtractionParser: ContextActionExtractionResponseParser =
        ContextActionExtractionResponseParser(),
    private val contextActionExtractionValidator: ContextActionExtractionValidator =
        ContextActionExtractionValidator(),
    private val routineExtractionParser: RoutineExtractionResponseParser =
        RoutineExtractionResponseParser()
) {
    suspend fun process(normalizedText: String): AiParsedCommand {
        return try {
            Log.d("AGENT_ORCHESTRATOR", "Trying LM Studio task agent first")
            val rawContent = laptopAgentClient.process(normalizedText)
            val agentResponse = taskAgentResponseParser.parse(rawContent)
            val normalizedCommand = taskActionNormalizer.normalize(agentResponse)
            val validatedCommand = actionValidator.validate(normalizedCommand)
            Log.d("AGENT_ORCHESTRATOR", "LM Studio task agent accepted ${validatedCommand.intent}")
            validatedCommand
        } catch (e: Exception) {
            Log.e("AGENT_ORCHESTRATOR", "Task agent failed closed for text='$normalizedText'", e)
            throw TaskAgentProcessingException("Task agent failed to process command", e)
        }
    }

    suspend fun processContextAction(
        normalizedText: String,
        expectedAction: ConversationContextAction
    ): ContextActionChangeSet {
        return try {
            Log.d("AGENT_ORCHESTRATOR", "Trying bounded context-action extraction")
            val rawContent = laptopAgentClient.processContextAction(normalizedText, expectedAction)
            val response = contextActionExtractionParser.parse(rawContent)
            contextActionExtractionValidator.validate(response, expectedAction)
        } catch (e: Exception) {
            Log.e("AGENT_ORCHESTRATOR", "Context-action extraction failed closed", e)
            throw TaskAgentProcessingException("Task agent failed to extract context action", e)
        }
    }

    suspend fun processRoutine(normalizedText: String): RoutineExtractionResponse {
        return try {
            Log.d("ROUTINE_EXTRACTION", "Starting bounded routine extraction")
            val rawContent = laptopAgentClient.processRoutine(normalizedText)
            val response = routineExtractionParser.parse(rawContent)
            Log.d(
                "ROUTINE_EXTRACTION",
                "stepCount=${response.steps.size} " +
                    "needClarification=${response.needClarification}"
            )
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ROUTINE_EXTRACTION", "Routine extraction failed closed", e)
            throw TaskAgentProcessingException(
                "Task agent failed to extract a routine",
                e
            )
        }
    }
}
