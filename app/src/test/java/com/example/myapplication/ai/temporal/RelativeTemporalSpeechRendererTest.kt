package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTemporalSpeechRendererTest {
    @Test
    fun editProposalUsesNaturalWholeHourSpeech() {
        val speech = RelativeTemporalSpeechRenderer.proposal(
            taskTitle = "Doctor appointment",
            schedule = ExactTemporalSchedule("17/09/2026", "10:00 AM"),
            crossedDateBoundary = false
        )

        assertTrue(speech.contains("at 10:00 AM"))
    }

    @Test
    fun exactMinuteNoonAndMidnightRemainNaturalAndExact() {
        assertEquals("to 8:30 AM, keeping no task date", destination("8:30 AM"))
        assertEquals("to noon, keeping no task date", destination("12:00 PM"))
        assertEquals("to midnight, keeping no task date", destination("12:00 AM"))
    }

    private fun destination(time: String): String =
        RelativeTemporalSpeechRenderer.repeatedProposal(
            taskTitle = "Doctor appointment",
            schedule = ExactTemporalSchedule(null, time),
            crossedDateBoundary = false
        ).removePrefix("The current proposal is to move Doctor appointment ").removeSuffix(".")
}
