package com.example.myapplication.ai.temporal

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.agent.TaskActionNormalizer
import com.example.myapplication.ai.agent.TaskAgentResponse
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.ai.agent.LaptopAgentClient
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TemporalRolesAndPolicyTest {
    private val resolver = TemporalExpressionResolver()
    private fun base() = Calendar.getInstance().apply { set(2026, Calendar.JULY, 16, 12, 0, 0); set(Calendar.MILLISECOND, 0) }

    @Test fun createNextWeekMorningRetainsWindowsAndPhrases() {
        val r = resolver.resolve("next week", "morning", "", base())
        assertEquals(TemporalResolutionType.DATE_TIME_WINDOW, r.type)
        assertEquals("20/07/2026", r.startDateInclusive)
        assertEquals("26/07/2026", r.endDateInclusive)
        assertEquals(300, r.startMinuteInclusive)
        assertEquals(719, r.endMinuteInclusive)
        assertEquals("next week", r.originalDatePhrase)
        assertEquals("morning", r.originalTimePhrase)
        assertFalse(r.isExactTime)
    }

    @Test fun clarificationIsValidatedAgainstOriginalWindow() {
        val r = resolver.resolve("next week", "morning", "", base())
        assertFalse(TemporalActionPolicy.validateClarification(r, "17/07/2026", null))
        assertTrue(TemporalActionPolicy.validateClarification(r, "20/07/2026", null))
        assertFalse(TemporalActionPolicy.validateClarification(r, null, 13 * 60))
        assertTrue(TemporalActionPolicy.validateClarification(r, null, 9 * 60))
    }

    @Test fun exactDateTimeIsEmittedForExactComponents() {
        val r = resolver.resolve("tomorrow", "9 am", "", base())
        assertEquals(TemporalResolutionType.EXACT_DATE_TIME, r.type)
        assertEquals("tomorrow", r.originalDatePhrase)
        assertEquals("9 am", r.originalTimePhrase)
    }

    @Test fun rescheduleDestinationIsNewScheduleNotTargetFilter() {
        val command = TaskActionNormalizer().normalize(
            TaskAgentResponse(
                action = AiIntent.RESCHEDULE_TASK.name,
                target_task_title = "breakfast preparation",
                new_date = "next week",
                new_time = "10 AM",
                confidence = 0.95f
            )
        )
        assertEquals("breakfast preparation", command.targetTaskTitle)
        assertNull(command.targetDateText)
        assertNull(command.targetTimeText)
        assertEquals("next week", command.newDateText)
        assertEquals("10 AM", command.newTimeText)
    }

    @Test fun targetTemporalConstraintsMayFilterSourceMatching() {
        val command = TaskActionNormalizer().normalize(
            TaskAgentResponse(
                action = AiIntent.MARK_DONE.name,
                target_date = "tomorrow",
                target_time = "8 AM",
                confidence = 0.95f
            )
        )
        assertEquals("tomorrow", command.targetDateText)
        assertEquals("8 AM", command.targetTimeText)
    }

    @Test fun completedOnlyFilterForMarkUndoneStyleMatching() {
        val tasks = listOf(
            TaskEntity(1, "active", "10:00 AM", false, "20/07/2026"),
            TaskEntity(2, "completed", "10:00 AM", true, "20/07/2026")
        )
        val window = resolver.resolve("next week", "10 AM", "", base())
        assertEquals(listOf("completed"), TaskTemporalFilter.filterAndSort(tasks, window, TaskCompletionFilter.COMPLETED_ONLY).map { it.title })
    }


    @Test fun bareExactClockTimesResolveDirectly() {
        val cases = mapOf(
            "9 AM" to 540,
            "9:00 AM" to 540,
            "11:00 AM" to 660,
            "11:30 PM" to 1410,
            "09:00" to 540,
            "23:15" to 1395,
            "noon" to 720,
            "midnight" to 0
        )
        cases.forEach { (phrase, minute) ->
            val r = resolver.resolve(null, phrase, phrase, base())
            assertEquals("phrase=$phrase", TemporalResolutionType.EXACT_TIME, r.type)
            assertEquals("phrase=$phrase", minute, r.startMinuteInclusive)
            assertEquals("phrase=$phrase", minute, r.endMinuteInclusive)
        }
    }

    @Test fun invalidBareClockTimesStayUnresolved() {
        assertEquals(TemporalResolutionStatus.UNRESOLVED, resolver.resolve(null, "13 PM", "13 PM", base()).status)
        assertEquals(TemporalResolutionStatus.UNRESOLVED, resolver.resolve(null, "25:00", "25:00", base()).status)
    }

    @Test fun createMorningConstraintAccepts11AmAndRejects1Pm() {
        val morning = resolver.resolve(null, "morning", "morning", base())
        val eleven = resolver.resolve(null, "11:00 AM", "11:00 AM", base()).startMinuteInclusive
        val onePm = resolver.resolve(null, "1:00 PM", "1:00 PM", base()).startMinuteInclusive
        assertTrue(TemporalActionPolicy.validateClarification(morning, null, eleven))
        assertFalse(TemporalActionPolicy.validateClarification(morning, null, onePm))
    }

    @Test fun mutationPromptForbidsSuccessClaims() {
        val prompt = LaptopAgentClient.SYSTEM_PROMPT
        assertTrue(prompt.contains("Never claim that an action succeeded"))
        assertTrue(prompt.contains("new_date"))
        assertTrue(prompt.contains("target_date"))
    }
}
