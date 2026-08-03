package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TaskDetailEditingTest {
    private val resolver = TaskFieldEditResolver()
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = calendar("08/08/2026 12:00 PM")

    @Test
    fun initialDraftEqualsAuthoritativeTaskAndIsClean() {
        val controller = TaskDetailDraftController.from(task())

        assertEquals("Final Year Project", controller.draft.title)
        assertEquals("10/08/2026", controller.draft.dueDate)
        assertEquals("09:00 PM", controller.draft.dueTime)
        assertFalse(controller.isDirty)
        assertNull(controller.freezeSaveClaim())
    }

    @Test
    fun eachFieldChangeMarksDirtyAndRevertingAllFieldsClearsDirty() {
        val controller = TaskDetailDraftController.from(task())

        controller.changeTitle("Thesis")
        assertTrue(controller.isDirty)
        controller.changeTitle("Final Year Project")
        controller.changeDate("11/08/2026")
        assertTrue(controller.isDirty)
        controller.changeDate("10/08/2026")
        controller.changeTime("08:30 PM")
        assertTrue(controller.isDirty)
        controller.changeTime("09:00 PM")
        assertFalse(controller.isDirty)
    }

    @Test
    fun saveClaimFreezesRevisionAndCannotSaveNewerDraft() {
        val controller = TaskDetailDraftController.from(task())
        controller.changeTitle("First draft")
        val claim = controller.freezeSaveClaim()
        assertNotNull(claim)
        val frozen = requireNotNull(claim)

        assertTrue(controller.isCurrent(frozen))
        controller.changeTitle("Newer draft")
        assertFalse(controller.isCurrent(frozen))
    }

    @Test
    fun completionUpdatesAuthoritativeBaseWithoutDiscardingDirtyFields() {
        val controller = TaskDetailDraftController.from(task())
        controller.changeTitle("Unsaved title")

        controller.updateAuthoritativeCompletion(true)

        assertEquals("Unsaved title", controller.draft.title)
        assertTrue(controller.base.isDone)
        assertTrue(controller.isDirty)
        assertTrue(requireNotNull(controller.freezeSaveClaim()).base.isDone)
    }

    @Test
    fun discardAndSuccessfulRoomReplacementClearDirtyState() {
        val controller = TaskDetailDraftController.from(task())
        controller.changeTitle("Unsaved")
        controller.discard()
        assertFalse(controller.isDirty)

        controller.changeTime("10:00 PM")
        controller.replaceFromRoom(task(title = "Saved", time = "10:00 PM"))
        assertEquals("Saved", controller.draft.title)
        assertFalse(controller.isDirty)
    }

    @Test
    fun titleResolverExtractsCandidateAndRejectsBlankOrControls() {
        assertEquals(
            TaskFieldEditResult.Title("final report"),
            resolver.resolveTitle("Change the title to final report")
        )
        assertEquals(
            TaskFieldEditResult.Title("final year project"),
            resolver.resolveTitle("I want the title to be final year project")
        )
        assertEquals(TaskFieldEditResult.Invalid, resolver.resolveTitle("yes"))
        assertEquals(TaskFieldEditResult.Invalid, resolver.resolveTitle("  "))
    }

    @Test
    fun absoluteAndRelativeDatesUseCurrentDraftAsTheBase() {
        val draft = EditableTaskDraft("Task", "10/08/2026", "09:00 PM", 4)

        assertEquals(
            TaskFieldEditResult.Schedule("15/08/2026", "09:00 PM"),
            resolver.resolveDate("Move it to 15 August 2026", draft, now)
        )
        assertEquals(
            TaskFieldEditResult.Schedule("13/08/2026", "09:00 PM"),
            resolver.resolveDate("Three days later", draft, now)
        )
    }

    @Test
    fun exactAndRelativeTimesUseCurrentDraftAndCanCrossDateBoundary() {
        val draft = EditableTaskDraft("Task", "10/08/2026", "11:30 PM", 8)

        assertEquals(
            TaskFieldEditResult.Schedule("10/08/2026", "09:00 PM"),
            resolver.resolveTime("9 PM", draft, now)
        )
        assertEquals(
            TaskFieldEditResult.Schedule("11/08/2026", "12:30 AM"),
            resolver.resolveTime("One hour later", draft, now)
        )
    }

    @Test
    fun halfPastSupportsExactMeridiemAndBroadTimeRequestsClarification() {
        val draft = EditableTaskDraft("Task", "10/08/2026", "09:00 PM", 1)

        assertEquals(
            TaskFieldEditResult.Schedule("10/08/2026", "08:30 PM"),
            resolver.resolveTime("Half past eight PM", draft, now)
        )
        assertEquals(
            TaskFieldEditResult.NeedsClarification("Did you mean 8:30 AM or 8:30 PM?"),
            resolver.resolveTime("Half past eight", draft, now)
        )
        assertEquals(
            TaskFieldEditResult.NeedsClarification("What exact time would you like to use?"),
            resolver.resolveTime("Move it to the morning", draft, now)
        )
    }

    @Test
    fun invalidAndPastSchedulesAreRejected() {
        val draft = EditableTaskDraft("Task", "10/08/2026", "09:00 PM", 0)

        assertEquals(TaskFieldEditResult.Invalid, resolver.resolveDate("31 February", draft, now))
        assertEquals(TaskFieldEditResult.Invalid, resolver.resolveTime("twenty five o'clock", draft, now))
        assertEquals(
            TaskFieldEditResult.PastSchedule,
            resolver.resolveDate("7 August 2026", draft, now)
        )
    }

    @Test
    fun readAllUsesDraftValuesAndMentionsOnlyDirtyState() {
        val status = TaskStatusPresenter.present(false, "10/08/2026", "09:00 PM", now.time, utc)
        val clean = TaskDetailSpeechRenderer.readAll(
            "Draft title", status, "10/08/2026", "09:00 PM", 1, 2, false
        )
        val dirty = TaskDetailSpeechRenderer.readAll(
            "Draft title", status, "10/08/2026", "09:00 PM", 1, 2, true
        )

        assertTrue(clean.contains("Draft title"))
        assertFalse(clean.contains("not saved"))
        assertTrue(dirty.endsWith("These changes are not saved."))
    }

    @Test
    fun confirmationInterpreterDistinguishesYesNoCancelAndUnclear() {
        assertEquals(TaskDetailConfirmation.YES, TaskDetailConfirmationInterpreter.interpret("yes"))
        assertEquals(TaskDetailConfirmation.NO, TaskDetailConfirmationInterpreter.interpret("no"))
        assertEquals(TaskDetailConfirmation.CANCEL, TaskDetailConfirmationInterpreter.interpret("cancel"))
        assertEquals(TaskDetailConfirmation.UNCLEAR, TaskDetailConfirmationInterpreter.interpret("perhaps"))
    }

    private fun task(
        title: String = "Final Year Project",
        time: String = "09:00 PM"
    ) = TaskEntity(
        id = 42,
        title = title,
        dueDate = "10/08/2026",
        dueTime = time,
        isDone = false
    )

    private fun calendar(value: String): Calendar = Calendar.getInstance(utc).apply {
        time = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.UK).apply {
            isLenient = false
            timeZone = utc
        }.parse(value)!!
    }
}
