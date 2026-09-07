package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalResolutionType
import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftMoveInterpreter
import com.example.myapplication.voice.CreateDraftReadTarget
import com.example.myapplication.voice.CreateTaskDialogState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftSemanticOrchestratorTest {
    private class FakeClient(private val response: String) : CreateDraftSemanticClient {
        var calls = 0
        var lastContext = ""

        override suspend fun interpretCreateDraftMove(userText: String, contextSummary: String): String {
            calls++
            lastContext = contextSummary
            return response
        }
    }

    private class SequentialFakeClient(vararg responses: String) : CreateDraftSemanticClient {
        private val responses = responses.toList()
        val contexts = mutableListOf<String>()
        var calls = 0

        override suspend fun interpretCreateDraftMove(userText: String, contextSummary: String): String {
            contexts += contextSummary
            val response = responses.getOrNull(calls)
                ?: error("Unexpected semantic call ${calls + 1}")
            calls++
            return response
        }
    }

    @Test
    fun recognisedLocalTitleChangeStillCallsAgentAndCandidateIsAdvisoryOnly() = runBlocking {
        val client = FakeClient(
            json("CHANGE_FIELD", field = "TITLE", value = "revision", confidence = 0.97)
        )
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        assertTrue(result.agentAttempted)
        assertEquals(1, client.calls)
        assertTrue(client.lastContext.contains("Advisory local candidate move: CHANGE_FIELD"))
        assertTrue(client.lastContext.contains("Advisory local candidate field: TITLE"))
        assertTrue(client.lastContext.contains("Advisory local candidate has a value: true"))
        assertFalse(client.lastContext.contains("revision"))
    }

    @Test
    fun validAgentDecisionIsPrimaryAndMayCorrectRecognisedCandidate() = runBlocking {
        val client = FakeClient(
            json("CHANGE_FIELD", field = "TIME", value = "10 AM", confidence = 0.96)
        )
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 AM"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun agentMayCorrectLocalUnknown() = runBlocking {
        val client = FakeClient(
            json("CHANGE_FIELD", field = "TIME", value = "10 AM", confidence = 0.97)
        )
        val result = resolve(client, "move the time to 10 am", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 AM"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        assertTrue(client.lastContext.contains("Advisory local candidate recognised: false"))
    }

    @Test
    fun punctuatedAgentTimeRemainsAcceptedAndResolvesExactlyInAndroid() = runBlocking {
        val client = FakeClient(
            json("PROVIDE_FIELD", field = "TIME", value = "9:00 a.m.")
        )
        val result = resolve(client, "9:00 am", CreateTaskDialogState.WAITING_FOR_TIME)

        assertEquals(
            CreateDraftMove.ProvideField(CreateDraftField.TIME, "9:00 a.m."),
            result.move
        )
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)

        val candidate = (result.move as CreateDraftMove.ProvideField).value
        val temporal = TemporalExpressionResolver().resolve(null, candidate, candidate)
        assertEquals(TemporalResolutionType.EXACT_TIME, temporal.type)
        assertEquals(540, temporal.startMinuteInclusive)
    }

    @Test
    fun agentUnknownFallsBackToLocalConfirmSave() = runBlocking {
        val client = FakeClient(validUnknown())
        val result = resolve(client, "yes", saveState)

        assertEquals(CreateDraftMove.ConfirmSave, result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
        assertFalse(result.source == CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY)
        assertEquals(1, client.calls)
    }

    @Test
    fun agentUnknownFallsBackToLocalChangeField() = runBlocking {
        val client = FakeClient(validUnknown())
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"), result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun agentUnknownWithLocalUnknownIsDeterministicUnknown() = runBlocking {
        val client = SequentialFakeClient(validUnknown(), validUnknown())
        val result = resolve(client, "unclear words", saveState)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertFalse(result.source == CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY)
        assertEquals(2, client.calls)
    }

    @Test
    fun doubleAbstentionCanBeRepairedAsConfirmSave() = runBlocking {
        val client = SequentialFakeClient(validUnknown(), validConfirm())
        val result = resolve(client, "yeah yes", saveState)

        assertEquals(CreateDraftMove.ConfirmSave, result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_REPAIR, result.source)
        assertEquals(2, client.calls)
        assertTrue(client.contexts[1].contains("Previous assistant act: ASKED_TO_CONFIRM_SAVE"))
        assertTrue(client.contexts[1].contains("Expected response kind: CONFIRM_REJECT_OR_CORRECT"))
        assertTrue(client.contexts[1].contains("Semantic repair status: PRIMARY_AND_LOCAL_ABSTAINED"))
        assertTrue(client.contexts[1].contains("no valid deterministic proposal exists"))
    }

    @Test
    fun doubleAbstentionCanBeRepairedAsAllowedTimeChange() = runBlocking {
        val client = SequentialFakeClient(
            validUnknown(),
            json("CHANGE_FIELD", field = "TIME", value = "10 AM", confidence = 0.96)
        )
        val result = resolve(client, "move the time over", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 AM"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_REPAIR, result.source)
        assertEquals(2, client.calls)
    }

    @Test
    fun lowConfidenceRepairBecomesDeterministicUnknown() = runBlocking {
        val client = SequentialFakeClient(
            validUnknown(),
            json("CHANGE_FIELD", field = "TIME", value = "10 AM", confidence = 0.50)
        )
        val result = resolve(client, "unclear words", saveState)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(2, client.calls)
    }

    @Test
    fun stateInvalidRepairBecomesDeterministicUnknown() = runBlocking {
        val client = SequentialFakeClient(
            validUnknown(),
            json("PROVIDE_FIELD", field = "TITLE", value = "revision", confidence = 0.96)
        )
        val state = CreateTaskDialogState.WAITING_FOR_TIME
        val candidate = CreateDraftMove.Unknown
        val orchestrator = CreateDraftSemanticOrchestrator(
            localInterpreter = CreateDraftMoveInterpreter(),
            semanticClient = client
        )
        val result = orchestrator.resolve(
            "unclear words",
            state,
            context(state, candidate),
            candidate
        )

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(2, client.calls)
    }

    @Test
    fun repairConfirmCannotContradictClearButStateInvalidLocalRejection() = runBlocking {
        val client = SequentialFakeClient(validUnknown(), validConfirm())
        val state = CreateTaskDialogState.WAITING_FOR_TIME
        val candidate = CreateDraftMove.RejectSave
        val orchestrator = CreateDraftSemanticOrchestrator(
            localInterpreter = CreateDraftMoveInterpreter(),
            semanticClient = client
        )
        val result = orchestrator.resolve("no", state, context(state, candidate), candidate)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(2, client.calls)
    }

    @Test
    fun agentConfirmCannotContradictLocalRejection() = runBlocking {
        val client = FakeClient(validConfirm())
        val result = resolve(client, "no", saveState)

        assertEquals(CreateDraftMove.RejectSave, result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
    }

    @Test
    fun agentConfirmCannotContradictLocalFieldChange() = runBlocking {
        val client = FakeClient(validConfirm())
        val result = resolve(client, "change the time to 10 am", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 am"), result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
    }

    @Test
    fun agentConfirmMayResolveLocalUnknown() = runBlocking {
        val client = FakeClient(validConfirm())
        val result = resolve(client, "that looks right save it", saveState)

        assertEquals(CreateDraftMove.ConfirmSave, result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
    }

    @Test
    fun malformedAgentOutputUsesValidLocalFallback() = runBlocking {
        val client = FakeClient("not json")
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"), result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
        assertTrue(result.agentAttempted)
        assertEquals(1, client.calls)
    }

    @Test
    fun lowConfidenceAgentOutputUsesValidLocalFallback() = runBlocking {
        val client = FakeClient(
            json("CHANGE_FIELD", field = "TIME", value = "10 AM", confidence = 0.50)
        )
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"), result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
    }

    @Test
    fun malformedAgentOutputWithoutValidLocalCandidateBecomesUnknown() = runBlocking {
        val client = FakeClient("not json")
        val result = resolve(client, "unclear words", saveState)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun stateValidationRejectsAgentDecisionWhenLocalCandidateIsAlsoInvalid() = runBlocking {
        val state = CreateTaskDialogState.WAITING_FOR_TIME
        val client = FakeClient(
            json("PROVIDE_FIELD", field = "TITLE", value = "revision", confidence = 0.96)
        )
        val result = resolve(client, "", state)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun exactCancelUsesImmediateSafetyReflexWithoutAgent() = runBlocking {
        val client = FakeClient("unused")
        val result = resolve(client, "cancel", saveState)

        assertEquals(CreateDraftMove.Cancel, result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_SAFETY_REFLEX, result.source)
        assertFalse(result.agentAttempted)
        assertEquals(0, client.calls)
    }

    @Test
    fun readyToSaveDoesNotCallAgent() = runBlocking {
        val client = FakeClient("unused")
        val result = resolve(client, "yes", CreateTaskDialogState.READY_TO_SAVE)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(0, client.calls)
    }

    @Test
    fun coroutineCancellationIsPreserved() {
        val client = CreateDraftSemanticClient { _, _ -> throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                resolve(client, "unclear words", saveState)
            }
        }
    }

    @Test
    fun cancellationDuringRepairIsPreserved() {
        var calls = 0
        val client = CreateDraftSemanticClient { _, _ ->
            calls++
            if (calls == 1) validUnknown() else throw CancellationException("repair cancelled")
        }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                resolve(client, "unclear words", saveState)
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun semanticCallCountNeverExceedsTwo() = runBlocking {
        val client = SequentialFakeClient(validUnknown(), validUnknown(), validConfirm())
        val result = resolve(client, "unclear words", saveState)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(2, client.calls)
    }

    @Test
    fun readRequestsSelectOnlyAndroidDraftTargetsAcrossPendingStates() = runBlocking {
        val cases = listOf(
            Triple("What is the title?", "READ_TITLE", CreateDraftReadTarget.TITLE),
            Triple("What time is it currently set for?", "READ_TIME", CreateDraftReadTarget.TIME),
            Triple("When is this task scheduled?", "READ_SCHEDULE", CreateDraftReadTarget.SCHEDULE)
        )
        cases.forEach { (input, move, target) ->
            val result = resolve(FakeClient(json(move)), input, saveState)
            assertEquals(CreateDraftMove.ReadDraft(target), result.move)
            assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        }

        val collectingDate = resolve(
            FakeClient(json("READ_TITLE")),
            "What title did I set?",
            CreateTaskDialogState.WAITING_FOR_DATE
        )
        assertEquals(CreateDraftMove.ReadDraft(CreateDraftReadTarget.TITLE), collectingDate.move)
    }

    @Test
    fun combinedScheduleMeaningPreservesSeparateLiteralCandidates() = runBlocking {
        val initial = resolve(
            FakeClient(json("PROVIDE_SCHEDULE", date = "Sunday", time = "9 PM")),
            "Sunday at 9 PM",
            CreateTaskDialogState.WAITING_FOR_DATE
        )
        assertEquals(CreateDraftMove.ProvideSchedule("Sunday", "9 PM"), initial.move)

        val correction = resolve(
            FakeClient(json("PROVIDE_SCHEDULE", date = "Sunday", time = "9 PM")),
            "Actually make it Sunday at 9 PM instead",
            saveState
        )
        assertEquals(CreateDraftMove.ProvideSchedule("Sunday", "9 PM"), correction.move)
        assertFalse(correction.move == CreateDraftMove.RejectSave)
    }

    @Test
    fun fieldRequestAndSuppliedValueRemainDistinctAtSaveConfirmation() = runBlocking {
        val cases = listOf(
            Pair(
                "Change the time",
                CreateDraftMove.ChangeField(CreateDraftField.TIME)
            ) to json("CHANGE_FIELD", field = "TIME"),
            Pair(
                "Change the time to 8 PM",
                CreateDraftMove.ChangeField(CreateDraftField.TIME, "8 PM")
            ) to json("CHANGE_FIELD", field = "TIME", value = "8 PM"),
            Pair(
                "Change the title",
                CreateDraftMove.ChangeField(CreateDraftField.TITLE)
            ) to json("CHANGE_FIELD", field = "TITLE"),
            Pair(
                "Change the title to Buy medicine",
                CreateDraftMove.ChangeField(CreateDraftField.TITLE, "Buy medicine")
            ) to json("CHANGE_FIELD", field = "TITLE", value = "Buy medicine"),
            Pair(
                "No, make it 9 PM instead",
                CreateDraftMove.ChangeField(CreateDraftField.TIME, "9 PM")
            ) to json("CHANGE_FIELD", field = "TIME", value = "9 PM")
        )
        cases.forEach { case ->
            val (inputAndExpected, response) = case
            val (input, expected) = inputAndExpected
            val result = resolve(FakeClient(response), input, saveState)
            assertEquals(expected, result.move)
            assertFalse(result.move == CreateDraftMove.RejectSave)
        }
    }

    @Test
    fun ambiguousClockCandidateIsPreservedWithoutInventingMeridiem() = runBlocking {
        val result = resolve(
            FakeClient(json("CHANGE_FIELD", field = "TIME", value = "9")),
            "make it 9",
            saveState
        )
        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "9"), result.move)
    }

    @Test
    fun realisticBareFieldsAndSingleFieldCorrectionsRemainSupported() = runBlocking {
        val bareCases = listOf(
            Triple(
                CreateTaskDialogState.WAITING_FOR_TITLE,
                "Buy medicine",
                CreateDraftMove.ProvideField(CreateDraftField.TITLE, "Buy medicine")
            ),
            Triple(
                CreateTaskDialogState.WAITING_FOR_DATE,
                "Sunday",
                CreateDraftMove.ProvideField(CreateDraftField.DATE, "Sunday")
            ),
            Triple(
                CreateTaskDialogState.WAITING_FOR_TIME,
                "8 PM",
                CreateDraftMove.ProvideField(CreateDraftField.TIME, "8 PM")
            )
        )
        bareCases.forEach { (state, input, expected) ->
            val provided = expected as CreateDraftMove.ProvideField
            val result = resolve(
                FakeClient(
                    json(
                        "PROVIDE_FIELD",
                        field = provided.field.name,
                        value = provided.value
                    )
                ),
                input,
                state
            )
            assertEquals(expected, result.move)
        }

        val correctionCases = listOf(
            Triple(
                "No, make it Sunday instead",
                json("CHANGE_FIELD", field = "DATE", value = "Sunday"),
                CreateDraftMove.ChangeField(CreateDraftField.DATE, "Sunday")
            ),
            Triple(
                "No, make it 8 PM instead",
                json("CHANGE_FIELD", field = "TIME", value = "8 PM"),
                CreateDraftMove.ChangeField(CreateDraftField.TIME, "8 PM")
            )
        )
        correctionCases.forEach { (input, response, expected) ->
            val result = resolve(FakeClient(response), input, saveState)
            assertEquals(expected, result.move)
            assertFalse(result.move == CreateDraftMove.RejectSave)
        }
    }

    @Test
    fun naturalSaveApprovalRemainsSemanticAndStateBounded() = runBlocking {
        listOf("That sounds good", "Sure thing").forEach { input ->
            val result = resolve(FakeClient(json("CONFIRM_SAVE", confidence = 0.97)), input, saveState)
            assertEquals(CreateDraftMove.ConfirmSave, result.move)
            assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        }

        val outsideConfirmation = resolve(
            FakeClient(json("CONFIRM_SAVE", confidence = 0.97)),
            "That sounds good",
            CreateTaskDialogState.WAITING_FOR_TIME
        )
        assertFalse(outsideConfirmation.move == CreateDraftMove.ConfirmSave)
    }

    private suspend fun resolve(
        client: CreateDraftSemanticClient,
        text: String,
        state: CreateTaskDialogState
    ): CreateDraftMoveResolution {
        val orchestrator = CreateDraftSemanticOrchestrator(
            localInterpreter = CreateDraftMoveInterpreter(),
            semanticClient = client
        )
        val candidate = orchestrator.proposeLocal(text, state)
        val context = context(state, candidate)
        return orchestrator.resolve(text, state, context, candidate)
    }

    private fun context(
        state: CreateTaskDialogState,
        candidate: CreateDraftMove
    ) = CreateDraftAgentContext.capture(
        state = state,
        pendingReplacementField = null,
        hasTitle = true,
        hasSelectedDate = true,
        hasSelectedTime = state == saveState,
        localCandidate = candidate
    )

    private fun validUnknown() = json("UNKNOWN")

    private fun validConfirm() = json("CONFIRM_SAVE")

    private fun json(
        move: String,
        field: String = "",
        value: String = "",
        date: String = "",
        time: String = "",
        confidence: Double = 0.95
    ): String = """{"move":"$move","field":"$field","value":"$value","date_text":"$date","time_text":"$time","confidence":$confidence}"""

    private val saveState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
}
