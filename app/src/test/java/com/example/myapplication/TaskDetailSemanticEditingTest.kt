package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TaskDetailSemanticEditingTest {
    private val resolver = TaskFieldEditResolver()
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun combinedTomorrowTimeFromTimeFocusUpdatesBothFields() {
        val result = resolver.resolveScheduleProposal(
            dateText = "tomorrow",
            timeText = "11:45 AM",
            draft = draft("03/08/2026", "09:00 PM"),
            now = calendar("03/08/2026 08:00 PM")
        )
        assertEquals(TaskFieldEditResult.Schedule("04/08/2026", "11:45 AM"), result)
    }

    @Test
    fun fridayAtNineFromDateFocusUpdatesBothFields() {
        val result = resolver.resolveScheduleProposal(
            dateText = "Friday",
            timeText = "9 PM",
            draft = draft("03/08/2026", "08:00 PM"),
            now = calendar("03/08/2026 06:00 PM")
        )
        assertEquals(TaskFieldEditResult.Schedule("07/08/2026", "09:00 PM"), result)
    }

    @Test
    fun dateFallbackPreservesExactEmbeddedTimeAndRejectsBroadMorning() {
        assertEquals(
            TaskFieldEditResult.Schedule("07/08/2026", "09:00 PM"),
            resolver.resolveDate(
                "Friday at 9 PM",
                draft("03/08/2026", "08:00 PM"),
                calendar("03/08/2026 06:00 PM")
            )
        )
        assertEquals(
            TaskFieldEditResult.NeedsClarification("What exact time would you like to use?"),
            resolver.resolveDate(
                "Friday morning",
                draft("03/08/2026", "08:00 PM"),
                calendar("03/08/2026 06:00 PM")
            )
        )
    }

    @Test
    fun futureDateAndFutureSameDayExactTimesValidate() {
        assertEquals(
            TaskFieldEditResult.Schedule("04/08/2026", "11:45 AM"),
            resolver.resolveTime(
                "11:45 AM",
                draft("04/08/2026", "09:00 PM"),
                calendar("03/08/2026 11:26 PM")
            )
        )
        assertEquals(
            TaskFieldEditResult.Schedule("03/08/2026", "11:45 PM"),
            resolver.resolveTime(
                "11:45 PM",
                draft("03/08/2026", "09:00 PM"),
                calendar("03/08/2026 08:00 PM")
            )
        )
    }

    @Test
    fun passedSameDayExactTimeCreatesGuardedTomorrowProposal() {
        assertEquals(
            TaskFieldEditResult.PastSameDayTime("11:45 AM", "04/08/2026"),
            resolver.resolveTime(
                "11:45 AM",
                draft("03/08/2026", "09:00 PM"),
                calendar("03/08/2026 11:26 PM")
            )
        )
    }

    @Test
    fun tomorrowProposalValidatesAndGuardRejectsRevisionOrGenerationChanges() {
        val pending = TaskDetailPastTimeProposal("11:45 AM", "04/08/2026", 5, 8)
        assertTrue(pending.isCurrent(5, 8))
        assertFalse(pending.isCurrent(6, 8))
        assertFalse(pending.isCurrent(5, 9))
        assertEquals(
            TaskFieldEditResult.Schedule("04/08/2026", "11:45 AM"),
            resolver.resolveScheduleProposal(
                pending.tomorrowDate,
                pending.proposedTime,
                draft("03/08/2026", "09:00 PM", revision = 5),
                calendar("03/08/2026 11:26 PM")
            )
        )
    }

    @Test
    fun tomorrowClarificationYesNoCancelAndUnclearStayDeterministic() {
        assertEquals(
            TaskDetailPastTimeConfirmationMove.APPLY_TOMORROW,
            TaskDetailPastTimeConfirmationResolver.resolve("yes")
        )
        assertEquals(
            TaskDetailPastTimeConfirmationMove.ASK_DATE_AND_TIME,
            TaskDetailPastTimeConfirmationResolver.resolve("no")
        )
        assertEquals(
            TaskDetailPastTimeConfirmationMove.CANCEL,
            TaskDetailPastTimeConfirmationResolver.resolve("cancel")
        )
        assertEquals(
            TaskDetailPastTimeConfirmationMove.REPEAT_QUESTION,
            TaskDetailPastTimeConfirmationResolver.resolve("perhaps")
        )
    }

    @Test
    fun relativeDateAndTimeChangesUseCurrentDraftAsBase() {
        assertEquals(
            TaskFieldEditResult.Schedule("13/08/2026", "09:00 PM"),
            resolver.resolveDate(
                "three days later",
                draft("10/08/2026", "09:00 PM"),
                calendar("03/08/2026 08:00 PM")
            )
        )
        assertEquals(
            TaskFieldEditResult.Schedule("11/08/2026", "01:00 AM"),
            resolver.resolveTime(
                "two hours later",
                draft("10/08/2026", "11:00 PM"),
                calendar("03/08/2026 08:00 PM")
            )
        )
    }

    @Test
    fun requestGuardRejectsStaleStateGenerationAndDraftRevision() {
        val guard = TaskDetailEditRequestGuard(TaskDetailEditInteraction.WAITING_FOR_TIME, 3, 7)
        assertTrue(guard.isCurrent(TaskDetailEditInteraction.WAITING_FOR_TIME, 3, 7))
        assertFalse(guard.isCurrent(TaskDetailEditInteraction.WAITING_FOR_DATE, 3, 7))
        assertFalse(guard.isCurrent(TaskDetailEditInteraction.WAITING_FOR_TIME, 4, 7))
        assertFalse(guard.isCurrent(TaskDetailEditInteraction.WAITING_FOR_TIME, 3, 8))
    }

    @Test
    fun resultSpeechNamesEveryChangedFieldAndPastQuestionRepeatsExactTime() {
        assertEquals(
            "Date changed to 4 August 2026 and time changed to 11:45 AM. Not saved.",
            TaskDetailEditSpeechRenderer.scheduleChanged(
                "03/08/2026", "09:00 PM", "04/08/2026", "11:45 AM"
            )
        )
        assertEquals(
            "11:45 AM today has already passed. Did you mean tomorrow at 11:45 AM?",
            TaskDetailEditSpeechRenderer.pastSameDayQuestion("11:45 AM")
        )
        assertEquals("What date and time would you like to use?", TaskDetailEditSpeechRenderer.askDateAndTime())
    }

    @Test
    fun androidRejectsPastModelScheduleAndCommandWrapperTitle() {
        assertEquals(
            TaskFieldEditResult.PastSchedule,
            resolver.resolveScheduleProposal(
                "02/08/2026", "09:00 PM", draft("03/08/2026", "10:00 PM"),
                calendar("03/08/2026 11:26 PM")
            )
        )
        assertEquals(TaskFieldEditResult.Invalid, resolver.validateProposedTitle("change it to breakfast"))
        assertEquals(TaskFieldEditResult.Title("breakfast"), resolver.validateProposedTitle("breakfast"))
    }

    private fun draft(date: String, time: String, revision: Long = 1) =
        EditableTaskDraft("Task", date, time, revision)

    private fun calendar(value: String): Calendar = Calendar.getInstance(utc).apply {
        time = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.UK).apply {
            isLenient = false
            timeZone = utc
        }.parse(value)!!
    }
}
