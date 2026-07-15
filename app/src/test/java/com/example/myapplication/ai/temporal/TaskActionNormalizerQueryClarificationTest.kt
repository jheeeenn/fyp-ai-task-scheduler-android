package com.example.myapplication.ai.temporal

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.agent.TaskActionNormalizer
import com.example.myapplication.ai.agent.TaskAgentResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskActionNormalizerQueryClarificationTest {
    @Test fun propagatesQueryClarificationFields() {
        val command = TaskActionNormalizer().normalize(
            TaskAgentResponse(
                action = AiIntent.QUERY_TASK.name,
                need_clarification = true,
                missing_fields = listOf(" date ", "time", "date", "")
            )
        )

        assertEquals(AiIntent.QUERY_TASK.name, command.intent)
        assertTrue(command.needsClarification)
        assertEquals(listOf("date", "time"), command.missingFields)
    }
}
