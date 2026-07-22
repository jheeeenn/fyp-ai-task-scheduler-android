package com.example.myapplication.ai.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationDecisionParserTest {
    private val parser = ConversationDecisionParser()

    @Test
    fun acceptsValidContextReadT2Summary() {
        val decision = parser.parse(
            decisionJson(
                route = "CONTEXT_READ",
                contextRef = "T2",
                contextDetail = "SUMMARY"
            )
        )

        assertEquals(ConversationRoute.CONTEXT_READ, decision.route)
        assertEquals("T2", decision.contextRef)
        assertEquals(ConversationContextDetail.SUMMARY, decision.contextDetail)
        assertEquals("", decision.taskText)
        assertEquals("", decision.reply)
    }

    @Test
    fun acceptsValidContextActions() {
        val reschedule = parser.parse(
            decisionJson(
                route = "CONTEXT_ACTION",
                contextRef = "T2",
                contextAction = "RESCHEDULE"
            )
        )
        val update = parser.parse(
            decisionJson(
                route = "CONTEXT_ACTION",
                contextRef = "T1",
                contextAction = "UPDATE"
            )
        )

        assertEquals(ConversationContextAction.RESCHEDULE, reschedule.contextAction)
        assertEquals("T2", reschedule.contextRef)
        assertEquals(ConversationContextAction.UPDATE, update.contextAction)
        assertEquals("T1", update.contextRef)
    }

    @Test
    fun rejectsInvalidContextActionFields() {
        listOf(
            decisionJson(route = "CONTEXT_ACTION", contextAction = "UPDATE"),
            decisionJson(route = "CONTEXT_ACTION", contextRef = "T1"),
            decisionJson(route = "CONTEXT_ACTION", contextRef = "T1", contextDetail = "TIME", contextAction = "RESCHEDULE"),
            decisionJson(route = "CONTEXT_ACTION", taskText = "edit first", contextRef = "T1", contextAction = "UPDATE"),
            decisionJson(route = "CONTEXT_ACTION", reply = "Done", contextRef = "T1", contextAction = "UPDATE")
        ).forEach { invalid ->
            assertThrows(ConversationSchemaException::class.java) { parser.parse(invalid) }
        }
    }

    @Test
    fun rejectsContextReadWithEmptyRef() {
        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(
                decisionJson(
                    route = "CONTEXT_READ",
                    contextRef = "",
                    contextDetail = "SUMMARY"
                )
            )
        }
    }

    @Test
    fun rejectsContextReadWithNoneDetail() {
        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(
                decisionJson(
                    route = "CONTEXT_READ",
                    contextRef = "T2",
                    contextDetail = "NONE"
                )
            )
        }
    }

    @Test
    fun rejectsContextReadWithModelAuthoredFactualReply() {
        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(
                decisionJson(
                    route = "CONTEXT_READ",
                    reply = "The second task is groceries.",
                    contextRef = "T2",
                    contextDetail = "SUMMARY"
                )
            )
        }
    }

    @Test
    fun nonContextRoutesRequireEmptyRefAndNoneDetail() {
        listOf("TASK_COMMAND", "DIRECT_REPLY", "ASK_CLARIFICATION", "END_SESSION", "UNKNOWN")
            .forEach { route ->
                assertThrows(ConversationSchemaException::class.java) {
                    parser.parse(
                        decisionJson(
                            route = route,
                            taskText = if (route == "TASK_COMMAND") "delete medicine" else "",
                            reply = if (route == "DIRECT_REPLY") "Hello" else "",
                            contextRef = "T1",
                            contextDetail = "TITLE"
                        )
                    )
                }
            }
    }

    @Test
    fun acceptsNonContextRouteWithEmptyRefAndNoneDetail() {
        val decision = parser.parse(
            decisionJson(
                route = "TASK_COMMAND",
                taskText = "delete the medicine task"
            )
        )

        assertEquals(ConversationRoute.TASK_COMMAND, decision.route)
        assertEquals("", decision.contextRef)
        assertEquals(ConversationContextDetail.NONE, decision.contextDetail)
        assertEquals(ConversationContextAction.NONE, decision.contextAction)
    }

    @Test
    fun allNonContextActionRoutesRequireContextActionNone() {
        listOf("TASK_COMMAND", "CONTEXT_READ", "DIRECT_REPLY", "ASK_CLARIFICATION", "END_SESSION", "UNKNOWN")
            .forEach { route ->
                assertThrows(ConversationSchemaException::class.java) {
                    parser.parse(
                        decisionJson(
                            route = route,
                            contextRef = if (route == "CONTEXT_READ") "T1" else "",
                            contextDetail = if (route == "CONTEXT_READ") "TITLE" else "NONE",
                            contextAction = "UPDATE"
                        )
                    )
                }
            }
    }

    @Test
    fun continuesRejectingTaskAgentFields() {
        val withTaskAgentField = decisionJson(
            route = "TASK_COMMAND",
            taskText = "delete medicine"
        ).replace(
            "\"listen_again\":true",
            "\"listen_again\":true,\"action\":\"DELETE_TASK\""
        )

        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(withTaskAgentField)
        }
    }

    private fun decisionJson(
        route: String,
        taskText: String = "",
        reply: String = "",
        contextRef: String = "",
        contextDetail: String = "NONE",
        contextAction: String = "NONE"
    ): String = """
        {
          "route":"$route",
          "task_text":"$taskText",
          "reply":"$reply",
          "context_ref":"$contextRef",
          "context_detail":"$contextDetail",
          "context_action":"$contextAction",
          "confidence":0.97,
          "listen_again":true
        }
    """.trimIndent()
}
