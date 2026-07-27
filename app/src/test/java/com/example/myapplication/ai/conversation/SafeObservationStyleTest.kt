package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SafeObservationStyleTest {
    private val parser = SafeObservationStyleParser()

    @Test
    fun styleSchemaIsStrictAndContainsExactlyFourFields() {
        val format = AgentResponseSchemas.safeObservationStyleResponseFormat()
        val schema = format.getJSONObject("json_schema").getJSONObject("schema")
        assertEquals(
            setOf("use_style", "lead_in", "bridge", "confidence"),
            schema.getJSONObject("properties").keys().asSequence().toSet()
        )
        assertFalse(schema.getBoolean("additionalProperties"))
        assertEquals(4, schema.getJSONArray("required").length())
    }

    @Test
    fun parserRejectsAdditionalAndMissingFields() {
        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(
                """{"use_style":true,"lead_in":"","bridge":"","confidence":0.9,"speech":"unsafe"}"""
            )
        }
        assertThrows(ConversationSchemaException::class.java) {
            parser.parse("""{"use_style":true,"lead_in":"","confidence":0.9}""")
        }
    }

    @Test
    fun validatorFailsClosedForLowConfidenceDigitsDatesAndTimes() {
        assertRejected("Certainly.", "", confidence = 0.84)
        assertRejected("Here are 2 items.", "")
        assertRejected("For 20/07/2026.", "")
        assertRejected("Ready at 08:30.", "")
        assertRejected("Ready at 8 PM.", "")
    }

    @Test
    fun validatorRejectsOperationalClaimsAndReadingControls() {
        listOf(
            "It was saved.",
            "Everything was completed.",
            "I found them.",
            "Please continue.",
            "You can repeat.",
            "Say stop.",
            "Confirm when ready.",
            "Here is the first group."
        ).forEach { assertRejected(it, "") }
    }

    @Test
    fun validatorAcceptsGenericConversationalFragments() {
        assertTrue(
            SafeObservationStyleValidator.isValid(
                SafeObservationStyleEnvelope(
                    useStyle = true,
                    leadIn = "Certainly — here is the overview.",
                    bridge = "Take your time.",
                    confidence = 0.95
                )
            )
        )
    }

    @Test
    fun safeStyleContextContainsOnlyNonFactualClassifications() {
        val plan = queryPlan()
        val input = plan.styleContext.toSafeJson()
        val json = JSONObject(input)

        assertEquals(
            setOf(
                "operation",
                "presentation",
                "page_role",
                "tone",
                "continued_interaction_expected",
                "control_category"
            ),
            json.keys().asSequence().toSet()
        )
        listOf(
            "tasks",
            "title",
            "due",
            "date",
            "time",
            "count",
            "temporal",
            "speech",
            "core",
            "room"
        ).forEach { forbidden ->
            assertFalse("Unexpected '$forbidden' in $input", input.lowercase().contains(forbidden))
        }
        assertFalse(json.has("id"))
        assertFalse(json.has("room_id"))
    }

    @Test
    fun styleClientReceivesOnlyTheSafeContextJson() = runBlocking {
        val plan = queryPlan()
        val client = FakeStyleClient(
            Result.success("""{"use_style":false,"lead_in":"","bridge":"","confidence":0.99}""")
        )

        orchestrator(client).styleTaskQuerySpeech(plan, true)

        assertEquals(plan.styleContext.toSafeJson(), client.lastStyleInput)
        assertFalse(client.lastStyleInput.orEmpty().contains("Alpha"))
        assertFalse(client.lastStyleInput.orEmpty().contains("20/07/2026"))
        assertFalse(client.lastStyleInput.orEmpty().contains(plan.deterministicSpeech))
    }

    @Test
    fun acceptedCompositionPreservesCoreControlOrderAndTaskCoverage() {
        val plan = queryPlan()
        val speech = SafeTaskQuerySpeechComposer.compose(
            plan,
            SafeObservationStyleEnvelope(
                true,
                "Certainly — here is the overview.",
                "Take your time.",
                0.96
            )
        )

        assertEquals(1, occurrences(speech, plan.authoritativeCore))
        assertEquals(1, occurrences(speech, plan.authoritativeControl))
        assertTrue(speech.indexOf(plan.authoritativeCore) < speech.indexOf(plan.authoritativeControl))
        assertTrue(speech.indexOf("Alpha") < speech.indexOf("Beta"))
        assertEquals(1, occurrences(speech, "Alpha"))
        assertEquals(1, occurrences(speech, "Beta"))
    }

    @Test
    fun invalidAndTimeoutStyleFallBackWithoutRetry() = runBlocking {
        val invalidClient = FakeStyleClient(
            Result.success("""{"use_style":true,"lead_in":"Please continue.","bridge":"","confidence":0.99}""")
        )
        val timeoutClient = FakeStyleClient(Result.failure(IOException("request timed out")))
        val plan = queryPlan()

        val invalid = orchestrator(invalidClient).styleTaskQuerySpeech(plan, true)
        val timeout = orchestrator(timeoutClient).styleTaskQuerySpeech(plan, true)

        assertEquals(plan.deterministicSpeech, invalid.speech)
        assertEquals("android_deterministic", invalid.source)
        assertEquals(plan.deterministicSpeech, timeout.speech)
        assertEquals("android_deterministic", timeout.source)
        assertEquals(1, invalidClient.styleCalls)
        assertEquals(1, timeoutClient.styleCalls)
    }

    @Test
    fun acceptedStyleUsesHybridSourceAndOneAttempt() = runBlocking {
        val client = FakeStyleClient(
            Result.success(
                """{"use_style":true,"lead_in":"Certainly.","bridge":"Take your time.","confidence":0.95}"""
            )
        )
        val plan = queryPlan()
        val response = orchestrator(client).styleTaskQuerySpeech(plan, true)

        assertEquals("android_hybrid_safe", response.source)
        assertEquals(
            "Certainly. ${plan.authoritativeCore} Take your time. ${plan.authoritativeControl}",
            response.speech
        )
        assertEquals(1, client.styleCalls)
    }

    @Test
    fun exhaustedBudgetSkipsStyleCall() = runBlocking {
        val client = FakeStyleClient(
            Result.success("""{"use_style":true,"lead_in":"Certainly.","bridge":"","confidence":0.95}""")
        )
        val plan = queryPlan()
        val response = orchestrator(client).styleTaskQuerySpeech(plan, false)

        assertEquals(plan.deterministicSpeech, response.speech)
        assertEquals(0, client.styleCalls)
    }

    @Test
    fun firstPassRoutingAndStyleStayWithinTwoConversationCalls() = runBlocking {
        val client = BudgetClient(primaryResult = validRoutingDecision())
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val decision = orchestrator.process("show my work", "app")
        val response = orchestrator.styleTaskQuerySpeech(queryPlan(), decision.source == "conversation_agent")

        assertEquals("android_hybrid_safe", response.source)
        assertEquals(1, client.routingCalls)
        assertEquals(0, client.repairCalls)
        assertEquals(1, client.styleCalls)
        assertEquals(2, client.totalCalls)
    }

    @Test
    fun schemaRepairedRoutingSkipsStyleAndStillUsesAtMostTwoCalls() = runBlocking {
        val client = BudgetClient(primaryResult = "{}")
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val decision = orchestrator.process("show my work", "app")
        val response = orchestrator.styleTaskQuerySpeech(queryPlan(), decision.source == "conversation_agent")

        assertEquals("conversation_agent_schema_repair", decision.source)
        assertEquals("android_deterministic", response.source)
        assertEquals(1, client.routingCalls)
        assertEquals(1, client.repairCalls)
        assertEquals(0, client.styleCalls)
        assertEquals(2, client.totalCalls)
    }

    @Test
    fun styleAttemptDoesNotRecordCandidateAndFinalSpeechRecordsOnce() = runBlocking {
        val memory = ConversationSessionMemory()
        val client = FakeStyleClient(
            Result.success(
                """{"use_style":true,"lead_in":"Certainly.","bridge":"","confidence":0.95}"""
            )
        )
        val orchestrator = ConversationOrchestrator(
            client,
            ConversationDecisionParser(),
            memory = memory
        )
        val observation = queryObservation()
        val response = orchestrator.styleTaskQuerySpeech(queryPlan(), true)
        assertFalse(memory.snapshotForPrompt().contains("Certainly."))

        orchestrator.recordDeterministicObservation(observation, response)
        val snapshot = memory.snapshotForPrompt()
        assertEquals(1, occurrences(snapshot, "Assistant: ${response.speech}"))
        assertFalse(snapshot.contains("lead_in"))
        assertFalse(snapshot.contains("confidence"))
    }

    @Test
    fun rejectedStyleCandidateIsNeverRecorded() = runBlocking {
        val memory = ConversationSessionMemory()
        val client = FakeStyleClient(
            Result.success(
                """{"use_style":true,"lead_in":"Please continue.","bridge":"","confidence":0.99}"""
            )
        )
        val orchestrator = ConversationOrchestrator(
            client,
            ConversationDecisionParser(),
            memory = memory
        )
        val observation = queryObservation()
        val response = orchestrator.styleTaskQuerySpeech(queryPlan(), true)
        orchestrator.recordDeterministicObservation(observation, response)

        assertEquals("android_deterministic", response.source)
        assertFalse(memory.snapshotForPrompt().contains("Please continue."))
        assertEquals(1, occurrences(memory.snapshotForPrompt(), "Assistant: ${response.speech}"))
    }

    @Test
    fun eligibilityIsLimitedToInformationalTaskQueryPages() {
        val eligible = queryObservation()
        assertTrue(AndroidObservationResponseRenderer.taskQueryPlanOrNull(eligible) != null)
        assertTrue(
            AndroidObservationResponseRenderer.taskQueryPlanOrNull(
                eligible.copy(outcome = ExecutionOutcome.NO_RESULTS, queryPage = null)
            ) == null
        )
        assertTrue(
            AndroidObservationResponseRenderer.taskQueryPlanOrNull(
                eligible.copy(operation = ExecutionOperation.UPDATE_TASK)
            ) == null
        )
    }

    @Test
    fun countOnlyUsesSingularAndPluralPronouns() {
        val one = countObservation(1)
        val many = countObservation(2)
        assertTrue(AndroidObservationResponseRenderer.render(one).speech.endsWith("read it?"))
        assertTrue(AndroidObservationResponseRenderer.render(many).speech.endsWith("read them?"))
    }

    private fun assertRejected(leadIn: String, bridge: String, confidence: Double = 0.95) {
        assertFalse(
            SafeObservationStyleValidator.isValid(
                SafeObservationStyleEnvelope(true, leadIn, bridge, confidence)
            )
        )
    }

    private fun queryPlan(): TaskQuerySpeechPlan =
        requireNotNull(AndroidObservationResponseRenderer.taskQueryPlanOrNull(queryObservation()))

    private fun queryObservation() = ExecutionObservation(
        operation = ExecutionOperation.QUERY_TASK,
        outcome = ExecutionOutcome.INFORMATION,
        tasks = listOf(
            ObservedTask(title = "Alpha", dueDate = "20/07/2026", dueTime = "08:00"),
            ObservedTask(title = "Beta", dueDate = "21/07/2026", dueTime = "09:00")
        ),
        queryPage = TaskQueryPageObservation(
            totalTaskCount = 2,
            pageStartPosition = 1,
            pageEndPosition = 2,
            pageNumber = 1,
            pageCount = 1,
            pageSize = 5,
            hasNextPage = false,
            presentation = TaskQueryPresentationLevel.OVERVIEW,
            detailLevel = TaskQuerySpeechDetail.BRIEF,
            tone = TaskQuerySpeechTone.FRIENDLY,
            includeTaskDates = true,
            temporalLabel = "this week"
        ),
        listenAgain = true,
        fallbackSpeech = ""
    )

    private fun countObservation(count: Int) = ExecutionObservation(
        operation = ExecutionOperation.QUERY_TASK,
        outcome = ExecutionOutcome.INFORMATION,
        queryPage = TaskQueryPageObservation(
            totalTaskCount = count,
            pageStartPosition = 0,
            pageEndPosition = 0,
            pageNumber = 0,
            pageCount = 1,
            pageSize = 5,
            hasNextPage = false,
            presentation = TaskQueryPresentationLevel.COUNT_ONLY,
            detailLevel = TaskQuerySpeechDetail.BRIEF,
            tone = TaskQuerySpeechTone.NEUTRAL,
            includeTaskDates = false,
            temporalLabel = "today"
        ),
        listenAgain = true,
        fallbackSpeech = ""
    )

    private fun occurrences(text: String, value: String): Int =
        text.windowed(value.length).count { it == value }

    private fun orchestrator(client: FakeStyleClient) =
        ConversationOrchestrator(client, ConversationDecisionParser())

    private class FakeStyleClient(
        private val styleResult: Result<String>
    ) : ConversationAgentClient(null) {
        var styleCalls = 0
        var lastStyleInput: String? = null

        override suspend fun requestSafeObservationStyle(styleContextJson: String): String {
            styleCalls += 1
            lastStyleInput = styleContextJson
            return styleResult.getOrThrow()
        }
    }

    private class BudgetClient(
        private val primaryResult: String
    ) : ConversationAgentClient(null) {
        var routingCalls = 0
        var repairCalls = 0
        var styleCalls = 0
        val totalCalls get() = routingCalls + repairCalls + styleCalls

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            routingCalls += 1
            return primaryResult
        }

        override suspend fun processRepair(userText: String, appContextSummary: String): String {
            repairCalls += 1
            return validRoutingDecision()
        }

        override suspend fun requestSafeObservationStyle(styleContextJson: String): String {
            styleCalls += 1
            return """{"use_style":true,"lead_in":"Certainly.","bridge":"","confidence":0.95}"""
        }
    }

    companion object {
        private fun validRoutingDecision() =
            """{"route":"TASK_COMMAND","task_text":"show my work","reply":"","context_ref":"","context_detail":"NONE","context_action":"NONE","query_reading_move":"NONE","query_presentation_hint":"OVERVIEW","confidence":0.98,"listen_again":true}"""
    }
}
