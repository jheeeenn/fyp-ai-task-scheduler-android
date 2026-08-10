package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PendingContextActionRequestContinuationTest {
    private val homeSource = File(
        "src/main/java/com/example/myapplication/HomeActivity.kt"
    ).readText()

    @Test
    fun pendingReschedulePreservesOriginalRequestForExtraction() {
        val state = homeSource
            .substringAfter("private data class PendingContextActionClarification(")
            .substringBefore("private enum class AssistantRequestInvalidationReason")
        val resolution = homeSource
            .substringAfter("private suspend fun resolvePendingContextActionTarget(")
            .substringBefore("private fun sameContextActionTaskSnapshot(")
        val actionBranch = contextActionBranch()

        assertTrue(state.contains("val originalNormalizedRequest: String"))
        assertTrue(resolution.contains("normalizedText = normalizedText"))
        assertTrue(resolution.contains("normalizedText = normalizedText,\n                decision = candidate"))
        assertTrue(resolution.contains("originalActionRequest = pending.originalNormalizedRequest"))
        assertTrue(homeSource.contains("pendingTargetResolution.originalActionRequest ?: normalized"))
        assertTrue(actionBranch.contains("normalizedText = contextActionRequestText"))
        assertFalse(actionBranch.contains("agentOrchestrator.processContextAction(\n                                normalizedText = normalized,"))
    }

    @Test
    fun pendingUpdateUsesSameOriginalRequestBoundary() {
        val beginCalls = homeSource
            .split("beginContextActionTargetClarification(")
            .drop(1)
            .dropLast(1)

        assertTrue(beginCalls.size >= 2)
        beginCalls.forEach { call ->
            assertTrue(call.substringBefore(")").contains("originalNormalizedRequest = normalized"))
        }
        assertTrue(
            ConversationAgentClient.CONTEXT_ACTION_REPAIR_SYSTEM_PROMPT.contains(
                "Use UPDATE for opening or editing general task details and explicit replacement titles"
            )
        )
        assertTrue(contextActionBranch().contains("expectedAction = validation.action"))
    }

    @Test
    fun deleteStillConfirmsBeforeAnyChangeExtraction() {
        val actionBranch = contextActionBranch()
        val delete = actionBranch.indexOf("askDeleteConfirmation(requireNotNull(initiallyFetchedTask))")
        val extraction = actionBranch.indexOf("agentOrchestrator.processContextAction(")

        assertTrue(delete >= 0)
        assertTrue(extraction > delete)
        assertTrue(actionBranch.substring(delete, extraction).contains("return@launch"))
    }

    @Test
    fun freshAndStaleTurnsClearTheOnlyStoredOriginalRequest() {
        val clear = homeSource
            .substringAfter("private fun clearPendingContextActionClarification(")
            .substringBefore("private suspend fun resolvePendingContextActionTarget(")
        val resolve = homeSource
            .substringAfter("private suspend fun resolvePendingContextActionTarget(")
            .substringBefore("private fun sameContextActionTaskSnapshot(")
        val fresh = resolve
            .substringAfter("PendingContextActionTargetMove.NOT_A_TARGET_ANSWER")
            .substringBefore("PendingContextActionTargetMove.ASK_CLARIFICATION")
        val stale = resolve.substringBefore("val interpretation = try")

        assertTrue(clear.contains("pendingContextActionClarification = null"))
        assertTrue(fresh.contains("clearPendingContextActionClarification(restoreContext = true)"))
        assertTrue(fresh.contains("return PendingContextActionResolution()"))
        assertTrue(stale.contains("clearPendingContextActionClarification(restoreContext = false)"))
        assertFalse(homeSource.contains("pendingOriginalActionRequest"))
    }

    @Test
    fun targetSelectionModelCannotAlterActionOrOriginalRequest() {
        val schema = AgentResponseSchemas.pendingContextActionTargetResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
            .getJSONObject("properties")
        val clientMethod = File(
            "src/main/java/com/example/myapplication/ai/conversation/ConversationAgentClient.kt"
        ).readText()
            .substringAfter("open suspend fun processPendingContextActionTarget(")
            .substringBefore("open suspend fun respondToObservation(")

        assertTrue(schema.has("move"))
        assertTrue(schema.has("context_ref"))
        assertTrue(schema.has("confidence"))
        assertFalse(schema.has("context_action"))
        assertFalse(schema.has("original_request"))
        assertFalse(clientMethod.contains("originalNormalizedRequest"))
        assertTrue(homeSource.contains("contextAction = pending.action"))
        assertTrue(homeSource.contains("originalActionRequest = pending.originalNormalizedRequest"))
    }

    private fun contextActionBranch(): String = homeSource
        .substringAfter("ConversationRoute.CONTEXT_ACTION -> {")
        .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")
}
