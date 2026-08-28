package com.example.myapplication.ai.conversation

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTopLevelRouteConsistencyGuardTest {
    @Test
    fun newContradictionsAlwaysSelectGeneralRepairAndPromptsRemainBounded() {
        listOf(
            ConversationDecisionFailureCode.APP_NAVIGATION_MISROUTED,
            ConversationDecisionFailureCode.SAVED_ROUTINE_LIST_MISROUTED,
            ConversationDecisionFailureCode.SAVED_ROUTINE_READ_MISROUTED
        ).forEach { failure ->
            assertEquals(
                ConversationRepairProfile.GENERAL,
                ConversationRepairProfileSelector.select(
                    failureCode = failure.name,
                    failedRoute = ConversationRoute.CONTEXT_ACTION,
                    suppliedTemporaryRefCount = 0,
                    validatedFocusAvailable = false
                )
            )
        }

        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        assertTrue(prompt.contains("APP_NAVIGATION is semantic routing only"))
        assertTrue(prompt.contains("Android owns the fixed screen mapping"))
        assertTrue(prompt.contains("navigation_target is NONE for every route other than APP_NAVIGATION"))
        assertTrue(prompt.contains("Do I have any routines?"))
        assertTrue(prompt.contains("List my saved routines."))
        assertTrue(prompt.contains("What is my evening routine?"))
        assertTrue(prompt.contains("What is an evening routine?"))
        assertTrue(prompt.contains("Morning, evening, or daily wording alone never implies DAILY_BRIEFING"))

        val repairSource = java.io.File(
            "src/main/java/com/example/myapplication/ai/conversation/ConversationAgentClient.kt"
        ).readText()
        assertTrue(repairSource.contains("SAVED_ROUTINE_READ_MISROUTED repair rule:"))
        assertTrue(repairSource.contains("SavedRoutineSemanticOrchestrator decides READ_DETAILS"))
        assertTrue(repairSource.contains("Use no Room IDs or invented routine records"))
    }

    @Test
    fun explicitNavigationEvidenceIsBoundedAndRejectsFalsePositives() {
        val positives = mapOf(
            "Open settings" to ConversationNavigationTarget.SETTINGS,
            "Go to settings" to ConversationNavigationTarget.SETTINGS,
            "Open the setting page" to ConversationNavigationTarget.SETTINGS,
            "Open create task" to ConversationNavigationTarget.CREATE_TASK,
            "Open the create task page" to ConversationNavigationTarget.CREATE_TASK,
            "Go to task creation" to ConversationNavigationTarget.CREATE_TASK,
            "Open today's tasks" to ConversationNavigationTarget.TODAY_TASKS,
            "Open today list page" to ConversationNavigationTarget.TODAY_TASKS,
            "Go to today's task list" to ConversationNavigationTarget.TODAY_TASKS,
            "Open scheduled tasks" to ConversationNavigationTarget.SCHEDULED_TASKS,
            "Open the scheduled task page" to ConversationNavigationTarget.SCHEDULED_TASKS,
            "Go to scheduled tasks" to ConversationNavigationTarget.SCHEDULED_TASKS
        )
        positives.forEach { (utterance, target) ->
            assertEquals(
                utterance,
                target,
                ConversationTopLevelRouteConsistencyGuard.explicitNavigationTarget(utterance)
            )
        }

        listOf(
            "How do I open settings?",
            "Where are settings?",
            "What tasks do I have today?",
            "Read today's tasks",
            "Create a task",
            "Create a task called Dentist tomorrow at 9 PM",
            "Turn on high contrast",
            "What is high contrast set to?"
        ).forEach { utterance ->
            assertEquals(
                utterance,
                null,
                ConversationTopLevelRouteConsistencyGuard.explicitNavigationTarget(utterance)
            )
        }
    }

    @Test
    fun navigationMisroutesUseExactlyOneGeneralRepair() = runBlocking {
        val cases = listOf(
            NavigationCase("Open settings", "SETTINGS_ACTION", "SETTINGS"),
            NavigationCase("Open the setting page", "CONTEXT_ACTION", "SETTINGS"),
            NavigationCase("Open create task", "TASK_COMMAND", "CREATE_TASK"),
            NavigationCase("Open today list page", "TASK_COMMAND", "TODAY_TASKS"),
            NavigationCase("Open scheduled task page", "CONTEXT_ACTION", "SCHEDULED_TASKS")
        )

        cases.forEach { case ->
            val client = RepairClient(
                primary = decisionJson(
                    route = case.primaryRoute,
                    settingAction = "NONE",
                    contextAction = "NONE"
                ),
                repair = decisionJson(
                    route = "APP_NAVIGATION",
                    navigationTarget = case.expectedTarget,
                    listenAgain = false
                )
            )
            val decision = orchestrator(client).process(case.utterance, "Home guidance")

            assertEquals(ConversationRoute.APP_NAVIGATION, decision.route)
            assertEquals(
                ConversationNavigationTarget.valueOf(case.expectedTarget),
                decision.navigationTarget
            )
            assertEquals("", decision.contextRef)
            assertEquals(1, client.generalRepairCalls)
            assertEquals(0, client.compactRepairCalls)
            assertEquals(
                ConversationDecisionFailureCode.APP_NAVIGATION_MISROUTED.name,
                client.failureCode
            )
            assertEquals(ConversationRoute.valueOf(case.primaryRoute), client.failedRoute)
            assertTrue(
                client.repairContext.contains(
                    ConversationRepairProfileSelector.marker(ConversationRepairProfile.GENERAL)
                )
            )
        }
    }

    @Test
    fun scheduledNavigationContradictionPrecedesMissingContextAuthority() = runBlocking {
        val client = RepairClient(
            primary = decisionJson(route = "CONTEXT_ACTION", contextAction = "NONE"),
            repair = decisionJson(
                route = "APP_NAVIGATION",
                navigationTarget = "SCHEDULED_TASKS",
                listenAgain = false
            )
        )

        val decision = orchestrator(client).process("Open scheduled task page", "Home guidance")

        assertEquals(ConversationRoute.APP_NAVIGATION, decision.route)
        assertEquals(
            ConversationDecisionFailureCode.APP_NAVIGATION_MISROUTED.name,
            client.failureCode
        )
        assertFalse(client.repairContext.contains("INVALID_CONTEXT_REF repair rule"))
        assertEquals(1, client.generalRepairCalls)
        assertEquals(0, client.compactRepairCalls)
    }

    @Test
    fun navigationFalsePositivesKeepTheirValidPrimaryRoutesWithoutRepair() = runBlocking {
        val cases = listOf(
            Triple("How do I open settings?", "DIRECT_REPLY", "NONE"),
            Triple("What tasks do I have today?", "TASK_COMMAND", "NONE"),
            Triple("Read today's tasks", "TASK_COMMAND", "NONE"),
            Triple("Create a task", "TASK_COMMAND", "NONE"),
            Triple("Create a task called Dentist tomorrow at 9 PM", "TASK_COMMAND", "NONE"),
            Triple("Turn on high contrast", "SETTINGS_ACTION", "HIGH_CONTRAST_ON"),
            Triple("What is high contrast set to?", "SETTINGS_READ", "HIGH_CONTRAST")
        )

        cases.forEach { (utterance, route, authority) ->
            val client = RepairClient(
                primary = decisionJson(
                    route = route,
                    reply = if (route == "DIRECT_REPLY") "Open Settings from Home." else "",
                    settingAction = if (route == "SETTINGS_ACTION") authority else "NONE",
                    settingTarget = if (route == "SETTINGS_READ") authority else "NONE"
                ),
                repair = ""
            )
            val decision = orchestrator(client).process(utterance, "Home guidance")

            assertEquals(ConversationRoute.valueOf(route), decision.route)
            assertEquals(0, client.generalRepairCalls)
            assertEquals(0, client.compactRepairCalls)
        }
    }

    @Test
    fun savedRoutineListMisroutesUseGeneralRepairWhileCorrectRoutesDoNot() = runBlocking {
        listOf(
            "Do I have any routines?" to "TASK_COMMAND",
            "What routines have I saved?" to "DIRECT_REPLY",
            "List my saved routines" to "TASK_COMMAND"
        ).forEach { (utterance, primaryRoute) ->
            assertTrue(
                ConversationTopLevelRouteConsistencyGuard.hasSavedRoutineListEvidence(utterance)
            )
            val client = RepairClient(
                primary = decisionJson(
                    route = primaryRoute,
                    reply = if (primaryRoute == "DIRECT_REPLY") "You have routines." else ""
                ),
                repair = decisionJson(route = "SAVED_ROUTINE_ACTION")
            )

            val decision = orchestrator(client).process(utterance, "Home guidance")

            assertEquals(ConversationRoute.SAVED_ROUTINE_ACTION, decision.route)
            assertEquals(utterance, decision.taskText)
            assertEquals(1, client.generalRepairCalls)
            assertEquals(0, client.compactRepairCalls)
            assertEquals(
                ConversationDecisionFailureCode.SAVED_ROUTINE_LIST_MISROUTED.name,
                client.failureCode
            )
        }

        val correct = RepairClient(
            primary = decisionJson(route = "SAVED_ROUTINE_ACTION"),
            repair = ""
        )
        assertEquals(
            ConversationRoute.SAVED_ROUTINE_ACTION,
            orchestrator(correct).process("Show my saved routines", "Home guidance").route
        )
        assertEquals(0, correct.generalRepairCalls)

        listOf(
            "Do I have any saved routines?",
            "What routines do I have?",
            "List my routines.",
            "Show my saved routines."
        ).forEach {
            assertTrue(ConversationTopLevelRouteConsistencyGuard.hasSavedRoutineListEvidence(it))
        }
    }

    @Test
    fun routineListGuardDoesNotMatchBuildRunDeleteOrGuidance() = runBlocking {
        val cases = listOf(
            "Build me a morning routine" to "SMART_ROUTINE_BUILDER",
            "Create a morning routine" to "SMART_ROUTINE_BUILDER",
            "Run my morning routine" to "SAVED_ROUTINE_ACTION",
            "Use my morning routine tomorrow" to "SAVED_ROUTINE_ACTION",
            "Delete my morning routine" to "SAVED_ROUTINE_ACTION",
            "How do routines work?" to "DIRECT_REPLY"
        )

        cases.forEach { (utterance, route) ->
            assertFalse(
                ConversationTopLevelRouteConsistencyGuard.hasSavedRoutineListEvidence(utterance)
            )
            val client = RepairClient(
                primary = decisionJson(
                    route = route,
                    reply = if (route == "DIRECT_REPLY") "Routines are reusable templates." else ""
                ),
                repair = ""
            )
            assertEquals(
                ConversationRoute.valueOf(route),
                orchestrator(client).process(utterance, "Home guidance").route
            )
            assertEquals(0, client.generalRepairCalls)
        }
    }

    @Test
    fun physicalSavedRoutineReadFailureUsesRecentBoundedContextAndOneGeneralRepair() =
        runBlocking {
            val client = RepairClient(
                primary = decisionJson(route = "DAILY_BRIEFING"),
                repair = decisionJson(route = "SAVED_ROUTINE_ACTION")
            )
            val orchestrator = orchestrator(client)
            orchestrator.commitFinalDecision(
                ConversationDecision(
                    route = ConversationRoute.SAVED_ROUTINE_ACTION,
                    confidence = 0.97,
                    listenAgain = true
                )
            )

            val decision = orchestrator.process(
                "What is the evening routine",
                "Home guidance"
            )

            assertEquals(ConversationRoute.SAVED_ROUTINE_ACTION, decision.route)
            assertEquals("What is the evening routine", decision.taskText)
            assertEquals(1, client.generalRepairCalls)
            assertEquals(0, client.compactRepairCalls)
            assertEquals(
                ConversationDecisionFailureCode.SAVED_ROUTINE_READ_MISROUTED.name,
                client.failureCode
            )
            assertEquals(ConversationRoute.DAILY_BRIEFING, client.failedRoute)
            assertTrue(
                client.repairContext.contains(
                    "Recent saved-routine routing context available: true"
                )
            )
        }

    @Test
    fun correctSavedRoutineReadPrimaryNeedsNoRepairWithRecentContext() = runBlocking {
        val client = RepairClient(
            primary = decisionJson(route = "SAVED_ROUTINE_ACTION"),
            repair = ""
        )
        val orchestrator = orchestrator(client)
        orchestrator.commitFinalDecision(
            ConversationDecision(
                route = ConversationRoute.SAVED_ROUTINE_ACTION,
                confidence = 0.97,
                listenAgain = true
            )
        )

        val decision = orchestrator.process("What is the evening routine", "Home guidance")

        assertEquals(ConversationRoute.SAVED_ROUTINE_ACTION, decision.route)
        assertEquals(0, client.generalRepairCalls)
        assertEquals(0, client.compactRepairCalls)
    }

    @Test
    fun possessiveSavedRoutineReadsAreStrongWithoutPriorContext() = runBlocking {
        listOf(
            "What is my evening routine?" to "DIRECT_REPLY",
            "Read my evening routine." to "DAILY_BRIEFING",
            "What is in my evening routine?" to "DIRECT_REPLY",
            "Tell me about my evening routine." to "DAILY_BRIEFING",
            "Describe my evening routine." to "DIRECT_REPLY",
            "Explain my evening routine." to "DIRECT_REPLY",
            "What does my evening routine contain?" to "DAILY_BRIEFING",
            "What does my evening routine include?" to "DIRECT_REPLY"
        ).forEach { (utterance, primaryRoute) ->
            assertTrue(
                utterance,
                ConversationTopLevelRouteConsistencyGuard.hasSavedRoutineReadEvidence(utterance)
            )
            val client = RepairClient(
                primary = decisionJson(
                    route = primaryRoute,
                    reply = if (primaryRoute == "DIRECT_REPLY") "Generic routine advice." else ""
                ),
                repair = decisionJson(route = "SAVED_ROUTINE_ACTION")
            )

            val decision = orchestrator(client).process(utterance, "Home guidance")

            assertEquals(ConversationRoute.SAVED_ROUTINE_ACTION, decision.route)
            assertEquals(utterance, decision.taskText)
            assertEquals(1, client.generalRepairCalls)
            assertEquals(0, client.compactRepairCalls)
            assertEquals(
                ConversationDecisionFailureCode.SAVED_ROUTINE_READ_MISROUTED.name,
                client.failureCode
            )
        }
    }

    @Test
    fun genericRoutineGuidanceAndOtherRoutineRoutesDoNotTriggerReadGuard() = runBlocking {
        val cases = listOf(
            "What is a morning routine?" to "DIRECT_REPLY",
            "What is an evening routine?" to "DIRECT_REPLY",
            "What should an evening routine include?" to "DIRECT_REPLY",
            "How does a morning routine work?" to "DIRECT_REPLY",
            "Why is a morning routine useful?" to "DIRECT_REPLY",
            "Can you explain what a routine is?" to "DIRECT_REPLY",
            "Can you suggest a bedtime routine?" to "DIRECT_REPLY",
            "How do routines work?" to "DIRECT_REPLY",
            "Give me my daily briefing" to "DAILY_BRIEFING",
            "Build me a morning routine" to "SMART_ROUTINE_BUILDER",
            "Create a morning routine" to "SMART_ROUTINE_BUILDER",
            "Run my morning routine" to "SAVED_ROUTINE_ACTION",
            "Use my morning routine tomorrow" to "SAVED_ROUTINE_ACTION",
            "Delete my morning routine" to "SAVED_ROUTINE_ACTION"
        )

        cases.forEach { (utterance, route) ->
            assertFalse(
                utterance,
                ConversationTopLevelRouteConsistencyGuard.hasSavedRoutineReadEvidence(utterance)
            )
            val client = RepairClient(
                primary = decisionJson(
                    route = route,
                    reply = if (route == "DIRECT_REPLY") "Routine guidance." else ""
                ),
                repair = ""
            )

            assertEquals(
                ConversationRoute.valueOf(route),
                orchestrator(client).process(utterance, "Home guidance").route
            )
            assertEquals(0, client.generalRepairCalls)
            assertEquals(0, client.compactRepairCalls)
        }

        assertFalse(
            ConversationTopLevelRouteConsistencyGuard.hasSavedRoutineReadEvidence(
                "What is the evening routine?"
            )
        )
        assertTrue(
            ConversationTopLevelRouteConsistencyGuard.hasSavedRoutineReadEvidence(
                "What is the evening routine?",
                recentSavedRoutineContext = true
            )
        )
    }

    @Test
    fun topLevelGuardContainsNoStorageOrExecutionAuthority() {
        val source = java.io.File(
            "src/main/java/com/example/myapplication/ai/conversation/" +
                "ConversationTopLevelRouteConsistencyGuard.kt"
        ).readText()

        assertFalse(source.contains("Room"))
        assertFalse(source.contains("RoutineDao"))
        assertFalse(source.contains("routineId"))
        assertFalse(source.contains("SavedRoutineSemanticOrchestrator"))
        assertFalse(source.contains("READ_DETAILS"))
    }

    @Test
    fun repeatedTopLevelContradictionFailsClosedAfterOneRepair() {
        val client = RepairClient(
            primary = decisionJson(route = "TASK_COMMAND"),
            repair = decisionJson(route = "TASK_COMMAND")
        )

        assertThrows(ConversationOrchestratorException::class.java) {
            runBlocking { orchestrator(client).process("Open create task", "Home guidance") }
        }
        assertEquals(1, client.generalRepairCalls)
        assertEquals(0, client.compactRepairCalls)
    }

    @Test
    fun repeatedSavedRoutineReadContradictionFailsClosedAfterOneGeneralRepair() {
        val client = RepairClient(
            primary = decisionJson(route = "DIRECT_REPLY", reply = "Routine guidance."),
            repair = decisionJson(route = "DAILY_BRIEFING")
        )

        assertThrows(ConversationOrchestratorException::class.java) {
            runBlocking {
                orchestrator(client).process("What is my evening routine?", "Home guidance")
            }
        }
        assertEquals(1, client.generalRepairCalls)
        assertEquals(0, client.compactRepairCalls)
    }

    private fun orchestrator(client: RepairClient) = ConversationOrchestrator(
        conversationAgentClient = client,
        parser = ConversationDecisionParser()
    )

    private data class NavigationCase(
        val utterance: String,
        val primaryRoute: String,
        val expectedTarget: String
    )

    private class RepairClient(
        private val primary: String,
        private val repair: String
    ) : ConversationAgentClient(null) {
        var generalRepairCalls = 0
        var compactRepairCalls = 0
        var failureCode = ""
        var failedRoute: ConversationRoute? = null
        var repairContext = ""

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String = primary

        override suspend fun processRepair(
            userText: String,
            appContextSummary: String,
            failureCode: String,
            failedRoute: ConversationRoute?
        ): String {
            generalRepairCalls++
            this.failureCode = failureCode
            this.failedRoute = failedRoute
            repairContext = appContextSummary
            return repair
        }

        override suspend fun processTaskCommandRouteRepair(
            normalizedText: String,
            failureCode: String,
            failedRoute: ConversationRoute
        ): String {
            compactRepairCalls++
            return ""
        }
    }

    private companion object {
        fun decisionJson(
            route: String,
            navigationTarget: String = "NONE",
            reply: String = "",
            contextAction: String = "NONE",
            settingAction: String = "NONE",
            settingTarget: String = "NONE",
            listenAgain: Boolean = true
        ): String = JSONObject()
            .put("route", route)
            .put("navigation_target", navigationTarget)
            .put("task_text", "")
            .put("reply", reply)
            .put("context_ref", "")
            .put("context_detail", "NONE")
            .put("context_action", contextAction)
            .put("setting_action", settingAction)
            .put("setting_target", settingTarget)
            .put("query_reading_move", "NONE")
            .put("query_presentation_hint", "NONE")
            .put("confidence", 0.97)
            .put("listen_again", listenAgain)
            .toString()
    }
}
