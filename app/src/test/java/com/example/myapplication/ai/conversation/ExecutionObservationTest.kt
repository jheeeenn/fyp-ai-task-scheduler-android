package com.example.myapplication.ai.conversation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExecutionObservationTest {
    @Test fun serializationContainsUserFactsAndExcludesFallbackAndIds() {
        val json = ExecutionObservation(
            operation = ExecutionOperation.DELETE_TASK,
            outcome = ExecutionOutcome.AMBIGUOUS,
            taskTitle = "essay",
            taskCount = 2,
            tasks = listOf(ObservedTask("essay draft", "20/07/2026", "09:00")),
            choices = listOf("essay draft", "essay final"),
            requiredInput = RequiredInput.TASK_CHOICE,
            allowedUserMoves = listOf(AllowedUserMove.SELECT_OPTION, AllowedUserMove.CANCEL),
            listenAgain = true,
            fallbackSpeech = "fallback",
            fallbackHint = "hint"
        ).toAgentJson()
        val parsed = JSONObject(json)
        assertTrue(json.contains("essay draft"))
        assertTrue(json.contains("TASK_CHOICE"))
        assertTrue(json.contains("SELECT_OPTION"))
        assertEquals("REQUEST_CLARIFICATION", parsed.getString("expected_response_type"))
        assertFalse(json.contains("fallback"))
        assertFalse(parsed.has("fallbackSpeech"))
        assertFalse(parsed.has("fallbackHint"))
        assertFalse(parsed.has("fallback_speech"))
        assertFalse(parsed.has("fallback_hint"))
        assertFalse(json.contains("task_id"))
        assertFalse(json.contains("Room"))
    }

    @Test fun outcomeMappingPreservesAuthoritativeResponseCategories() {
        assertEquals(ConversationResponseType.INFORMATION, ExecutionOutcome.NOT_FOUND.toConversationResponseType())
        assertEquals(ConversationResponseType.ACKNOWLEDGEMENT, ExecutionOutcome.CANCELLED.toConversationResponseType())
        assertEquals(ConversationResponseType.ERROR, ExecutionOutcome.FAILURE.toConversationResponseType())
        assertEquals(ConversationResponseType.ERROR, ExecutionOutcome.REJECTED.toConversationResponseType())
        assertEquals(ConversationResponseType.REQUEST_CONFIRMATION, ExecutionOutcome.NEEDS_CONFIRMATION.toConversationResponseType())
        assertEquals(ConversationResponseType.REQUEST_CLARIFICATION, ExecutionOutcome.AMBIGUOUS.toConversationResponseType())
    }

    @Test fun notFoundAndCancelledSerializeExpectedResponseType() {
        fun serializedType(outcome: ExecutionOutcome): String = JSONObject(
            ExecutionObservation(
                operation = ExecutionOperation.DELETE_TASK,
                outcome = outcome,
                listenAgain = true,
                fallbackSpeech = "Android fallback"
            ).toAgentJson()
        ).getString("expected_response_type")

        assertEquals("INFORMATION", serializedType(ExecutionOutcome.NOT_FOUND))
        assertEquals("ACKNOWLEDGEMENT", serializedType(ExecutionOutcome.CANCELLED))
    }

    @Test fun structuredTaskFactsRemainAgentVisibleWithoutInternalIds() {
        val json = JSONObject(
            ExecutionObservation(
                operation = ExecutionOperation.QUERY_TASK,
                outcome = ExecutionOutcome.INFORMATION,
                taskCount = 1,
                dateText = "tomorrow",
                detail = "After giving the task details, ask whether the user needs anything else.",
                tasks = listOf(
                    ObservedTask(
                        title = "Submit report",
                        dueDate = "20/07/2026",
                        dueTime = "09:00",
                        isDone = false,
                        subtaskCount = 2,
                        unfinishedSubtaskCount = 1,
                        unfinishedSubtaskTitles = listOf("Proofread")
                    )
                ),
                listenAgain = true,
                fallbackSpeech = "Full deterministic query sentence."
            ).toAgentJson()
        )
        val task = json.getJSONArray("tasks").getJSONObject(0)

        assertEquals("Submit report", task.getString("title"))
        assertEquals("20/07/2026", task.getString("due_date"))
        assertEquals("09:00", task.getString("due_time"))
        assertFalse(task.getBoolean("is_done"))
        assertEquals(2, task.getInt("subtask_count"))
        assertEquals(1, task.getInt("unfinished_subtask_count"))
        assertEquals("Proofread", task.getJSONArray("unfinished_subtask_titles").getString(0))
        assertEquals("After giving the task details, ask whether the user needs anything else.", json.getString("detail"))
        assertFalse(json.toString().contains("Full deterministic query sentence."))
        assertFalse(task.has("id"))
        assertFalse(json.toString().contains("task_id"))
    }
}
