package com.example.myapplication.ai.routine

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineFollowUpInterpreterTest {
    @Test
    fun repeatedIdenticalSimpleControlsCollapseToOneSafeControl() {
        mapOf(
            "no no" to RoutineFollowUpMove.Reject,
            "no no no" to RoutineFollowUpMove.Reject,
            "yes yes" to RoutineFollowUpMove.Confirm,
            "yes yes yes" to RoutineFollowUpMove.Confirm,
            "cancel cancel" to RoutineFollowUpMove.Cancel,
            "repeat repeat" to RoutineFollowUpMove.Repeat
        ).forEach { (input, expected) ->
            assertEquals(expected, RoutineFollowUpInterpreter.interpret(input))
        }
    }

    @Test
    fun mixedControlsAndRepeatedUnrelatedWordsRemainUnknown() {
        listOf(
            "yes no",
            "no yes",
            "yes change the second time",
            "please yes",
            "maybe no",
            "routine routine",
            "breakfast breakfast"
        ).forEach { input ->
            assertEquals(
                "Expected UNKNOWN for '$input'",
                RoutineFollowUpMove.Unknown,
                RoutineFollowUpInterpreter.interpret(input)
            )
        }
    }
}
