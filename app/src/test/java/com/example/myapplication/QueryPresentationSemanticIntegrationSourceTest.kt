package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QueryPresentationSemanticIntegrationSourceTest {
    private val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
    private val client = File(
        "src/main/java/com/example/myapplication/ai/conversation/ConversationAgentClient.kt"
    ).readText()
    private val semantic = File(
        "src/main/java/com/example/myapplication/ai/conversation/querypresentation/" +
            "QueryPresentationSemanticOrchestrator.kt"
    ).readText()

    @Test
    fun homeUsesSameConversationClientAfterValidatedTaskAgentResult() {
        val initialization = home.substringAfter("val conversationAgentClient =")
            .substringBefore("conversationIntentClassifier =")
        assertTrue(initialization.contains("ConversationOrchestrator(\n            conversationAgentClient"))
        assertTrue(
            initialization.contains(
                "QueryPresentationSemanticOrchestrator(\n            conversationAgentClient"
            )
        )

        val pipeline = home.substringAfter("val aiResult = agentOrchestrator.process(taskAgentInput)")
            .substringBefore("// branches for actions")
        val semanticCall = pipeline.indexOf("resolveForValidatedIntent(")
        val reconciliation = pipeline.indexOf("TaskQueryPresentationReconciler.reconcile(")
        assertTrue(semanticCall >= 0 && semanticCall < reconciliation)
        assertTrue(pipeline.contains("normalizedUserText = normalized"))
        assertTrue(pipeline.contains("validatedTaskAgentIntent = aiResult.intent"))
        assertTrue(pipeline.contains("boundedSemantic = boundedPresentation.presentation"))
        assertTrue(pipeline.contains("if (!isAssistantRequestCurrent(requestToken)) return@launch"))
    }

    @Test
    fun dedicatedRequestContainsNoTaskRowsResultsOrExecutionAuthority() {
        val method = client.substringAfter("override suspend fun classifyQueryPresentation(")
            .substringBefore("private fun executeConversationRequest(")
        assertTrue(method.contains("normalized_user_utterance"))
        assertTrue(method.contains("validated_task_agent_intent"))
        assertTrue(method.contains("RequestKind.QUERY_PRESENTATION"))
        listOf(
            "task_id",
            "room_id",
            "temporary_ref",
            "query_results",
            "task_count",
            "task_title",
            "reminder",
            "due_date",
            "due_time"
        ).forEach { forbidden -> assertFalse(forbidden, method.contains(forbidden)) }

        listOf("AppDatabase", "TaskEntity", "Room", "ReminderHelper", "startActivity(")
            .forEach { forbidden -> assertFalse(forbidden, semantic.contains(forbidden)) }
    }

    @Test
    fun productionClassifierHasStrictSmallDeterministicConfigurationAndNoPhraseRouter() {
        assertTrue(client.contains("RequestKind.QUERY_PRESENTATION -> QUERY_PRESENTATION_TEMPERATURE"))
        assertTrue(client.contains("RequestKind.QUERY_PRESENTATION -> QUERY_PRESENTATION_MAX_TOKENS"))
        assertTrue(
            client.contains(
                "RequestKind.QUERY_PRESENTATION ->\n" +
                    "                AgentResponseSchemas.queryPresentationSemanticResponseFormat()"
            )
        )
        assertTrue(client.contains("const val QUERY_PRESENTATION_TEMPERATURE = 0.0"))
        assertTrue(client.contains("const val QUERY_PRESENTATION_MAX_TOKENS = 48"))
        assertFalse(semantic.contains("Regex("))
        assertFalse(semantic.contains("normalizedUserText.contains"))
        assertFalse(semantic.contains("when (normalizedUserText"))
    }
}
