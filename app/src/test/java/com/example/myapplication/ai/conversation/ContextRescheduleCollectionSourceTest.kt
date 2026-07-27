package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContextRescheduleCollectionSourceTest {
    private val home =
        File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun emptyExtractionRequiresExplicitEditorCollection() {
        val branch = home
            .substringAfter("ConversationRoute.CONTEXT_ACTION ->")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")

        assertTrue(branch.contains("!hasDateChange && !hasTimeChange"))
        assertTrue(branch.contains("requiresTemporalCollection = clarificationRequired"))
        assertTrue(branch.contains("openContextActionEditScreen("))
    }

    @Test
    fun dateOnlyAndTimeOnlyChangesDoNotRequireBothFields() {
        val branch = home
            .substringAfter("val hasDateChange =")
            .substringBefore("if (readOnlyTaskContextStore.currentGeneration()")

        assertTrue(branch.contains("hasDateChange || hasTimeChange"))
        assertFalse(branch.contains("hasDateChange && hasTimeChange"))
    }

    @Test
    fun extractionLogContainsOnlyBooleanOutcomeMetadata() {
        val log = home
            .substringAfter("\"HOME_CONTEXT_RESCHEDULE_EXTRACTION\"")
            .substringBefore(")\n")

        assertTrue(log.contains("hasDateChange="))
        assertTrue(log.contains("hasTimeChange="))
        assertTrue(log.contains("clarificationRequired="))
        listOf(
            "task.title",
            "task.id",
            "privateTaskId",
            "newDateText",
            "newTimeText",
            "normalized",
            "date=",
            "time="
        ).forEach { forbidden ->
            assertFalse("log contains $forbidden", log.contains(forbidden))
        }
    }

    @Test
    fun collectionIntentKeepsAuthoritativeTaskAndDoesNotSave() {
        val helper = home
            .substringAfter("private suspend fun openContextActionEditScreen(")
            .substringBefore("private fun todayDateString()")

        assertTrue(helper.contains("putExtra(\"task_id\", task.id)"))
        assertTrue(helper.contains("\"reschedule_collection_required\""))
        assertTrue(helper.contains("What date or time would you like to use").not())
        assertFalse(helper.contains("updateTask("))
        assertFalse(helper.contains("saveTask("))
    }
}
