package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextStore
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import com.example.myapplication.ai.conversation.taskcontext.ContextActionDecisionValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextItemRestatementDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextItemRestatementPolicy
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextReadValidator
import com.example.myapplication.ai.conversation.query.QueryReadingControlPolicy
import com.example.myapplication.ai.conversation.query.QueryReadingInteractionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DailyBriefingContractTest {
    private val homeSource =
        File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
    private val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT

    @Test
    fun strictRoutingSchemaAllowsDailyBriefingButRepairSchemasDoNot() {
        fun routeValues(format: org.json.JSONObject): Set<String> {
            val routes = format.getJSONObject("json_schema")
                .getJSONObject("schema")
                .getJSONObject("properties")
                .getJSONObject("route")
                .getJSONArray("enum")
            return (0 until routes.length()).map(routes::getString).toSet()
        }

        assertTrue(
            routeValues(AgentResponseSchemas.conversationDecisionResponseFormat())
                .contains("DAILY_BRIEFING")
        )
        assertFalse(
            routeValues(AgentResponseSchemas.contextReadRepairResponseFormat())
                .contains("DAILY_BRIEFING")
        )
        assertFalse(
            routeValues(AgentResponseSchemas.contextActionRepairResponseFormat())
                .contains("DAILY_BRIEFING")
        )
        assertTrue(
            ConversationAgentClient.CONTEXT_READ_REPAIR_SYSTEM_PROMPT
                .contains("Never return DAILY_BRIEFING.")
        )
        assertTrue(
            ConversationAgentClient.CONTEXT_ACTION_REPAIR_SYSTEM_PROMPT
                .contains("Never return DAILY_BRIEFING.")
        )
    }

    @Test
    fun promptContainsSemanticBriefingAndExplicitTaskQueryExamples() {
        listOf(
            "Give me my daily briefing.",
            "What is on my schedule today?",
            "Brief me for the day.",
            "What do I need to handle today?",
            "Help me review my day."
        ).forEach { assertTrue(prompt.contains(it)) }
        listOf(
            "Show all my tasks today.",
            "How many tasks do I have tomorrow?",
            "Read the full details for next week."
        ).forEach { assertTrue(prompt.contains(it)) }
        assertTrue(prompt.contains("illustrative semantic DAILY_BRIEFING examples"))
        assertTrue(prompt.contains("remain TASK_COMMAND"))
        assertTrue(prompt.contains("upcoming tasks within seven days"))
        assertTrue(prompt.contains("one deterministic suggested focus"))
        assertTrue(prompt.contains("Do not claim that automatic or scheduled daily briefings are available"))
    }

    @Test
    fun appGuidanceDescribesExpandedOnDemandScopeWithoutAdaptiveClaims() {
        assertTrue(
            homeSource.contains(
                "covering overdue tasks, today's tasks, upcoming tasks within seven days, and one suggested focus"
            )
        )
        assertTrue(
            homeSource.contains(
                "not behavioural learning, habit-based recommendations, priority fields, or calendar integration"
            )
        )
        assertTrue(
            homeSource.contains(
                "Daily briefings are available on demand and are not delivered automatically on a schedule."
            )
        )
        assertFalse(homeSource.contains("scheduled briefing notification", ignoreCase = true))
    }

    @Test
    fun dailyBriefingRouteReturnsBeforeTaskAgentAndUsesProtectedRendering() {
        val routeBranch = homeSource
            .substringAfter("ConversationRoute.DAILY_BRIEFING -> {")
            .substringBefore("ConversationRoute.CONTEXT_READ ->")
        val execution = homeSource
            .substringAfter("private suspend fun executeDailyBriefing(")
            .substringBefore("private fun isDailyBriefingRequestCurrent(")

        assertTrue(routeBranch.contains("executeDailyBriefing("))
        assertTrue(routeBranch.contains("return@launch"))
        assertFalse(routeBranch.contains("agentOrchestrator"))
        assertTrue(execution.contains("DailyBriefingSpeechRenderer.render(snapshot)"))
        assertTrue(execution.contains("AndroidObservationResponseRenderer.render(observation)"))
        assertTrue(execution.contains("renderObservationResponse(observation)"))
        assertTrue(execution.contains("ExecutionOperation.DAILY_BRIEFING"))
        assertFalse(execution.contains("agentOrchestrator"))
        assertFalse(execution.contains("styleTaskQuerySpeech"))
        assertFalse(execution.contains("respondToObservation"))
    }

    @Test
    fun onlySpokenTasksEnterFreshReadOnlyContextInNumberedOrder() {
        val store = ReadOnlyTaskContextStore()
        val before = store.currentGeneration()
        val tasks = (1L..8L).map { id ->
            TaskEntity(
                id = id,
                title = "Task $id",
                dueDate = "28/07/2026",
                dueTime = "${id + 8}:00"
            )
        }
        val briefing = DailyBriefingSnapshotBuilder.build(
            localDate = "27/07/2026",
            rootTasks = tasks,
            subtasksByParentId = emptyMap()
        )

        store.replaceDailyBriefingResults(briefing.spokenRoomTasks)
        val snapshot = store.snapshot()

        assertTrue(snapshot.generation > before)
        assertEquals(TaskContextScope.DAILY_BRIEFING, snapshot.scope)
        assertEquals(
            briefing.spokenItems.map { it.task.title },
            snapshot.items.map { it.title }
        )
        assertEquals(listOf("T1", "T2", "T3", "T4", "T5"), snapshot.items.map { it.ref })
        assertFalse(snapshot.truncated)
        assertFalse(snapshot.items.any { it.title in setOf("Task 6", "Task 7", "Task 8") })

        val restatement = ContextItemRestatementPolicy.resolve(
            normalizedText = "repeat the third one",
            capturedSnapshot = snapshot,
            currentGeneration = store.currentGeneration()
        )
        assertEquals(ContextItemRestatementDisposition.RESOLVED, restatement.disposition)
        assertEquals("T3", restatement.decision?.contextRef)

        store.replaceDailyBriefingResults(briefing.spokenRoomTasks)
        assertTrue(store.currentGeneration() > snapshot.generation)
    }

    @Test
    fun contextualReadAndRescheduleRemainGenerationValidatedAfterBriefing() {
        val store = ReadOnlyTaskContextStore()
        val task = TaskEntity(
            id = 42,
            title = "Medical check-up",
            dueDate = "27/07/2026",
            dueTime = "10:00"
        )
        store.replaceDailyBriefingResults(listOf(task))
        val capture = store.capture()
        val reschedule = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = "T1",
            contextAction = ConversationContextAction.RESCHEDULE,
            confidence = 0.97
        )

        listOf(
            ConversationContextDetail.TIME,
            ConversationContextDetail.DATE,
            ConversationContextDetail.STATUS,
            ConversationContextDetail.SUBTASKS,
            ConversationContextDetail.SUMMARY
        ).forEach { detail ->
            val read = ConversationDecision(
                route = ConversationRoute.CONTEXT_READ,
                contextRef = "T1",
                contextDetail = detail,
                confidence = 0.97
            )
            assertTrue(
                ReadOnlyTaskContextReadValidator.validate(
                    read,
                    capture.snapshot,
                    store.currentGeneration()
                ).isValid
            )
        }
        assertTrue(
            ContextActionDecisionValidator.validate(
                reschedule,
                capture.snapshot,
                store.currentGeneration()
            ).isValid
        )

        store.replaceDailyBriefingResults(listOf(task.copy(id = 43)))

        assertFalse(
            ContextActionDecisionValidator.validate(
                reschedule,
                capture.snapshot,
                store.currentGeneration()
            ).isValid
        )
    }

    @Test
    fun naturalRepeatControlIsValidOnlyWithStoredBriefingSpeech() {
        val valid = QueryReadingControlPolicy.validate(
            move = ConversationQueryReadingMove.REPEAT_LAST,
            interactionState = QueryReadingInteractionState.DAILY_BRIEFING,
            hasActiveSession = false,
            hasAuthoritativeRepeat = true
        )
        val missingSpeech = QueryReadingControlPolicy.validate(
            move = ConversationQueryReadingMove.REPEAT_LAST,
            interactionState = QueryReadingInteractionState.DAILY_BRIEFING,
            hasActiveSession = false,
            hasAuthoritativeRepeat = false
        )

        assertTrue(valid.isValid)
        assertFalse(missingSpeech.isValid)
    }

    @Test
    fun executionChecksRequestAfterRoomAndBeforeEveryUserVisibleEffect() {
        val execution = homeSource
            .substringAfter("private suspend fun executeDailyBriefing(")
            .substringBefore("private fun isDailyBriefingRequestCurrent(")
        val room = execution.indexOf("withContext(Dispatchers.IO)")
        val publish = execution.indexOf("replaceDailyBriefingResults(")
        val memory = execution.indexOf("recordObservationResponse(")
        val repeat = execution.indexOf("authoritativeRepeatState =")
        val speech = execution.indexOf("deliverObservationResponse(")
        val afterRoomGuard =
            execution.indexOf("isDailyBriefingRequestCurrent(requestToken)", room)

        assertTrue(room >= 0)
        assertTrue(afterRoomGuard in (room + 1) until publish)
        assertTrue(execution.lastIndexOf("isDailyBriefingRequestCurrent(requestToken)", memory) < memory)
        assertTrue(execution.lastIndexOf("isDailyBriefingRequestCurrent(requestToken)", repeat) < repeat)
        assertTrue(execution.lastIndexOf("isDailyBriefingRequestCurrent(requestToken)", speech) < speech)
        assertEquals(1, execution.split("recordObservationResponse(").size - 1)
        assertEquals(1, execution.split("authoritativeRepeatState =").size - 1)
        assertEquals(1, execution.split("deliverObservationResponse(").size - 1)
        assertTrue(homeSource.contains("DAILY_BRIEFING_STALE\", \"reason=REQUEST_CHANGED"))
    }

    @Test
    fun exactRepeatUsesStoredSpeechWithoutRoomModelOrContextReplacement() {
        val repeat = homeSource
            .substringAfter("private fun repeatLastAuthoritativeSpeech()")
            .substringBefore("private fun endAssistantConversation()")

        assertTrue(repeat.contains("assistantSession.speak(repeatState.speech"))
        assertTrue(repeat.contains("currentGeneration() == contextGeneration"))
        listOf(
            "AppDatabase",
            "taskDao",
            "getRootTasks",
            "getSubtasks",
            "agentOrchestrator",
            "conversationAgentClient",
            "conversationOrchestrator.process(",
            "replaceDailyBriefingResults",
            "DailyBriefingSnapshotBuilder"
        ).forEach { forbidden -> assertFalse(repeat.contains(forbidden)) }
    }

    @Test
    fun observationCarriesAuthoritativeCountsAndHighlightedFacts() {
        val observation = ExecutionObservation(
            operation = ExecutionOperation.DAILY_BRIEFING,
            outcome = ExecutionOutcome.INFORMATION,
            taskCount = 6,
            overdueTaskCount = 2,
            todayActiveTaskCount = 6,
            upcomingActiveTaskCount = 3,
            additionalTodayTaskCount = 1,
            additionalUpcomingTaskCount = 2,
            dailyBriefingItems = listOf(
                DailyBriefingItem(
                    category = DailyBriefingItemCategory.TODAY,
                    task = ObservedTask("Revision", "27/07/2026", "15:00"),
                    isSuggestedFocus = true
                )
            ),
            dailyBriefingFocusReason = DailyBriefingFocusReason.EARLIEST_TODAY,
            tasks = listOf(ObservedTask("Revision", "27/07/2026", "15:00")),
            listenAgain = true,
            fallbackSpeech = "Deterministic speech"
        )
        val json = org.json.JSONObject(observation.toAgentJson())

        assertEquals("DAILY_BRIEFING", json.getString("operation"))
        assertEquals("INFORMATION", json.getString("outcome"))
        assertEquals(2, json.getInt("overdue_task_count"))
        assertEquals(6, json.getInt("today_active_task_count"))
        assertEquals(3, json.getInt("upcoming_active_task_count"))
        assertEquals(1, json.getInt("additional_today_task_count"))
        assertEquals(2, json.getInt("additional_upcoming_task_count"))
        assertEquals(1, json.getJSONArray("numbered_task_facts").length())
        assertEquals(1, json.getJSONObject("suggested_focus").getInt("ordinal"))
        assertEquals(
            "TODAY",
            json.getJSONObject("suggested_focus").getString("category")
        )
        assertEquals(1, json.getJSONArray("tasks").length())
        assertFalse(json.toString().contains("Deterministic speech"))
        assertFalse(json.toString().contains("task_id"))
        assertEquals(
            "Deterministic speech",
            AndroidObservationResponseRenderer.render(observation).speech
        )
    }
}
