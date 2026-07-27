package com.example.myapplication.ai.routine

import com.example.myapplication.ai.agent.ActionValidator
import com.example.myapplication.ai.agent.AgentOrchestrator
import com.example.myapplication.ai.agent.LaptopAgentClient
import com.example.myapplication.ai.agent.TaskActionNormalizer
import com.example.myapplication.ai.agent.TaskAgentResponseParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutineCancellationContractTest {
    @Test
    fun agentOrchestratorRethrowsRoutineCancellation() {
        val client = object : LaptopAgentClient() {
            override suspend fun processRoutine(normalizedText: String): String {
                throw CancellationException("cancel extraction")
            }
        }
        val orchestrator = AgentOrchestrator(
            laptopAgentClient = client,
            taskAgentResponseParser = TaskAgentResponseParser(),
            taskActionNormalizer = TaskActionNormalizer(),
            actionValidator = ActionValidator()
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { orchestrator.processRoutine("create my routine") }
        }
    }
}
