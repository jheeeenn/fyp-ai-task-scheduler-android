package com.example.myapplication.ai.conversation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class SafeObservationAsyncStateTest {
    private val homeSource =
        File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun styleAuthorizationIsImmutablePerTurnAndNoGlobalBudgetBooleanRemains() {
        val first = SafeStyleTurnAuthorization(requestGeneration = 4, styleCallAllowed = true)
        val second = SafeStyleTurnAuthorization(requestGeneration = 5, styleCallAllowed = false)

        assertEquals(4, first.requestGeneration)
        assertTrue(first.styleCallAllowed)
        assertEquals(5, second.requestGeneration)
        assertFalse(second.styleCallAllowed)
        assertFalse(homeSource.contains("safeObservationStyleBudgetAvailable"))
        assertTrue(homeSource.contains("val localStyleAuthorization = SafeStyleTurnAuthorization("))
        assertTrue(homeSource.contains("val routedStyleAuthorization = SafeStyleTurnAuthorization("))
    }

    @Test
    fun staleStyleAuthorizationDoesNotInvalidateCurrentRequestAndUsesDeterministicSpeech() =
        runBlocking {
            val requestToken = AssistantRequestToken(requestGeneration = 9)
            val staleAuthorization = SafeStyleTurnAuthorization(
                requestGeneration = 8,
                styleCallAllowed = true
            )
            val evaluation = SafeStyleAuthorizationPolicy.evaluate(
                requestToken,
                staleAuthorization
            )
            val client = ImmediateStyleClient()
            val response = ConversationOrchestrator(client, ConversationDecisionParser())
                .styleTaskQuerySpeech(stylePlan(), evaluation.styleCallAllowed)

            assertTrue(AssistantRequestTokenPolicy.isCurrent(requestToken, 9, true))
            assertEquals(SafeStyleAuthorizationStatus.STALE, evaluation.status)
            assertEquals("android_deterministic", response.source)
            assertEquals(stylePlan().deterministicSpeech, response.speech)
            assertEquals(0, client.calls)

            val render = homeSource
                .substringAfter("private suspend fun renderObservationResponse(")
                .substringBefore("private fun recordObservationResponse(")
            assertTrue(render.contains("reason=STYLE_AUTHORIZATION_STALE"))
            assertTrue(render.contains("AndroidObservationResponseRenderer.render(observation)"))
        }

    @Test
    fun staleRequestTokenCancelsTheOldRequestButQueryResetPreservesCurrentToken() {
        val token = AssistantRequestToken(requestGeneration = 12)
        assertFalse(AssistantRequestTokenPolicy.isCurrent(token, 13, true))
        assertFalse(AssistantRequestTokenPolicy.isCurrent(token, 12, false))
        assertTrue(AssistantRequestTokenPolicy.isCurrent(token, 12, true))

        val clear = homeSource
            .substringAfter("private fun clearAccessibleTaskQuerySession(")
            .substringBefore("private fun beginAssistantRequest(")
        assertFalse(clear.contains("assistantRequestGeneration"))
        assertFalse(clear.contains("invalidateAssistantRequest("))
        assertTrue(clear.contains("queryReadingStateGeneration += 1"))
    }

    @Test
    fun newCommandInvalidatesEarlierAuthorization() {
        val captured = state(request = 7)
        val current = captured.copy(requestGeneration = 8)

        assertEquals(
            SafeObservationStaleReason.REQUEST_CHANGED,
            SafeObservationDeliveryGuard.staleReason(captured, current)
        )
    }

    @Test
    fun cancellationStopAndConversationEndInvalidateInflightDeliveryBeforeClear() {
        val captured = state(request = 10)
        val inactive = captured.copy(requestGeneration = 11, assistantRequestActive = false)
        assertEquals(
            SafeObservationStaleReason.REQUEST_CHANGED,
            SafeObservationDeliveryGuard.staleReason(captured, inactive)
        )

        val cancelled = homeSource
            .substringAfter("override fun onAssistantCancelled()")
            .substringBefore("override fun onAssistantSessionStopped()")
        val stopped = homeSource
            .substringAfter("override fun onAssistantSessionStopped()")
            .substringBefore("override fun onResume()")
        val ended = homeSource
            .substringAfter("private fun endAssistantConversation()")
            .substringBefore("private fun clearConversationSessionContext(")
        listOf(cancelled, stopped, ended).forEach { body ->
            assertTrue(body.indexOf("invalidateAssistantRequest(") >= 0)
            assertTrue(
                body.indexOf("invalidateAssistantRequest(") <
                    body.indexOf("clearConversationSessionContext(")
            )
        }
        assertTrue(cancelled.contains("AssistantRequestInvalidationReason.USER_CANCELLED"))
        assertTrue(stopped.contains("AssistantRequestInvalidationReason.SESSION_STOPPED"))
        assertTrue(ended.contains("AssistantRequestInvalidationReason.CONVERSATION_ENDED"))
    }

    @Test
    fun newQueryAndPageContinuationInvalidateEarlierDelivery() {
        val captured = state(queryGeneration = 20, contextGeneration = 30, pageIndex = 0)
        assertEquals(
            SafeObservationStaleReason.QUERY_CHANGED,
            SafeObservationDeliveryGuard.staleReason(
                captured,
                captured.copy(queryReadingStateGeneration = 21)
            )
        )
        assertEquals(
            SafeObservationStaleReason.QUERY_PAGE_CHANGED,
            SafeObservationDeliveryGuard.staleReason(
                captured,
                captured.copy(pageIndex = 1)
            )
        )
        assertEquals(
            SafeObservationStaleReason.TASK_CONTEXT_CHANGED,
            SafeObservationDeliveryGuard.staleReason(
                captured,
                captured.copy(taskContextGeneration = 31)
            )
        )
    }

    @Test
    fun staleStyledTimeoutAndUnsafeResultsAllRemainSilent() {
        listOf("android_hybrid_safe", "timeout_fallback", "unsafe_fallback").forEach {
            val effects = DeliveryEffects()
            val captured = state(request = 1)
            val reason = SafeObservationDeliveryGuard.runIfCurrent(
                captured,
                captured.copy(requestGeneration = 2)
            ) {
                effects.recorded += 1
                effects.authoritativeRepeatUpdates += 1
                effects.pageRepeatUpdates += 1
                effects.spoken += 1
            }

            assertEquals(SafeObservationStaleReason.REQUEST_CHANGED, reason)
            assertEquals(DeliveryEffects(), effects)
        }
    }

    @Test
    fun inflightStyledTimeoutAndUnsafeCompletionsAreDiscardedWhenTurnChanges() =
        runBlocking {
            val outcomes = listOf(
                Result.success(
                    """{"use_style":true,"lead_in":"Certainly.","bridge":"","confidence":0.95}"""
                ),
                Result.failure(IOException("request timed out")),
                Result.success(
                    """{"use_style":true,"lead_in":"Please continue.","bridge":"","confidence":0.99}"""
                )
            )

            outcomes.forEach { outcome ->
                val result = completeStyleAfterInvalidation(outcome)
                assertEquals(SafeObservationStaleReason.REQUEST_CHANGED, result.reason)
                assertEquals(DeliveryEffects(), result.effects)
                assertNull(result.memory.finalSpokenResponse)
                assertEquals(1, result.styleCalls)
            }
        }

    @Test
    fun staleResultDoesNotEnterMemoryOrUpdateRepeatState() {
        val memory = ConversationSessionMemory()
        val effects = DeliveryEffects()
        val captured = state(queryGeneration = 1)

        SafeObservationDeliveryGuard.runIfCurrent(
            captured,
            captured.copy(queryReadingStateGeneration = 2)
        ) {
            memory.recordFinalSpokenResponse("Old response")
            effects.authoritativeRepeatUpdates += 1
            effects.pageRepeatUpdates += 1
            effects.spoken += 1
        }

        assertNull(memory.finalSpokenResponse)
        assertFalse(memory.snapshotForPrompt().contains("Old response"))
        assertEquals(DeliveryEffects(), effects)
    }

    @Test
    fun currentResultIsSpokenRecordedAndStoredExactlyOnce() {
        val memory = ConversationSessionMemory()
        val effects = DeliveryEffects()
        val captured = state()

        val reason = SafeObservationDeliveryGuard.runIfCurrent(captured, captured) {
            memory.recordFinalSpokenResponse("Current response")
            effects.recorded += 1
            effects.authoritativeRepeatUpdates += 1
            effects.pageRepeatUpdates += 1
            effects.spoken += 1
        }

        assertNull(reason)
        assertEquals("Current response", memory.finalSpokenResponse)
        assertEquals(
            DeliveryEffects(
                recorded = 1,
                authoritativeRepeatUpdates = 1,
                pageRepeatUpdates = 1,
                spoken = 1
            ),
            effects
        )
        assertEquals(1, memory.snapshotForPrompt().windowed("Assistant: Current response".length)
            .count { it == "Assistant: Current response" })
    }

    @Test
    fun inflightCurrentStyleResultStillDeliversExactlyOnce() = runBlocking {
        val captured = state()
        var current = captured
        val client = DelayedStyleClient()
        val memory = ConversationSessionMemory()
        val effects = DeliveryEffects()
        val delivery = async {
            val response = ConversationOrchestrator(client, ConversationDecisionParser())
                .styleTaskQuerySpeech(stylePlan(), true)
            SafeObservationDeliveryGuard.runIfCurrent(captured, current) {
                memory.recordFinalSpokenResponse(response.speech)
                effects.recorded += 1
                effects.authoritativeRepeatUpdates += 1
                effects.pageRepeatUpdates += 1
                effects.spoken += 1
            }
        }

        client.started.await()
        client.outcome.complete(
            Result.success(
                """{"use_style":true,"lead_in":"Certainly.","bridge":"","confidence":0.95}"""
            )
        )

        assertNull(delivery.await())
        assertEquals(1, client.calls)
        assertEquals(
            "Certainly. ${stylePlan().authoritativeCore} ${stylePlan().authoritativeControl}",
            memory.finalSpokenResponse
        )
        assertEquals(DeliveryEffects(1, 1, 1, 1), effects)
    }

    @Test
    fun localStartAndContinueUseTheirExactAuthorizationWhileRepeatUsesNoStylePath() {
        val handler = homeSource
            .substringAfter("private fun handleQueryReadingFollowUp(")
            .substringBefore("private fun validateQueryReadingControl(")
        val executor = homeSource
            .substringAfter("private fun executeQueryReadingControl(")
            .substringBefore("private fun currentQueryReadingInteractionState()")
        val repeat = homeSource
            .substringAfter("private fun repeatCurrentTaskQueryPage()")
            .substringBefore("private fun endAssistantConversation()")

        assertTrue(handler.contains("startQueryOverviewFromCount(requestToken, authorization)"))
        assertTrue(handler.contains("continueTaskQueryPage(requestToken, authorization)"))
        assertTrue(executor.contains("startQueryOverviewFromCount(requestToken, authorization)"))
        assertTrue(executor.contains("continueTaskQueryPage(requestToken, authorization)"))
        assertTrue(executor.contains("REPEAT_LAST -> repeatLastAuthoritativeSpeech()"))
        assertTrue(executor.contains("REPEAT_PAGE -> repeatCurrentTaskQueryPage()"))
        assertFalse(repeat.contains("styleTaskQuerySpeech("))
        assertFalse(repeat.contains("requestSafeObservationStyle("))
    }

    @Test
    fun postStyleGuardWrapsRecordingRepeatUpdatesHintAndSpeech() {
        val body = homeSource
            .substringAfter("private suspend fun speakRepeatableObservation(")
            .substringBefore("private suspend fun speakObservationThenRun(")
        val gate = body.indexOf("SafeObservationDeliveryGuard.runIfCurrent(")
        val record = body.indexOf("recordObservationResponse(")
        val authoritative = body.indexOf("authoritativeRepeatState =")
        val page = body.indexOf("currentQueryPageRepeatState =")
        val deliver = body.indexOf("deliverObservationResponse(")

        assertTrue(gate >= 0)
        assertTrue(gate < record)
        assertTrue(record < authoritative)
        assertTrue(authoritative < page)
        assertTrue(page < deliver)
        assertTrue(body.indexOf("renderObservationResponse(") < gate)
    }

    @Test
    fun styleHttpErrorUsesSpecificNonSensitiveLabel() {
        val message = ConversationAgentClient.safeStyleHttpErrorMessage(503)

        assertEquals("LM Studio safe-style HTTP 503", message)
        assertFalse(message.contains("create-draft"))
        assertFalse(message.contains("{"))
    }

    @Test
    fun requestLifecycleDiagnosticsAreReasonBasedAndContainNoTaskData() {
        val lifecycle = homeSource
            .substringAfter("private fun beginAssistantRequest()")
            .substringBefore("private fun logQueryPageEndIfActive(")

        assertTrue(lifecycle.contains("\"ASSISTANT_REQUEST_BEGIN\""))
        assertTrue(lifecycle.contains("\"ASSISTANT_REQUEST_INVALIDATE\""))
        assertTrue(lifecycle.contains("\"ASSISTANT_REQUEST_STALE\""))
        assertTrue(lifecycle.contains("reason=NEWER_REQUEST"))
        assertFalse(lifecycle.contains("task.title"))
        assertFalse(lifecycle.contains("normalized"))
    }

    private fun state(
        request: Long = 1,
        queryGeneration: Long = 2,
        contextGeneration: Long? = 3,
        pageIndex: Int? = 0
    ) = SafeObservationDeliveryState(
        requestGeneration = request,
        queryReadingStateGeneration = queryGeneration,
        taskContextGeneration = contextGeneration,
        pageIndex = pageIndex,
        interaction = SafeObservationInteraction.QUERY_PAGE,
        querySessionActive = true,
        assistantRequestActive = true
    )

    private data class DeliveryEffects(
        var recorded: Int = 0,
        var authoritativeRepeatUpdates: Int = 0,
        var pageRepeatUpdates: Int = 0,
        var spoken: Int = 0
    )

    private suspend fun completeStyleAfterInvalidation(
        outcome: Result<String>
    ): InflightResult = coroutineScope {
        val captured = state()
        var current = captured
        val client = DelayedStyleClient()
        val memory = ConversationSessionMemory()
        val effects = DeliveryEffects()
        val completion = async {
            val response = ConversationOrchestrator(client, ConversationDecisionParser())
                .styleTaskQuerySpeech(stylePlan(), true)
            val reason = SafeObservationDeliveryGuard.runIfCurrent(captured, current) {
                memory.recordFinalSpokenResponse(response.speech)
                effects.recorded += 1
                effects.authoritativeRepeatUpdates += 1
                effects.pageRepeatUpdates += 1
                effects.spoken += 1
            }
            InflightResult(reason, effects, memory, client.calls)
        }

        client.started.await()
        current = captured.copy(
            requestGeneration = captured.requestGeneration + 1
        )
        client.outcome.complete(outcome)
        completion.await()
    }

    private fun stylePlan() = TaskQuerySpeechPlan(
        authoritativeCore = "First, Alpha on 20 July 2026 at 08:00.",
        authoritativeControl = "You can ask for full details.",
        deterministicSpeech =
            "First, Alpha on 20 July 2026 at 08:00. You can ask for full details.",
        styleContext = TaskQueryStyleContext(
            operation = ExecutionOperation.QUERY_TASK,
            presentation = TaskQueryPresentationLevel.OVERVIEW,
            pageRole = TaskQueryPageRole.SINGLE,
            tone = TaskQuerySpeechTone.FRIENDLY,
            continuedInteractionExpected = true,
            controlCategory = TaskQueryControlCategory.ASK_TASK_OR_DETAILS
        )
    )

    private class DelayedStyleClient : ConversationAgentClient(null) {
        val started = CompletableDeferred<Unit>()
        val outcome = CompletableDeferred<Result<String>>()
        var calls = 0

        override suspend fun requestSafeObservationStyle(styleContextJson: String): String {
            calls += 1
            started.complete(Unit)
            return outcome.await().getOrThrow()
        }
    }

    private class ImmediateStyleClient : ConversationAgentClient(null) {
        var calls = 0

        override suspend fun requestSafeObservationStyle(styleContextJson: String): String {
            calls += 1
            return """{"use_style":true,"lead_in":"Certainly.","bridge":"","confidence":0.95}"""
        }
    }

    private data class InflightResult(
        val reason: SafeObservationStaleReason?,
        val effects: DeliveryEffects,
        val memory: ConversationSessionMemory,
        val styleCalls: Int
    )
}
