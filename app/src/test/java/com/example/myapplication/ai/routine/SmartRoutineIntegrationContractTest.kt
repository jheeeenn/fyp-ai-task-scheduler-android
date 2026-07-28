package com.example.myapplication.ai.routine

import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.agent.LaptopAgentClient
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationQueryReadingMove
import com.example.myapplication.ai.conversation.ConversationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SmartRoutineIntegrationContractTest {
    private val parser = ConversationDecisionParser()

    @Test
    fun routineRequestsUseDedicatedStrictRouteWithOriginalRequestOnly() {
        val decision = parser.parse(
            decisionJson(
                route = "SMART_ROUTINE_BUILDER",
                taskText = "create my morning routine for tomorrow"
            )
        )

        assertEquals(ConversationRoute.SMART_ROUTINE_BUILDER, decision.route)
        assertEquals("create my morning routine for tomorrow", decision.taskText)
        assertEquals("", decision.reply)
        assertEquals(ConversationContextDetail.NONE, decision.contextDetail)
        assertEquals(ConversationContextAction.NONE, decision.contextAction)
        assertEquals(ConversationQueryReadingMove.NONE, decision.queryReadingMove)
        assertEquals(TaskQueryPresentation.NONE, decision.queryPresentationHint)
        listOf(
            decisionJson("SMART_ROUTINE_BUILDER", "", confidence = 0.97),
            decisionJson("SMART_ROUTINE_BUILDER", "routine", reply = "I prepared it"),
            decisionJson("SMART_ROUTINE_BUILDER", "routine", confidence = 0.79),
            decisionJson("SMART_ROUTINE_BUILDER", "routine", listenAgain = false)
        ).forEach {
            assertThrows(Exception::class.java) { parser.parse(it) }
        }
    }

    @Test
    fun routingPromptSeparatesRoutineSingleTaskBreakdownAndDiscussion() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        assertTrue(prompt.contains("SMART_ROUTINE_BUILDER"))
        assertTrue(prompt.contains("\"Create my morning routine.\""))
        assertTrue(prompt.contains("\"Set up a routine for tomorrow.\""))
        assertTrue(prompt.contains("one ordinary task"))
        assertTrue(prompt.contains("task breakdown"))
        assertTrue(prompt.contains("general discussion or guidance about routines"))
        assertTrue(prompt.contains("SMART_ROUTINE_BUILDER is routing only"))
        assertTrue(prompt.contains("Do not extract steps"))
        assertTrue(prompt.contains("original normalized request"))
        assertTrue(prompt.contains("\"Create a task called revision\" is TASK_COMMAND"))
        assertTrue(prompt.contains("\"Break down my project task\""))
    }

    @Test
    fun ordinaryCreateAndBreakdownRemainInExistingTaskAgentContract() {
        val create = parser.parse(
            decisionJson("TASK_COMMAND", "create a task called revision")
        )
        val breakdown = parser.parse(
            decisionJson("TASK_COMMAND", "break down my project task")
        )
        assertEquals(ConversationRoute.TASK_COMMAND, create.route)
        assertEquals(ConversationRoute.TASK_COMMAND, breakdown.route)
        val taskPrompt = LaptopAgentClient.SYSTEM_PROMPT
        assertTrue(taskPrompt.contains("Use CREATE_TASK for new tasks or reminders."))
        assertTrue(taskPrompt.contains("Use BREAKDOWN_TASK only when"))
        assertFalse(taskPrompt.contains("SMART_ROUTINE_BUILDER"))
    }

    @Test
    fun homeDispatchRequiresConfirmationBeforeTheOnlyRoutineSaveCall() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val followUp = source
            .substringAfter("private fun handleRoutineFollowUp")
            .substringBefore("private fun handleRoutineDraftUpdate")
        val save = source
            .substringAfter("private fun savePendingRoutine")
            .substringBefore("private fun beginAssistantRequest")

        assertTrue(source.contains("ConversationRoute.SMART_ROUTINE_BUILDER ->"))
        assertTrue(source.contains("agentOrchestrator.processRoutine(normalizedRequest)"))
        assertTrue(followUp.contains("RoutineFollowUpMove.Confirm ->"))
        assertTrue(followUp.contains("savePendingRoutine()"))
        assertFalse(followUp.substringBefore("RoutineFollowUpMove.Confirm").contains("insertRootTasksAtomically"))
        assertTrue(save.contains("routineDraftController.markSaving()"))
        assertTrue(save.contains("dao.insertRootTasksAtomically(tasks)"))
        assertTrue(save.contains("ReminderHelper.scheduleReminderFromTask"))
    }

    @Test
    fun routineRoutingAndExtractionAreBoundToTheAssistantRequestToken() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val route = source
            .substringAfter("ConversationRoute.SMART_ROUTINE_BUILDER ->")
            .substringBefore("ConversationRoute.DAILY_BRIEFING ->")
        val extraction = source
            .substringAfter("private suspend fun handleSmartRoutineBuilder")
            .substringBefore("private fun handleRoutineFollowUp")
        val delivery = source
            .substringAfter("private fun handleRoutineDraftUpdate")
            .substringBefore("private fun logRoutineDraftState")

        assertTrue(
            route.indexOf("isAssistantRequestCurrent(requestToken)") in
                0 until route.indexOf("commitFinalDecision")
        )
        assertTrue(route.contains("handleSmartRoutineBuilder(normalized, requestToken)"))
        assertTrue(extraction.contains("requestToken: AssistantRequestToken"))
        assertTrue(
            extraction.indexOf("isAssistantRequestCurrent(requestToken)") <
                extraction.indexOf("routineDraftController.beginExtraction()")
        )
        assertTrue(
            extraction.indexOf(
                "isAssistantRequestCurrent(requestToken)",
                extraction.indexOf("agentOrchestrator.processRoutine")
            ) > extraction.indexOf("agentOrchestrator.processRoutine")
        )
        assertTrue(extraction.contains("routineDraftController.discardExtraction(generation)"))
        assertTrue(extraction.contains("routineDraftController.applyExtraction("))
        assertTrue(delivery.contains("if (!isAssistantRequestCurrent(requestToken)) return"))
    }

    @Test
    fun savingIsTerminalSingleResultAndSessionStopCannotReportCancellation() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val followUp = source
            .substringAfter("private fun handleRoutineFollowUp")
            .substringBefore("private fun handleRoutineDraftUpdate")
        val cancelled = source
            .substringAfter("override fun onAssistantCancelled()")
            .substringBefore("override fun onAssistantSessionStopped()")
        val stopped = source
            .substringAfter("override fun onAssistantSessionStopped()")
            .substringBefore("override fun onResume()")
        val save = source
            .substringAfter("private fun savePendingRoutine")
            .substringBefore("private fun beginAssistantRequest")

        assertTrue(
            followUp.indexOf("RoutineDraftState.SAVING") <
                followUp.indexOf("launchRoutineSemanticFollowUp")
        )
        assertTrue(followUp.contains("The confirmed routine is already being saved."))
        assertTrue(
            cancelled.contains(
                "routineDraftController.state != RoutineDraftState.SAVING"
            )
        )
        assertTrue(
            stopped.contains(
                "routineDraftController.state != RoutineDraftState.SAVING"
            )
        )
        assertTrue(save.contains("routineDraftController.completeSaving(pendingSave.generation)"))
        assertTrue(save.contains("ROUTINE_SAVE_STALE"))
        assertTrue(save.contains("assistantSession.assistantSessionActive"))
        assertTrue(save.contains("result.insertedCount == result.taskCount"))
        assertTrue(save.indexOf("completeSaving") < save.indexOf("\"ROUTINE_SAVE\""))
    }

    @Test
    fun cancelAndRejectBeforeSavingNeverReachPersistence() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val followUp = source
            .substringAfter("private fun handleRoutineFollowUp")
            .substringBefore("private fun handleRoutineDraftUpdate")
        val cancellationBranch = followUp
            .substringAfter("RoutineFollowUpMove.Cancel")
            .substringBefore("return when")

        assertTrue(cancellationBranch.contains("RoutineFollowUpMove.Reject"))
        assertTrue(cancellationBranch.contains("cancelPendingRoutine()"))
        assertFalse(cancellationBranch.contains("savePendingRoutine()"))
        assertFalse(cancellationBranch.contains("insertRootTasksAtomically"))
    }

    @Test
    fun daoAddsOnlyAtomicListInsertionWhileEntityAndDatabaseSchemaStayUnchanged() {
        val dao = File("src/main/java/com/example/myapplication/data/TaskDao.kt").readText()
        val entity = File("src/main/java/com/example/myapplication/data/TaskEntity.kt").readText()
        val database = File("src/main/java/com/example/myapplication/data/AppDatabase.kt").readText()

        assertTrue(dao.contains("@Transaction"))
        assertTrue(dao.contains("insertRootTasksAtomically"))
        assertTrue(dao.contains("require(tasks.all { it.parentTaskId == null })"))
        assertTrue(dao.contains("return insertAll(tasks)"))
        assertEquals(7, Regex("""^\s*val\s+\w+:""", RegexOption.MULTILINE).findAll(entity).count())
        assertFalse(entity.contains("routine"))
        assertTrue(database.contains("version = 6"))
        assertFalse(database.contains("version = 7"))
    }

    @Test
    fun protectedFlowsAndRoutineNonRecurrenceBoundaryRemainPresent() {
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val routineFiles = File("src/main/java/com/example/myapplication/ai/routine")
            .walkTopDown()
            .filter(File::isFile)
            .joinToString("\n", transform = File::readText)

        listOf(
            "AiIntent.CREATE_TASK.name ->",
            "AiIntent.BREAKDOWN_TASK.name ->",
            "ConversationRoute.DAILY_BRIEFING ->",
            "handleQueryTask(",
            "handleContextItemRead(",
            "handleContextItemRestatement("
        ).forEach { assertTrue("Missing protected flow $it", home.contains(it)) }
        assertFalse(routineFiles.contains("WorkManager"))
        assertFalse(routineFiles.contains("CalendarContract"))
        assertFalse(routineFiles.contains("recurrence", ignoreCase = true))
        assertFalse(routineFiles.contains("subtaskOrder"))
    }

    @Test
    fun guidanceStatesReviewExactScheduleConfirmationAndNoAutomaticRecurrence() {
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        assertTrue(home.contains("one-time routine containing 2 to 5 scheduled tasks"))
        assertTrue(home.contains("Every routine task requires an exact future date and time"))
        assertTrue(home.contains("reviews the complete routine before creation"))
        assertTrue(home.contains("save only after explicit confirmation"))
        assertTrue(home.contains("does not permanently recur routines"))
        assertTrue(home.contains("automatically generate future routine instances"))
    }

    private fun decisionJson(
        route: String,
        taskText: String,
        reply: String = "",
        confidence: Double = 0.97,
        listenAgain: Boolean = true
    ): String = """
        {
          "route":"$route",
          "task_text":"$taskText",
          "reply":"$reply",
          "context_ref":"",
          "context_detail":"NONE",
          "context_action":"NONE",
          "query_reading_move":"NONE",
          "query_presentation_hint":"NONE",
          "confidence":$confidence,
          "listen_again":$listenAgain
        }
    """.trimIndent()
}
