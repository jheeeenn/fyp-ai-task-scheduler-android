package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.TaskQueryPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDecisionParserTest {
    private val parser = ConversationDecisionParser()

    @Test
    fun appNavigationAcceptsEveryBoundedTargetAndRejectsInvalidAuthority() {
        listOf("SETTINGS", "CREATE_TASK", "TODAY_TASKS", "SCHEDULED_TASKS").forEach { target ->
            val decision = parser.parse(
                decisionJson(
                    route = "APP_NAVIGATION",
                    navigationTarget = target,
                    listenAgain = false
                )
            )
            assertEquals(ConversationRoute.APP_NAVIGATION, decision.route)
            assertEquals(ConversationNavigationTarget.valueOf(target), decision.navigationTarget)
            assertInactiveFieldsAreCanonical(decision)
        }

        listOf(
            decisionJson(
                route = "APP_NAVIGATION",
                navigationTarget = "NONE",
                listenAgain = false
            ),
            decisionJson(
                route = "APP_NAVIGATION",
                navigationTarget = "SETTINGS",
                confidence = 0.79,
                listenAgain = false
            ),
            decisionJson(
                route = "APP_NAVIGATION",
                navigationTarget = "SETTINGS",
                listenAgain = true
            ),
            decisionJson(route = "DIRECT_REPLY", navigationTarget = "UNSUPPORTED_SCREEN")
        ).forEach { invalid ->
            assertThrows(ConversationSchemaException::class.java) { parser.parse(invalid) }
        }
    }

    @Test
    fun nonNavigationRoutesCanonicalizeNavigationTargetAndMissingFieldIsRejected() {
        val result = parser.parseWithReport(
            decisionJson(
                route = "DIRECT_REPLY",
                navigationTarget = "SETTINGS",
                reply = "Open Settings from Home."
            )
        )

        assertEquals(ConversationNavigationTarget.NONE, result.decision.navigationTarget)
        assertTrue(result.canonicalizationReport.fields.contains("navigation_target"))
        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(
                decisionJson(route = "DIRECT_REPLY")
                    .replace("  \"navigation_target\":\"NONE\",", "")
            )
        }
    }

    @Test
    fun contextActionCanonicalizesInactiveTextAndPreservesAuthority() {
        val result = parser.parseWithReport(
            decisionJson(
                route = "CONTEXT_ACTION",
                taskText = EXACT_RESCHEDULE_UTTERANCE,
                reply = "I moved the task to August first at nine.",
                contextRef = "T2",
                contextDetail = "TIME",
                contextAction = "RESCHEDULE",
                queryReadingMove = "REPEAT_LAST",
                queryPresentationHint = "OVERVIEW"
            )
        )

        val decision = result.decision
        assertEquals(ConversationRoute.CONTEXT_ACTION, decision.route)
        assertEquals("", decision.taskText)
        assertEquals("", decision.reply)
        assertEquals("T2", decision.contextRef)
        assertEquals(ConversationContextDetail.NONE, decision.contextDetail)
        assertEquals(ConversationContextAction.RESCHEDULE, decision.contextAction)
        assertEquals(ConversationQueryReadingMove.NONE, decision.queryReadingMove)
        assertEquals(TaskQueryPresentation.NONE, decision.queryPresentationHint)
        assertEquals(
            listOf(
                "task_text",
                "reply",
                "context_detail",
                "query_reading_move",
                "query_presentation_hint"
            ),
            result.canonicalizationReport.fields
        )
    }

    @Test
    fun contextActionAuthorityFieldsStillFailClosed() {
        listOf(
            decisionJson(
                route = "CONTEXT_ACTION",
                contextRef = "T2",
                contextAction = "NONE"
            ),
            decisionJson(
                route = "CONTEXT_ACTION",
                contextRef = "918273645",
                contextAction = "RESCHEDULE"
            ),
            decisionJson(
                route = "CONTEXT_ACTION",
                contextRef = "task-2",
                contextAction = "UPDATE"
            )
        ).forEach { invalid ->
            assertThrows(ConversationSchemaException::class.java) {
                parser.parse(invalid)
            }
        }

        val blankRef = parser.parse(
            decisionJson(
                route = "CONTEXT_ACTION",
                contextRef = "",
                contextAction = "MARK_DONE"
            )
        )
        assertEquals("", blankRef.contextRef)
        assertEquals(ConversationContextAction.MARK_DONE, blankRef.contextAction)
    }

    @Test
    fun contextReadCanonicalizesInactiveFieldsAndKeepsRefAndDetailStrict() {
        val result = parser.parseWithReport(
            decisionJson(
                route = "CONTEXT_READ",
                taskText = "echoed request",
                reply = "The task is at nine.",
                contextRef = "T2",
                contextDetail = "TIME",
                contextAction = "UPDATE",
                queryReadingMove = "REPEAT_LAST",
                queryPresentationHint = "DETAILS"
            )
        )

        assertEquals("", result.decision.taskText)
        assertEquals("", result.decision.reply)
        assertEquals("T2", result.decision.contextRef)
        assertEquals(ConversationContextDetail.TIME, result.decision.contextDetail)
        assertEquals(ConversationContextAction.NONE, result.decision.contextAction)
        assertEquals(ConversationQueryReadingMove.NONE, result.decision.queryReadingMove)
        assertEquals(TaskQueryPresentation.NONE, result.decision.queryPresentationHint)
        assertTrue(result.canonicalizationReport.fields.contains("reply"))

        listOf(
            decisionJson(
                route = "CONTEXT_READ",
                contextRef = "",
                contextDetail = "SUMMARY"
            ),
            decisionJson(
                route = "CONTEXT_READ",
                contextRef = "T2",
                contextDetail = "NONE"
            ),
            decisionJson(
                route = "CONTEXT_READ",
                contextRef = "27",
                contextDetail = "DATE"
            )
        ).forEach { invalid ->
            assertThrows(ConversationSchemaException::class.java) {
                parser.parse(invalid)
            }
        }
    }

    @Test
    fun dailyBriefingCanonicalizesInactiveFieldsButKeepsThresholdAndListeningStrict() {
        val decision = parser.parse(
            decisionJson(
                route = "DAILY_BRIEFING",
                taskText = "brief me",
                reply = "Here is the briefing.",
                contextRef = "T1",
                contextDetail = "TIME",
                contextAction = "UPDATE",
                queryReadingMove = "REPEAT_LAST",
                queryPresentationHint = "OVERVIEW"
            )
        )

        assertEquals(ConversationRoute.DAILY_BRIEFING, decision.route)
        assertInactiveFieldsAreCanonical(decision)
        listOf(
            decisionJson(route = "DAILY_BRIEFING", confidence = 0.79),
            decisionJson(route = "DAILY_BRIEFING", listenAgain = false)
        ).forEach { invalid ->
            assertThrows(ConversationSchemaException::class.java) {
                parser.parse(invalid)
            }
        }
    }

    @Test
    fun delegatedRoutesDiscardIrrelevantEchoedFields() {
        val delegatedRoutes = listOf(
            "SMART_ROUTINE_BUILDER",
            "SAVED_ROUTINE_ACTION",
            "CONTEXT_AWARE_SUGGESTION"
        )
        delegatedRoutes.forEach { route ->
            val decision = parser.parse(
                decisionJson(
                    route = route,
                    taskText = "model-authored task text",
                    reply = "model-authored reply",
                    contextRef = "T7",
                    contextDetail = "STATUS",
                    contextAction = "RESCHEDULE",
                    queryReadingMove = "STOP",
                    queryPresentationHint = "COUNT_ONLY"
                )
            )
            assertEquals(ConversationRoute.valueOf(route), decision.route)
            assertInactiveFieldsAreCanonical(decision)
        }
    }

    @Test
    fun taskCommandKeepsOnlyPresentationAuthorityAndClearsModelTaskText() {
        val decision = parser.parse(
            decisionJson(
                route = "TASK_COMMAND",
                taskText = "model rewrite",
                reply = "Done",
                contextRef = "T1",
                contextDetail = "TITLE",
                contextAction = "UPDATE",
                queryReadingMove = "CONTINUE",
                queryPresentationHint = "OVERVIEW",
                confidence = 0.60,
                listenAgain = false
            )
        )

        assertEquals(ConversationRoute.TASK_COMMAND, decision.route)
        assertEquals("", decision.taskText)
        assertEquals("", decision.reply)
        assertEquals("", decision.contextRef)
        assertEquals(ConversationContextDetail.NONE, decision.contextDetail)
        assertEquals(ConversationContextAction.NONE, decision.contextAction)
        assertEquals(ConversationQueryReadingMove.NONE, decision.queryReadingMove)
        assertEquals(TaskQueryPresentation.OVERVIEW, decision.queryPresentationHint)
        assertEquals(0.60, decision.confidence, 0.0)
        assertFalse(decision.listenAgain)
    }

    @Test
    fun queryReadingControlCanonicalizesInactiveFieldsButRequiresAuthorityMove() {
        val accepted = parser.parse(
            decisionJson(
                route = "QUERY_READING_CONTROL",
                taskText = "repeat",
                reply = "Repeating it.",
                contextRef = "T1",
                contextDetail = "SUMMARY",
                contextAction = "UPDATE",
                queryReadingMove = "REPEAT_LAST",
                queryPresentationHint = "DETAILS"
            )
        )

        assertEquals(ConversationQueryReadingMove.REPEAT_LAST, accepted.queryReadingMove)
        assertEquals("", accepted.taskText)
        assertEquals("", accepted.reply)
        assertEquals("", accepted.contextRef)
        assertEquals(ConversationContextDetail.NONE, accepted.contextDetail)
        assertEquals(ConversationContextAction.NONE, accepted.contextAction)
        assertEquals(TaskQueryPresentation.NONE, accepted.queryPresentationHint)

        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(decisionJson(route = "QUERY_READING_CONTROL"))
        }
    }

    @Test
    fun conversationalReplyRoutesKeepReplyButDiscardControlFields() {
        listOf("DIRECT_REPLY", "ASK_CLARIFICATION", "END_SESSION", "UNKNOWN")
            .forEach { route ->
                val decision = parser.parse(
                    decisionJson(
                        route = route,
                        taskText = "echoed task text",
                        reply = "Safe conversational reply",
                        contextRef = "T1",
                        contextDetail = "TITLE",
                        contextAction = "UPDATE",
                        queryReadingMove = "STOP",
                        queryPresentationHint = "OVERVIEW"
                    )
                )
                assertEquals("Safe conversational reply", decision.reply)
                assertEquals("", decision.taskText)
                assertEquals("", decision.contextRef)
                assertEquals(ConversationContextDetail.NONE, decision.contextDetail)
                assertEquals(ConversationContextAction.NONE, decision.contextAction)
                assertEquals(ConversationQueryReadingMove.NONE, decision.queryReadingMove)
                assertEquals(TaskQueryPresentation.NONE, decision.queryPresentationHint)
            }
    }

    @Test
    fun structuralDecoderRejectsMissingAdditionalForbiddenAndWrongTypes() {
        val valid = decisionJson(route = "DIRECT_REPLY", reply = "Hello")
        val invalid = listOf(
            valid.replace("\"reply\":\"Hello\",", ""),
            valid.replace(
                "\"listen_again\":true",
                "\"listen_again\":true,\"extra\":\"x\""
            ),
            valid.replace(
                "\"listen_again\":true",
                "\"listen_again\":true,\"action\":\"DELETE_TASK\""
            ),
            valid.replace("\"context_ref\":\"\"", "\"context_ref\":7"),
            valid.replace("\"confidence\":0.97", "\"confidence\":\"high\""),
            valid.replace("\"listen_again\":true", "\"listen_again\":\"yes\"")
        )

        invalid.forEach {
            assertThrows(ConversationSchemaException::class.java) {
                parser.parse(it)
            }
        }
    }

    @Test
    fun structuralDecoderRejectsUnknownEnumsEvenWhenFieldWouldBeInactive() {
        listOf(
            decisionJson(route = "NOT_A_ROUTE"),
            decisionJson(route = "DIRECT_REPLY", contextDetail = "WHEN"),
            decisionJson(route = "DIRECT_REPLY", contextAction = "MOVE"),
            decisionJson(route = "DIRECT_REPLY", queryReadingMove = "AGAIN"),
            decisionJson(route = "DIRECT_REPLY", queryPresentationHint = "VERBOSE")
        ).forEach { invalid ->
            assertThrows(ConversationSchemaException::class.java) {
                parser.parse(invalid)
            }
        }
    }

    @Test
    fun confidenceMustBeFiniteAndBetweenZeroAndOne() {
        listOf(-0.01, 1.01).forEach { confidence ->
            assertThrows(ConversationSchemaException::class.java) {
                parser.parse(
                    decisionJson(route = "DIRECT_REPLY", confidence = confidence)
                )
            }
        }
    }

    private fun assertInactiveFieldsAreCanonical(decision: ConversationDecision) {
        if (decision.route != ConversationRoute.APP_NAVIGATION) {
            assertEquals(ConversationNavigationTarget.NONE, decision.navigationTarget)
        }
        assertEquals("", decision.taskText)
        assertEquals("", decision.reply)
        assertEquals("", decision.contextRef)
        assertEquals(ConversationContextDetail.NONE, decision.contextDetail)
        assertEquals(ConversationContextAction.NONE, decision.contextAction)
        assertEquals(ConversationQueryReadingMove.NONE, decision.queryReadingMove)
        assertEquals(TaskQueryPresentation.NONE, decision.queryPresentationHint)
    }

    private fun decisionJson(
        route: String,
        navigationTarget: String = "NONE",
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
          "navigation_target":"$navigationTarget",
          "task_text":"$taskText",
          "reply":"$reply",
          "context_ref":"$contextRef",
          "context_detail":"$contextDetail",
          "context_action":"$contextAction",
          "setting_target":"NONE","setting_action":"NONE",
          "query_reading_move":"$queryReadingMove",
          "query_presentation_hint":"$queryPresentationHint",
          "confidence":$confidence,
          "listen_again":$listenAgain
        }
    """.trimIndent()

    private companion object {
        const val EXACT_RESCHEDULE_UTTERANCE =
            "move the second one to 1st of August 2026 at 9:00 a.m."
    }
}
