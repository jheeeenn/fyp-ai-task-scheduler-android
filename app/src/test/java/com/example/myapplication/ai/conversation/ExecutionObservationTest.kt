package com.example.myapplication.ai.conversation

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
        assertTrue(json.contains("essay draft"))
        assertTrue(json.contains("TASK_CHOICE"))
        assertTrue(json.contains("SELECT_OPTION"))
        assertFalse(json.contains("fallback"))
        assertFalse(json.contains("fallbackSpeech"))
        assertFalse(json.contains("fallbackHint"))
        assertFalse(json.contains("task_id"))
        assertFalse(json.contains("Room"))
    }
}
