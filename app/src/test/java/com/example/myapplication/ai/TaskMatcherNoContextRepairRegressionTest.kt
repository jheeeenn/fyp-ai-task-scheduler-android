package com.example.myapplication.ai

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMatcherNoContextRepairRegressionTest {
    @Test
    fun evaluationTaskPhraseReachesAcceptedUnambiguousStoredTaskMatch() {
        val evaluation = TaskEntity(id = 41L, title = "evaluation revise")
        val result = TaskMatcher.findBestTaskMatch(
            spokenTitle = "the evaluation task",
            tasks = listOf(
                evaluation,
                TaskEntity(id = 42L, title = "buy groceries")
            )
        )

        assertNotNull(result.bestTask)
        assertEquals(evaluation, result.bestTask)
        assertTrue(result.bestScore >= 0.40)
        assertFalse(result.isAmbiguous)
    }
}
