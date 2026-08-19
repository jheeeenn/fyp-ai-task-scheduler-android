package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.query.QueryCountFollowUpMove
import com.example.myapplication.ai.conversation.query.QueryCountFollowUpParser
import com.example.myapplication.ai.conversation.taskcontext.ContextActionDecisionValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingResult
import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextFocusActionEllipsisPolicy
import com.example.myapplication.ai.conversation.taskcontext.PendingContextActionTargetMove
import com.example.myapplication.ai.conversation.taskcontext.PendingContextActionTargetParser
import com.example.myapplication.ai.conversation.taskcontext.PresentedQueryFocusPolicy
import com.example.myapplication.ai.conversation.taskcontext.PresentedQueryFocusResult
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import com.example.myapplication.ai.LocalConversationIntentClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConversationContextContinuityTest {
    @Test
    fun onlyDeliveredSingleItemOverviewIsEligibleForAutomaticFocus() {
        val single = snapshot("T1")

        val delivered = PresentedQueryFocusPolicy.evaluate(
            presentation = TaskQueryPresentationLevel.OVERVIEW,
            snapshot = single,
            deliveredGeneration = single.generation,
            currentGeneration = single.generation
        )
        val staleBeforeDelivery = PresentedQueryFocusPolicy.evaluate(
            presentation = TaskQueryPresentationLevel.OVERVIEW,
            snapshot = single,
            deliveredGeneration = null,
            currentGeneration = single.generation
        )
        val countOnly = PresentedQueryFocusPolicy.evaluate(
            presentation = TaskQueryPresentationLevel.COUNT_ONLY,
            snapshot = single,
            deliveredGeneration = null,
            currentGeneration = single.generation
        )
        val multiple = PresentedQueryFocusPolicy.evaluate(
            presentation = TaskQueryPresentationLevel.OVERVIEW,
            snapshot = snapshot("T1", "T2"),
            deliveredGeneration = 8,
            currentGeneration = 8
        )

        assertTrue(delivered.shouldEstablish)
        assertEquals("T1", delivered.item?.ref)
        assertEquals(PresentedQueryFocusResult.STALE, staleBeforeDelivery.result)
        assertEquals(PresentedQueryFocusResult.COUNT_ONLY, countOnly.result)
        assertEquals(PresentedQueryFocusResult.MULTIPLE_ITEMS, multiple.result)
    }

    @Test
    fun focusFromPresentedSingleItemGroundsDeicticDeleteAndReschedule() {
        val snapshot = snapshot("T1")
        val focus = focus("T1", snapshot.generation)
        val delete = actionDecision("T1", ConversationContextAction.DELETE)
        val reschedule = actionDecision("T1", ConversationContextAction.RESCHEDULE)

        assertEquals(
            ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS,
            ContextActionReferenceGroundingValidator.validate(
                "delete it",
                delete,
                snapshot,
                focus
            ).result
        )
        assertEquals(
            ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS,
            ContextActionReferenceGroundingValidator.validate(
                "move it to Friday",
                reschedule,
                snapshot,
                focus
            ).result
        )
        assertTrue(
            ContextActionDecisionValidator.validate(
                delete,
                snapshot,
                snapshot.generation
            ).isValid
        )
    }

    @Test
    fun boundedBareDeleteUsesFocusButNamedDeleteDoesNot() {
        val snapshot = snapshot("T1")
        val focus = focus("T1", snapshot.generation)
        val failedPrimary = ConversationDecision(
            route = ConversationRoute.TASK_COMMAND,
            taskText = "delete",
            source = "conversation_agent"
        )

        val bare = ContextFocusActionEllipsisPolicy.resolve(
            normalizedText = "delete",
            currentDecision = failedPrimary,
            capturedSnapshot = snapshot,
            currentGeneration = snapshot.generation,
            currentFocus = focus
        )
        val named = ContextFocusActionEllipsisPolicy.resolve(
            normalizedText = "delete dentist",
            currentDecision = failedPrimary.copy(taskText = "delete dentist"),
            capturedSnapshot = snapshot,
            currentGeneration = snapshot.generation,
            currentFocus = focus
        )

        assertEquals(ConversationRoute.CONTEXT_ACTION, bare?.route)
        assertEquals(ConversationContextAction.DELETE, bare?.contextAction)
        assertEquals("T1", bare?.contextRef)
        assertEquals(
            ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS,
            ContextActionReferenceGroundingValidator.validate(
                "delete",
                requireNotNull(bare),
                snapshot,
                focus
            ).result
        )
        assertNull(named)
    }

    @Test
    fun boundedCompletionEllipsisUsesOnlyCurrentFocus() {
        val snapshot = snapshot("T1")
        val focus = focus("T1", snapshot.generation)
        val failedPrimary = ConversationDecision(
            route = ConversationRoute.TASK_COMMAND,
            source = "conversation_agent"
        )

        mapOf(
            "complete" to ConversationContextAction.MARK_DONE,
            "mark done" to ConversationContextAction.MARK_DONE,
            "finish" to ConversationContextAction.MARK_DONE,
            "reopen" to ConversationContextAction.MARK_UNDONE,
            "undo completion" to ConversationContextAction.MARK_UNDONE,
            "mark undone" to ConversationContextAction.MARK_UNDONE
        ).forEach { (utterance, expectedAction) ->
            val decision = ContextFocusActionEllipsisPolicy.resolve(
                normalizedText = utterance,
                currentDecision = failedPrimary,
                capturedSnapshot = snapshot,
                currentGeneration = snapshot.generation,
                currentFocus = focus
            )
            assertEquals(expectedAction, decision?.contextAction)
            assertEquals("T1", decision?.contextRef)
        }
    }

    @Test
    fun oldFocusCannotCrossContextGeneration() {
        val oldSnapshot = snapshot("T1", generation = 8)
        val newSnapshot = snapshot("T1", generation = 9)
        val oldFocus = focus("T1", oldSnapshot.generation)

        assertNull(
            ContextFocusActionEllipsisPolicy.resolve(
                normalizedText = "delete",
                currentDecision = ConversationDecision(route = ConversationRoute.TASK_COMMAND),
                capturedSnapshot = newSnapshot,
                currentGeneration = newSnapshot.generation,
                currentFocus = oldFocus
            )
        )
        assertEquals(
            ContextActionReferenceGroundingResult.STALE_FOCUS,
            ContextActionReferenceGroundingValidator.validate(
                normalizedText = "delete it",
                decision = actionDecision("T1", ConversationContextAction.DELETE),
                capturedSnapshot = newSnapshot,
                currentFocus = oldFocus
            ).result
        )
    }

    @Test
    fun multiItemDeicticMutationNeverDefaultsToT1() {
        val multiple = snapshot("T1", "T2")
        val grounding = ContextActionReferenceGroundingValidator.validate(
            normalizedText = "delete it",
            decision = actionDecision("T1", ConversationContextAction.DELETE),
            capturedSnapshot = multiple,
            currentFocus = null
        )

        assertFalse(grounding.isValid)
        assertEquals(ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS, grounding.result)
    }

    @Test
    fun queryCountBoundedContractAcceptsNaturalAgreementAndFallsThroughFreshQueries() = runBlocking {
        val variants = listOf("yes of course", "please do", "go ahead")
        variants.forEach { utterance ->
            val client = ContinuityClient(queryCountResponse = queryCountJson("START_OVERVIEW"))
            val result = ConversationOrchestrator(client, ConversationDecisionParser())
                .processQueryCountFollowUp(utterance)
            assertEquals(QueryCountFollowUpMove.START_OVERVIEW, result.move)
            assertEquals(utterance, client.queryCountText)
        }

        val freshClient = ContinuityClient(
            queryCountResponse = queryCountJson("NOT_A_QUERY_READING_CONTROL")
        )
        val fresh = ConversationOrchestrator(freshClient, ConversationDecisionParser())
            .processQueryCountFollowUp("What tasks do I have next week?")
        assertEquals(QueryCountFollowUpMove.NOT_A_QUERY_READING_CONTROL, fresh.move)
        val prompt = ConversationAgentClient.QUERY_COUNT_FOLLOW_UP_SYSTEM_PROMPT
        listOf(
            "yes of course",
            "of course",
            "absolutely",
            "please do",
            "go ahead",
            "tell me",
            "read it"
        ).forEach { assertTrue(prompt.contains(it)) }
        assertTrue(prompt.contains("NOT_A_QUERY_READING_CONTROL"))
        assertEquals(0.0, ConversationAgentClient.ROUTING_TEMPERATURE, 0.0)
        assertEquals(0.80f, LocalConversationIntentClassifier.EXECUTION_CONFIDENCE_THRESHOLD, 0.0f)
    }

    @Test
    fun lowConfidenceQueryCountControlFallsThrough() {
        val decision = QueryCountFollowUpParser.parse(
            """{"move":"START_OVERVIEW","confidence":0.72}"""
        )
        assertEquals(QueryCountFollowUpMove.NOT_A_QUERY_READING_CONTROL, decision.move)
    }

    @Test
    fun pendingDeleteTargetContractKeepsActionAndroidOwned() = runBlocking {
        val client = ContinuityClient(
            pendingTargetResponse = pendingTargetJson("SELECT_TARGET", "T1")
        )
        val result = ConversationOrchestrator(client, ConversationDecisionParser())
            .processPendingContextActionTarget(
                normalizedText = "the dinner task",
                readOnlyTaskContextSnapshot = prompt("T1", "T2"),
                pendingAction = ConversationContextAction.DELETE
            )

        assertEquals(PendingContextActionTargetMove.SELECT_TARGET, result.move)
        assertEquals("T1", result.contextRef)
        assertEquals(ConversationContextAction.DELETE, client.pendingAction)
        assertFalse(client.pendingTargetResponse.contains("DELETE"))

        val candidate = actionDecision(result.contextRef, ConversationContextAction.DELETE)
        assertEquals(
            ContextActionReferenceGroundingResult.VALID_UNIQUE_TITLE,
            ContextActionReferenceGroundingValidator.validate(
                "the dinner task",
                candidate,
                snapshot("T1", "T2"),
                null
            ).result
        )
    }

    @Test
    fun pendingDeleteOrdinalResolvesAndFreshQueryFallsThrough() {
        val selected = PendingContextActionTargetParser.parse(
            pendingTargetJson("SELECT_TARGET", "T1")
        )
        val fresh = PendingContextActionTargetParser.parse(
            pendingTargetJson("NOT_A_TARGET_ANSWER", "")
        )
        val ordinalGrounding = ContextActionReferenceGroundingValidator.validate(
            normalizedText = "the first one",
            decision = actionDecision(selected.contextRef, ConversationContextAction.DELETE),
            capturedSnapshot = snapshot("T1", "T2"),
            currentFocus = null
        )

        assertEquals(ContextActionReferenceGroundingResult.VALID_ORDINAL, ordinalGrounding.result)
        assertEquals(PendingContextActionTargetMove.NOT_A_TARGET_ANSWER, fresh.move)
    }

    @Test
    fun homeIntegrationKeepsDeliveryPendingStateAndDeleteConfirmationBoundaries() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val repeatable = source
            .substringAfter("private suspend fun speakRepeatableObservation(")
            .substringBefore("private suspend fun speakObservationThenRun(")
        val contextAction = source
            .substringAfter("ConversationRoute.CONTEXT_ACTION -> {")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")

        assertTrue(repeatable.contains("SafeObservationDeliveryGuard.runIfCurrent("))
        assertTrue(repeatable.contains("updateFocusAfterAuthoritativeQueryDelivery("))
        assertTrue(repeatable.indexOf("deliverObservationResponse(") < repeatable.indexOf("updateFocusAfterAuthoritativeQueryDelivery("))
        assertTrue(source.contains("source=SINGLE_PRESENTED_QUERY_RESULT"))
        assertTrue(source.contains("reason=COUNT_ONLY"))
        assertTrue(source.contains("PresentedQueryFocusResult.MULTIPLE_ITEMS -> \"MULTIPLE_ITEMS\""))
        assertTrue(source.contains("PendingContextActionClarification("))
        assertTrue(source.contains("capturedGeneration"))
        assertTrue(source.contains("suppliedRefs"))
        assertTrue(source.contains("PendingContextActionTargetMove.NOT_A_TARGET_ANSWER"))
        assertTrue(source.contains("conversationOrchestrator.clearPendingDialogueAction()"))
        assertTrue(contextAction.contains("ContextActionDecisionValidator.validate("))
        assertTrue(contextAction.contains("ContextActionReferenceGroundingValidator.validate("))
        assertTrue(contextAction.contains("askDeleteConfirmation(requireNotNull(initiallyFetchedTask))"))
        assertTrue(contextAction.indexOf("askDeleteConfirmation(requireNotNull(initiallyFetchedTask))") < contextAction.indexOf("agentOrchestrator.processContextAction("))
        assertFalse(repeatable.contains("pendingDeleteTaskId"))
    }

    private class ContinuityClient(
        private val queryCountResponse: String = queryCountJson("NOT_A_QUERY_READING_CONTROL"),
        val pendingTargetResponse: String = pendingTargetJson("ASK_CLARIFICATION", "")
    ) : ConversationAgentClient(null) {
        var queryCountText = ""
        var pendingAction: ConversationContextAction? = null

        override suspend fun processQueryCountFollowUp(userText: String): String {
            queryCountText = userText
            return queryCountResponse
        }

        override suspend fun processPendingContextActionTarget(
            userText: String,
            taskContextSnapshot: String,
            pendingAction: ConversationContextAction
        ): String {
            this.pendingAction = pendingAction
            return pendingTargetResponse
        }
    }

    private companion object {
        fun item(ref: String, title: String) = ReadOnlyTaskContextItem(
            ref = ref,
            title = title,
            dueDate = "11/08/2026",
            dueTime = "6:00 PM",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )

        fun snapshot(vararg refs: String, generation: Long = 8) = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.RECENT_QUERY_RESULTS,
            generation = generation,
            items = refs.mapIndexed { index, ref ->
                item(ref, if (index == 0) "Dinner" else "Dentist")
            },
            truncated = false
        )

        fun focus(ref: String, generation: Long) = ConversationContextFocus(
            available = true,
            ref = ref,
            generation = generation,
            detail = ConversationContextDetail.SUMMARY,
            title = "Dinner"
        )

        fun actionDecision(ref: String, action: ConversationContextAction) = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = ref,
            contextAction = action,
            confidence = 0.97
        )

        fun prompt(vararg refs: String) = buildString {
            appendLine("Scope: RECENT_QUERY_RESULTS")
            appendLine("Generation: 8")
            appendLine("Items:")
            refs.forEachIndexed { index, ref ->
                appendLine("{\"ref\":\"$ref\",\"title\":\"${if (index == 0) "Dinner" else "Dentist"}\"}")
            }
        }

        fun queryCountJson(move: String) = """{"move":"$move","confidence":0.97}"""
        fun pendingTargetJson(move: String, ref: String) =
            """{"move":"$move","context_ref":"$ref","confidence":0.97}"""
    }
}
