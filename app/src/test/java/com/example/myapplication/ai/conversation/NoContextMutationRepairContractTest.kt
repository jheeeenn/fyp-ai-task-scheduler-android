package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoContextMutationRepairContractTest {
    @Test
    fun dedicatedSchemaAllowsExactlyTaskCommandAndClarification() {
        val format = AgentResponseSchemas.noContextMutationRepairResponseFormat()
        val jsonSchema = format.getJSONObject("json_schema")
        val objectSchema = jsonSchema.getJSONObject("schema")
        val properties = objectSchema.getJSONObject("properties")

        assertEquals("no_context_mutation_repair", jsonSchema.getString("name"))
        assertEquals(
            listOf("TASK_COMMAND", "ASK_CLARIFICATION"),
            enumValues(properties, "route")
        )
        assertFalse(enumValues(properties, "route").contains("CONTEXT_ACTION"))
        assertEquals(listOf(""), enumValues(properties, "context_ref"))
        listOf(
            "context_detail",
            "context_action",
            "setting_action",
            "setting_target",
            "query_reading_move",
            "query_presentation_hint"
        ).forEach { field ->
            assertEquals(field, listOf("NONE"), enumValues(properties, field))
        }
        assertEquals("string", properties.getJSONObject("task_text").getString("type"))
        assertEquals("string", properties.getJSONObject("reply").getString("type"))
        assertEquals(0.0, properties.getJSONObject("confidence").getDouble("minimum"), 0.0)
        assertEquals(1.0, properties.getJSONObject("confidence").getDouble("maximum"), 0.0)
        assertEquals("boolean", properties.getJSONObject("listen_again").getString("type"))
    }

    @Test
    fun exactNoContextAuthorityFailureSelectsDedicatedProfile() {
        val selected = ConversationRepairProfileSelector.select(
            failureCode = ConversationDecisionFailureCode.INVALID_CONTEXT_REF.name,
            failedRoute = ConversationRoute.CONTEXT_ACTION,
            suppliedTemporaryRefCount = 0,
            validatedFocusAvailable = false
        )

        assertEquals(ConversationRepairProfile.NO_CONTEXT_MUTATION_REPAIR, selected)
        val boundedContext = buildString {
            appendLine("Supplied temporary refs: NONE")
            appendLine("Supplied temporary ref count: 0")
            appendLine("Current validated focus ref: NONE")
            appendLine("Context action authority available: false")
            append(ConversationRepairProfileSelector.marker(selected))
        }
        assertEquals(
            ConversationRepairProfile.NO_CONTEXT_MUTATION_REPAIR,
            ConversationRepairProfileSelector.fromBoundedContext(boundedContext)
        )
    }

    @Test
    fun suppliedRefsOrValidatedFocusKeepGeneralRepairAvailable() {
        assertEquals(
            ConversationRepairProfile.GENERAL,
            ConversationRepairProfileSelector.select(
                failureCode = ConversationDecisionFailureCode.INVALID_CONTEXT_REF.name,
                failedRoute = ConversationRoute.CONTEXT_ACTION,
                suppliedTemporaryRefCount = 2,
                validatedFocusAvailable = false
            )
        )
        assertEquals(
            ConversationRepairProfile.GENERAL,
            ConversationRepairProfileSelector.select(
                failureCode = ConversationDecisionFailureCode.INVALID_CONTEXT_REF.name,
                failedRoute = ConversationRoute.CONTEXT_ACTION,
                suppliedTemporaryRefCount = 0,
                validatedFocusAvailable = true
            )
        )
    }

    @Test
    fun dedicatedRequestKindUsesDedicatedSchemaPromptAndRoutingBudget() {
        val clientSource = File(
            "src/main/java/com/example/myapplication/ai/conversation/ConversationAgentClient.kt"
        ).readText()
        val requestConfiguration = clientSource
            .substringAfter("private fun executeConversationRequest(")
            .substringBefore("private fun getEndpointUrl()")

        assertTrue(
            requestConfiguration.contains(
                "RequestKind.NO_CONTEXT_MUTATION_REPAIR ->\n" +
                    "                AgentResponseSchemas.noContextMutationRepairResponseFormat()"
            )
        )
        assertTrue(
            requestConfiguration.contains(
                "RequestKind.NO_CONTEXT_MUTATION_REPAIR ->\n" +
                    "                NO_CONTEXT_MUTATION_REPAIR_SYSTEM_PROMPT"
            )
        )
        assertTrue(
            requestConfiguration.contains(
                "RequestKind.NO_CONTEXT_MUTATION_REPAIR -> ROUTING_TEMPERATURE"
            )
        )
        assertEquals(0.0, ConversationAgentClient.ROUTING_TEMPERATURE, 0.0)
        assertTrue(ConversationAgentClient.NO_CONTEXT_MUTATION_REPAIR_MAX_TOKENS <= 160)

        val prompt = ConversationAgentClient.NO_CONTEXT_MUTATION_REPAIR_SYSTEM_PROMPT
        assertTrue(prompt.contains("Allowed routes are TASK_COMMAND and ASK_CLARIFICATION only"))
        assertTrue(prompt.contains("Therefore CONTEXT_ACTION is impossible"))
        assertTrue(prompt.contains("model-generated task_text is not trusted"))
        assertTrue(prompt.contains("Never invent T1, T2, Room IDs, task titles, or task facts"))
    }

    private fun enumValues(properties: JSONObject, field: String): List<String> {
        val values = properties.getJSONObject(field).getJSONArray("enum")
        return (0 until values.length()).map(values::getString)
    }
}
