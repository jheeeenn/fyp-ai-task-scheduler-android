package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationOrchestrator
import com.example.myapplication.ai.conversation.ConversationResponseParser
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.conversation.ConversationSessionMemory
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextReadTitleGroundingPolicyTest {
    private val tasks = listOf(
        task(101, "tech medicine"),
        task(102, "have breakfast"),
        task(103, "leave home"),
        task(104, "doctor appointment"),
        task(105, "prepare demo presentation")
    )

    @Test
    fun uniqueNormalizedSuppliedTitleSelectsItsTemporaryRef() {
        val store = suppliedStore()
        val result = reconcile(
            text = "What are the subtasks of   PREPARE demo presentation?",
            decision = readDecision("T5"),
            snapshot = store.snapshot(),
            currentGeneration = store.currentGeneration()
        )

        assertEquals(ContextReadTitleGroundingResult.MATCHED_MODEL_REF, result.result)
        assertEquals("T5", result.expectedRef)
        assertEquals("T5", result.decision.contextRef)
    }

    @Test
    fun blankModelRefIsRepairedOnlyFromOneStrongTitleMatch() {
        val store = suppliedStore()
        val result = reconcile(
            "What are the subtasks of prepare demo presentation",
            readDecision(""),
            store.snapshot(),
            store.currentGeneration()
        )

        assertEquals(ContextReadTitleGroundingResult.GROUNDED_BLANK_MODEL_REF, result.result)
        assertEquals("T5", result.decision.contextRef)
    }

    @Test
    fun mismatchedModelRefAndAmbiguousTitlesAreRejected() {
        val store = suppliedStore()
        val mismatch = reconcile(
            "What are the subtasks of prepare demo presentation",
            readDecision("T4"),
            store.snapshot(),
            store.currentGeneration()
        )
        assertEquals(ContextReadTitleGroundingResult.MODEL_REF_MISMATCH, mismatch.result)
        assertFalse(mismatch.isAccepted)

        val ambiguousStore = ReadOnlyTaskContextStore().apply {
            replaceRecentQueryResults(
                listOf(
                    task(1, "prepare demo presentation"),
                    task(2, "prepare demo presentation")
                )
            )
        }
        val ambiguous = reconcile(
            "What are the subtasks of prepare demo presentation",
            readDecision(""),
            ambiguousStore.snapshot(),
            ambiguousStore.currentGeneration()
        )
        assertEquals(ContextReadTitleGroundingResult.AMBIGUOUS_TITLE, ambiguous.result)
        assertFalse(ambiguous.isAccepted)
    }

    @Test
    fun exactLongerTitleWinsOverItsShorterSuppliedPrefix() {
        val store = ReadOnlyTaskContextStore().apply {
            replaceRecentQueryResults(
                listOf(
                    task(1, "prepare demo"),
                    task(2, "prepare demo presentation")
                )
            )
        }

        val result = reconcile(
            "What are the subtasks of prepare demo presentation",
            readDecision("T2"),
            store.snapshot(),
            store.currentGeneration()
        )

        assertEquals(ContextReadTitleGroundingResult.MATCHED_MODEL_REF, result.result)
        assertEquals("T2", result.expectedRef)
    }

    @Test
    fun staleGenerationAndContextNoneCannotGroundATitle() {
        val store = suppliedStore()
        val snapshot = store.snapshot()
        store.clear()
        assertEquals(
            ContextReadTitleGroundingResult.STALE_GENERATION,
            reconcile(
                "What are the subtasks of prepare demo presentation",
                readDecision(""),
                snapshot,
                store.currentGeneration()
            ).result
        )

        assertEquals(
            ContextReadTitleGroundingResult.NOT_APPLICABLE,
            reconcile(
                "What are the subtasks of prepare demo presentation",
                readDecision(""),
                store.snapshot(),
                store.currentGeneration()
            ).result
        )
    }

    @Test
    fun blankAgentRefEntersExistingAuthoritativeSubtaskReadFlow() = runBlocking {
        val store = suppliedStore()
        val capture = store.capture()
        val client = BlankRefClient(readJson(contextRef = ""))
        val orchestrator = ConversationOrchestrator(
            client,
            ConversationDecisionParser(),
            ConversationResponseParser(),
            ConversationSessionMemory()
        )

        val decision = orchestrator.process(
            normalizedText = "What are the subtasks of prepare demo presentation",
            appContextSummary = "QUERY_PAGE",
            readOnlyTaskContextSnapshot = capture.promptText,
            capturedTaskContextSnapshot = capture.snapshot
        )

        assertEquals(ConversationRoute.CONTEXT_READ, decision.route)
        assertEquals(ConversationContextDetail.SUBTASKS, decision.contextDetail)
        assertEquals("T5", decision.contextRef)
        assertEquals(0, client.repairCalls)
        assertTrue(
            ReadOnlyTaskContextReadValidator.validate(
                decision,
                capture.snapshot,
                store.currentGeneration(),
                "What are the subtasks of prepare demo presentation"
            ).isValid
        )

        val parent = tasks.last()
        val children = listOf(
            TaskEntity(id = 201, title = "Draft outline", parentTaskId = parent.id, subtaskOrder = 0),
            TaskEntity(id = 202, title = "Build slides", parentTaskId = parent.id, subtaskOrder = 1)
        )
        val speech = AuthoritativeSubtaskReader(store, { parent }, { children }).read(
            decision.contextRef,
            capture.snapshot.generation
        )
        assertEquals(
            "prepare demo presentation has two subtasks. First, Draft outline. Second, Build slides.",
            speech
        )
        assertEquals(TaskContextScope.SUBTASK_LIST, store.snapshot().scope)
    }

    @Test
    fun orchestratorRejectsMismatchedRefBeforeAcceptingGroundedRepair() = runBlocking {
        val store = suppliedStore()
        val capture = store.capture()
        val client = BlankRefClient(
            primary = readJson(contextRef = "T4"),
            repaired = readJson(contextRef = "T5")
        )
        val decision = orchestrator(client).process(
            normalizedText = "What are the subtasks of prepare demo presentation",
            appContextSummary = "QUERY_PAGE",
            readOnlyTaskContextSnapshot = capture.promptText,
            capturedTaskContextSnapshot = capture.snapshot
        )

        assertEquals(1, client.repairCalls)
        assertEquals(ConversationRoute.CONTEXT_READ, decision.route)
        assertEquals("T5", decision.contextRef)
    }

    @Test
    fun orchestratorClarifiesDuplicateTitleWithoutChoosingARef() = runBlocking {
        val store = ReadOnlyTaskContextStore().apply {
            replaceRecentQueryResults(
                listOf(
                    task(1, "prepare demo presentation"),
                    task(2, "prepare demo presentation")
                )
            )
        }
        val capture = store.capture()
        val client = BlankRefClient(readJson(contextRef = ""))
        val decision = orchestrator(client).process(
            normalizedText = "What are the subtasks of prepare demo presentation",
            appContextSummary = "QUERY_PAGE",
            readOnlyTaskContextSnapshot = capture.promptText,
            capturedTaskContextSnapshot = capture.snapshot
        )

        assertEquals(0, client.repairCalls)
        assertEquals(ConversationRoute.ASK_CLARIFICATION, decision.route)
        assertEquals("", decision.contextRef)
        assertTrue(decision.reply.contains("position in the list"))
    }

    @Test
    fun promptsRequireTheUniqueSuppliedTitleRef() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        assertTrue(prompt.contains("unique mention of one supplied title"))
        assertTrue(prompt.contains("Return that ref"))
    }

    private fun suppliedStore() = ReadOnlyTaskContextStore().apply {
        replaceRecentQueryResults(tasks)
    }

    private fun orchestrator(client: ConversationAgentClient) = ConversationOrchestrator(
        client,
        ConversationDecisionParser(),
        ConversationResponseParser(),
        ConversationSessionMemory()
    )

    private fun reconcile(
        text: String,
        decision: ConversationDecision,
        snapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long
    ) = ContextReadTitleGroundingPolicy.reconcile(
        text,
        decision,
        snapshot,
        currentGeneration
    )

    private fun readDecision(ref: String) = ConversationDecision(
        route = ConversationRoute.CONTEXT_READ,
        contextRef = ref,
        contextDetail = ConversationContextDetail.SUBTASKS,
        confidence = 0.98,
        listenAgain = true
    )

    private fun task(id: Long, title: String) = TaskEntity(id = id, title = title)

    private class BlankRefClient(
        private val primary: String,
        private val repaired: String = readJson(contextRef = "T5")
    ) : ConversationAgentClient(null) {
        var repairCalls = 0

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String = primary

        override suspend fun processRepair(
            userText: String,
            appContextSummary: String
        ): String {
            repairCalls += 1
            return repaired
        }
    }

    private companion object {
        fun readJson(contextRef: String): String = """
            {
              "route":"CONTEXT_READ",
              "navigation_target":"NONE",
              "task_text":"",
              "reply":"",
              "context_ref":"$contextRef",
              "context_detail":"SUBTASKS",
              "context_action":"NONE",
              "setting_target":"NONE",
              "setting_action":"NONE",
              "query_reading_move":"NONE",
              "query_presentation_hint":"NONE",
              "confidence":0.98,
              "listen_again":true
            }
        """.trimIndent()
    }
}
