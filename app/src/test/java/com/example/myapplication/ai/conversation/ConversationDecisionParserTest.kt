package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.TaskQueryPresentation
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
    fun acceptsStrictDailyBriefingRoute() {
        val decision = parser.parse(decisionJson(route = "DAILY_BRIEFING"))

        assertEquals(ConversationRoute.DAILY_BRIEFING, decision.route)
        assertEquals("", decision.taskText)
        assertEquals("", decision.reply)
        assertEquals("", decision.contextRef)
        assertEquals(ConversationContextDetail.NONE, decision.contextDetail)
        assertEquals(ConversationContextAction.NONE, decision.contextAction)
        assertEquals(ConversationQueryReadingMove.NONE, decision.queryReadingMove)
        assertEquals(TaskQueryPresentation.NONE, decision.queryPresentationHint)
        assertEquals(true, decision.listenAgain)
    }

    @Test
    fun dailyBriefingRequiresEmptyFieldsAcceptedConfidenceAndListenAgain() {
        listOf(
            decisionJson(route = "DAILY_BRIEFING", taskText = "brief me"),
            decisionJson(route = "DAILY_BRIEFING", reply = "Here is the briefing"),
            decisionJson(route = "DAILY_BRIEFING", contextRef = "T1"),
            decisionJson(route = "DAILY_BRIEFING", contextDetail = "TIME"),
            decisionJson(route = "DAILY_BRIEFING", contextAction = "UPDATE"),
            decisionJson(route = "DAILY_BRIEFING", queryReadingMove = "REPEAT_LAST"),
            decisionJson(route = "DAILY_BRIEFING", queryPresentationHint = "OVERVIEW"),
            decisionJson(route = "DAILY_BRIEFING", confidence = 0.79),
            decisionJson(route = "DAILY_BRIEFING", listenAgain = false)
        ).forEach { invalid ->
            assertThrows(ConversationSchemaException::class.java) { parser.parse(invalid) }
        }
    }

    @Test
    fun dailyBriefingOnlyConstraintsDoNotLeakIntoOtherRoutes() {
        val task = parser.parse(
            decisionJson(
                route = "TASK_COMMAND",
                taskText = "show all my tasks today",
                queryPresentationHint = "OVERVIEW",
                confidence = 0.60,
                listenAgain = false
            )
        )

        assertEquals(ConversationRoute.TASK_COMMAND, task.route)
        assertEquals(0.60, task.confidence, 0.0)
        assertEquals(false, task.listenAgain)
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
                taskText = "what do I have tomorrow",
                queryPresentationHint = "OVERVIEW"
            )
        )

        assertEquals(ConversationRoute.TASK_COMMAND, decision.route)
        assertEquals(TaskQueryPresentation.OVERVIEW, decision.queryPresentationHint)
        assertEquals("", decision.contextRef)
        assertEquals(ConversationContextDetail.NONE, decision.contextDetail)
        assertEquals(ConversationContextAction.NONE, decision.contextAction)
    }

    @Test
    fun queryReadingControlRequiresNonNoneMoveAndEmptyFactualFields() {
        val accepted = parser.parse(
            decisionJson(
                route = "QUERY_READING_CONTROL",
                queryReadingMove = "REPEAT_LAST"
            )
        )

        assertEquals(ConversationRoute.QUERY_READING_CONTROL, accepted.route)
        assertEquals(ConversationQueryReadingMove.REPEAT_LAST, accepted.queryReadingMove)

        listOf(
            decisionJson(route = "QUERY_READING_CONTROL"),
            decisionJson(route = "QUERY_READING_CONTROL", queryReadingMove = "REPEAT_LAST", reply = "Repeating it."),
            decisionJson(route = "QUERY_READING_CONTROL", queryReadingMove = "REPEAT_LAST", taskText = "repeat"),
            decisionJson(route = "QUERY_READING_CONTROL", queryReadingMove = "REPEAT_LAST", contextRef = "T1")
        ).forEach { invalid ->
            assertThrows(ConversationSchemaException::class.java) { parser.parse(invalid) }
        }
    }

    @Test
    fun routesOtherThanQueryReadingControlRequireMoveNone() {
        listOf("TASK_COMMAND", "CONTEXT_READ", "CONTEXT_ACTION", "DIRECT_REPLY", "ASK_CLARIFICATION", "END_SESSION", "UNKNOWN")
            .forEach { route ->
                assertThrows(ConversationSchemaException::class.java) {
                    parser.parse(
                        decisionJson(
                            route = route,
                            taskText = if (route == "TASK_COMMAND") "show tasks" else "",
                            reply = if (route == "DIRECT_REPLY") "Hello" else "",
                            contextRef = when (route) {
                                "CONTEXT_READ", "CONTEXT_ACTION" -> "T1"
                                else -> ""
                            },
                            contextDetail = if (route == "CONTEXT_READ") "TITLE" else "NONE",
                            contextAction = if (route == "CONTEXT_ACTION") "UPDATE" else "NONE",
                            queryReadingMove = "REPEAT_LAST"
                        )
                    )
                }
            }
    }

    @Test
    fun routesOtherThanTaskCommandRequirePresentationHintNone() {
        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(
                decisionJson(
                    route = "DIRECT_REPLY",
                    reply = "Hello",
                    queryPresentationHint = "COUNT_ONLY"
                )
            )
        }
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
        contextAction: String = "NONE",
        queryReadingMove: String = "NONE",
        queryPresentationHint: String = "NONE",
        confidence: Double = 0.97,
        listenAgain: Boolean = true
    ): String = """
        {
          "route":"$route",
          "task_text":"$taskText",
          "reply":"$reply",
          "context_ref":"$contextRef",
          "context_detail":"$contextDetail",
          "context_action":"$contextAction",
          "query_reading_move":"$queryReadingMove",
          "query_presentation_hint":"$queryPresentationHint",
          "confidence":$confidence,
          "listen_again":$listenAgain
        }
    """.trimIndent()
}
