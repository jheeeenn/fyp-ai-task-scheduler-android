package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeAppNavigationSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
    private val routeSwitch = source.substringAfter("when (conversationDecision.route)")
    private val navigationBranch = routeSwitch
        .substringAfter("ConversationRoute.APP_NAVIGATION -> {")
        .substringBefore("ConversationRoute.SMART_ROUTINE_BUILDER ->")

    @Test
    fun appNavigationIsHandledBeforeDelegatedAndTaskAgentRoutes() {
        val navigation = routeSwitch.indexOf("ConversationRoute.APP_NAVIGATION ->")
        val routines = routeSwitch.indexOf("ConversationRoute.SMART_ROUTINE_BUILDER ->")
        val taskAgent = routeSwitch.indexOf("ConversationRoute.TASK_COMMAND ->")

        assertTrue(navigation >= 0)
        assertTrue(navigation < routines)
        assertTrue(navigation < taskAgent)
        assertTrue(navigationBranch.contains("return@launch"))
    }

    @Test
    fun appNavigationUsesOnlyTheFixedAllowlistedActivityMapping() {
        assertTrue(
            navigationBranch.contains(
                "ConversationNavigationTarget.CREATE_TASK ->\n" +
                    "                                \"Opening task creation.\" to CreateTaskActivity::class.java"
            )
        )
        assertTrue(
            navigationBranch.contains(
                "ConversationNavigationTarget.TODAY_TASKS ->\n" +
                    "                                \"Opening today's tasks.\" to TodayTasksActivity::class.java"
            )
        )
        assertTrue(
            navigationBranch.contains(
                "ConversationNavigationTarget.SCHEDULED_TASKS ->\n" +
                    "                                \"Opening scheduled tasks.\" to MainActivity::class.java"
            )
        )
        assertTrue(
            navigationBranch.contains(
                "ConversationNavigationTarget.SETTINGS ->\n" +
                    "                                \"Opening settings.\" to SettingsActivity::class.java"
            )
        )
        assertTrue(navigationBranch.contains("ConversationNavigationTarget.NONE -> null"))
        assertFalse(navigationBranch.contains("AdvancedSettingsActivity"))
    }

    @Test
    fun appNavigationCommitsBeforeSpeakingAndOpeningAndDoesNotUseTaskExecution() {
        val commit = navigationBranch.indexOf(
            "conversationOrchestrator.commitFinalDecision(conversationDecision)"
        )
        val log = navigationBranch.indexOf("\"HOME_NAVIGATION\"", commit)
        val speakThenOpen = navigationBranch.indexOf("speakThenOpen(navigation.first)")

        assertTrue(commit >= 0)
        assertTrue(commit < log)
        assertTrue(log < speakThenOpen)
        assertTrue(navigationBranch.contains("result=OPENING"))
        assertTrue(navigationBranch.contains("val navigationIntent = Intent(this@HomeActivity, navigation.second).apply"))
        assertTrue(
            navigationBranch.contains(
                "conversationDecision.navigationTarget == ConversationNavigationTarget.CREATE_TASK"
            )
        )
        assertTrue(navigationBranch.contains("putExtra(EXTRA_CREATE_TASK_ASSISTANT_HANDOFF, true)"))
        assertTrue(navigationBranch.contains("startActivity(navigationIntent)"))
        assertFalse(navigationBranch.contains("agentOrchestrator"))
        assertFalse(navigationBranch.contains("TaskMatcher"))
        assertFalse(navigationBranch.contains("AppDatabase"))
        assertFalse(navigationBranch.contains("taskDao"))
    }

    @Test
    fun manualCreateTaskNavigationDoesNotSetAssistantHandoff() {
        val manualCreateButton = source
            .substringAfter("view = btnCreateTask")
            .substringBefore("view = btnScheduledTasks")

        assertTrue(manualCreateButton.contains("startActivity(Intent(this, CreateTaskActivity::class.java))"))
        assertFalse(manualCreateButton.contains("EXTRA_CREATE_TASK_ASSISTANT_HANDOFF"))
    }
}
