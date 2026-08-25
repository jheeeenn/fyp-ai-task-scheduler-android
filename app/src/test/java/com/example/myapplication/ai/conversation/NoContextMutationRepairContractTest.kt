package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoContextMutationRepairContractTest {
    private val parser = NoContextMutationRepairParser()

    @Test
    fun dedicatedSchemaContainsOnlyCompactSemanticFields() {
        val format = AgentResponseSchemas.noContextMutationRepairResponseFormat()
        val jsonSchema = format.getJSONObject("json_schema")
        val objectSchema = jsonSchema.getJSONObject("schema")
        val properties = objectSchema.getJSONObject("properties")
        val fields = properties.keys().asSequence().toSet()
        val required = objectSchema.getJSONArray("required")
        val requiredFields = (0 until required.length()).map(required::getString).toSet()

        assertEquals("no_context_mutation_repair", jsonSchema.getString("name"))
        assertEquals(setOf("move", "reply", "confidence"), fields)
        assertEquals(setOf("move", "reply", "confidence"), requiredFields)
        assertEquals(
            listOf("TASK_COMMAND", "ASK_CLARIFICATION"),
            enumValues(properties, "move")
        )
        listOf(
            "task_text",
            "context_ref",
            "context_detail",
            "context_action",
            "setting_action",
            "setting_target",
            "query_reading_move",
            "query_presentation_hint",
            "listen_again",
            "target_task_title"
        ).forEach { forbiddenField ->
            assertFalse(properties.has(forbiddenField))
        }
    }

    @Test
    fun compactParserAcceptsTaskCommand() {
        val parsed = parser.parse(
            """{"move":"TASK_COMMAND","reply":"","confidence":0.97}"""
        )

        assertEquals(NoContextMutationRepairMove.TASK_COMMAND, parsed.move)
        assertEquals("", parsed.reply)
        assertEquals(0.97, parsed.confidence, 0.0)
    }

    @Test
    fun compactParserAcceptsClarification() {
        val parsed = parser.parse(
            """{"move":"ASK_CLARIFICATION","reply":"Which task do you want to reschedule?","confidence":0.97}"""
        )

        assertEquals(NoContextMutationRepairMove.ASK_CLARIFICATION, parsed.move)
        assertEquals("Which task do you want to reschedule?", parsed.reply)
        assertEquals(0.97, parsed.confidence, 0.0)
    }

    @Test
    fun compactParserRejectsMalformedIncompleteAndUnknownMoves() {
        assertRejected("not json", ConversationDecisionFailureCode.INVALID_JSON)
        assertRejected(
            """{"move":"TASK_COMMAND","reply":"","confidence":0.97} trailing""",
            ConversationDecisionFailureCode.INVALID_JSON
        )
        assertRejected(
            """{"move":"TASK_COMMAND","reply":"","confidence":0.97""",
            ConversationDecisionFailureCode.INCOMPLETE_JSON
        )
        assertRejected(
            """{"move":"CONTEXT_ACTION","reply":"","confidence":0.97}""",
            ConversationDecisionFailureCode.UNKNOWN_ENUM_VALUE
        )
    }

    @Test
    fun compactParserRejectsAuthorityTaskFieldsAndUnsafeReplyContracts() {
        listOf(
            "context_ref" to "T1",
            "task_text" to "delete groceries",
            "target_task_title" to "groceries"
        ).forEach { (field, value) ->
            assertRejected(
                JSONObject()
                    .put("move", "TASK_COMMAND")
                    .put("reply", "")
                    .put("confidence", 0.97)
                    .put(field, value)
                    .toString(),
                ConversationDecisionFailureCode.ADDITIONAL_FIELDS
            )
        }
        assertRejected(
            """{"move":"TASK_COMMAND","reply":"Done","confidence":0.97}""",
            ConversationDecisionFailureCode.FORBIDDEN_FIELD
        )
        assertRejected(
            """{"move":"ASK_CLARIFICATION","reply":"","confidence":0.97}""",
            ConversationDecisionFailureCode.MISSING_FIELDS
        )
        assertRejected(
            """{"move":"TASK_COMMAND","reply":"","confidence":0.79}""",
            ConversationDecisionFailureCode.LOW_CONFIDENCE
        )
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
    fun dedicatedRequestKindUsesCompactSchemaPromptAndDiagnostics() {
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
        assertTrue(requestConfiguration.contains("finishReason=${'$'}finishReason"))
        assertTrue(requestConfiguration.contains("responseChars=${'$'}{body.length}"))
        assertTrue(requestConfiguration.contains("contentChars=${'$'}{content.length}"))

        val prompt = ConversationAgentClient.NO_CONTEXT_MUTATION_REPAIR_SYSTEM_PROMPT
        assertTrue(prompt.contains("Return only the compact JSON fields move, reply, and confidence"))
        assertTrue(prompt.contains("Android has already proven CONTEXT_ACTION is impossible"))
        assertTrue(prompt.contains("Never invent a task title, temporary ref, Room ID, task fact"))
        assertFalse(prompt.contains("twelve-field"))
    }

    private fun assertRejected(
        content: String,
        expectedCode: ConversationDecisionFailureCode
    ) {
        val failure = runCatching { parser.parse(content) }.exceptionOrNull()
        assertTrue(failure is ConversationSchemaException)
        assertEquals(expectedCode, (failure as ConversationSchemaException).decisionFailureCode)
    }

    private fun enumValues(properties: JSONObject, field: String): List<String> {
        val values = properties.getJSONObject(field).getJSONArray("enum")
        return (0 until values.length()).map(values::getString)
    }
}
