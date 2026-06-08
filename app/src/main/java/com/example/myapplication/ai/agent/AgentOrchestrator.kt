package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.AiRouter

class AgentOrchestrator(
    private val laptopAgentClient: LaptopAgentClient,
    private val taskAgentResponseParser: TaskAgentResponseParser,
    private val taskActionNormalizer: TaskActionNormalizer,
    private val actionValidator: ActionValidator,
    private val fallbackAiRouter: AiRouter
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
            Log.e("AGENT_ORCHESTRATOR", "LM Studio task agent failed; falling back to AiRouter", e)
            Log.d("AGENT_FALLBACK", "Fallback AiRouter processing text='$normalizedText'")
            fallbackAiRouter.process(normalizedText)
        }
    }
}
