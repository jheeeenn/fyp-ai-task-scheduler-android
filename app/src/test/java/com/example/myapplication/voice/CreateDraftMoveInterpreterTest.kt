package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftMoveInterpreterTest {
    private val interpreter = CreateDraftMoveInterpreter()

    @Test
    fun interpretsSaveConfirmationMoves() {
        assertEquals(
            CreateDraftMove.ConfirmSave,
            interpret("yes", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.RejectSave,
            interpret("no", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.Unknown,
            interpret("the summary sounds interesting", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        listOf("yes yes", "yeah", "yep", "yes sure").forEach { text ->
            assertEquals(
                text,
                CreateDraftMove.ConfirmSave,
                interpret(text, CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
            )
        }
        assertEquals(
            CreateDraftMove.RejectSave,
            interpret("yes, don't save it", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
    }

    @Test
    fun compoundRejectionKeepsTheRequestedTitleChange() {
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TITLE),
            interpret("no can you edit the title", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"),
            interpret("no change the title to revision", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
    }

    @Test
    fun interpretsExplicitTitleChanges() {
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TITLE),
            interpret("change the title", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"),
            interpret("change the title to revision", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"),
            interpret("rename it to revision", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
    }

    @Test
    fun interpretsExplicitTemporalChangesWithoutResolvingThem() {
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.DATE, "tomorrow"),
            interpret("change the date to tomorrow", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TIME, "5 pm"),
            interpret("change the time to 5 PM", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.ApplyUnspecifiedCorrection("tomorrow"),
            interpret("change it to tomorrow", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.ApplyUnspecifiedCorrection("6 pm"),
            interpret("make it 6 PM", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
    }

    @Test
    fun changeFieldStateAcceptsOnlyFieldSelectionsOrExplicitChanges() {
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TITLE),
            interpret("title", CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.DATE),
            interpret("date", CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TIME),
            interpret("time", CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"),
            interpret("change the title to revision", CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.DATE, "tomorrow"),
            interpret("change the date to tomorrow", CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD)
        )
        assertEquals(
            CreateDraftMove.ChangeField(CreateDraftField.TIME, "5 pm"),
            interpret("change the time to 5 PM", CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD)
        )
        assertEquals(
            CreateDraftMove.Unknown,
            interpret("revision", CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD)
        )
    }

    @Test
    fun waitingFieldStatesProvideOnlyTheRequestedField() {
        assertEquals(
            CreateDraftMove.ProvideField(CreateDraftField.TITLE, "revision"),
            interpret("revision", CreateTaskDialogState.WAITING_FOR_TITLE)
        )
        assertEquals(
            CreateDraftMove.ProvideField(CreateDraftField.DATE, "tomorrow"),
            interpret("tomorrow", CreateTaskDialogState.WAITING_FOR_DATE)
        )
        assertEquals(
            CreateDraftMove.ProvideField(CreateDraftField.TIME, "5 pm"),
            interpret("5 PM", CreateTaskDialogState.WAITING_FOR_TIME)
        )
    }

    @Test
    fun cancelAndHelpAreDeterministicControlMoves() {
        assertEquals(
            CreateDraftMove.Cancel,
            interpret("cancel", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.RequestHelp,
            interpret("help", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            CreateDraftMove.Cancel,
            interpret("discard this task", CreateTaskDialogState.READY_TO_SAVE)
        )
        assertEquals(
            CreateDraftMove.Unknown,
            interpret("change the title", CreateTaskDialogState.READY_TO_SAVE)
        )
    }

    @Test
    fun politeAndCompoundEditStructuresUseTheExplicitFieldGrammar() {
        val cases = mapOf(
            "no please change the title" to CreateDraftMove.ChangeField(CreateDraftField.TITLE),
            "no i want to change the date" to CreateDraftMove.ChangeField(CreateDraftField.DATE),
            "no change the time to 5 PM" to CreateDraftMove.ChangeField(CreateDraftField.TIME, "5 pm"),
            "actually change the title to revision" to CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"),
            "can you rename it to revision" to CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision")
        )

        cases.forEach { (text, expected) ->
            assertEquals(expected, interpret(text, CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION))
        }
    }

    @Test
    fun controlFlowUtterancesNeverBecomeTitleValues() {
        val controls = listOf(
            "no can you edit the title",
            "change the title",
            "edit the title",
            "what should i change",
            "yes",
            "no",
            "save",
            "cancel",
            "help",
            "change the date",
            "change the time"
        )

        controls.forEach { text ->
            val move = interpret(text, CreateTaskDialogState.WAITING_FOR_TITLE)
            assertFalse("Control utterance became a title: $text", move is CreateDraftMove.ProvideField)
            assertFalse(interpreter.isReasonableTitleCandidate(text))
        }

        listOf("revision", "review assignment", "call dentist", "buy medicine").forEach { title ->
            assertTrue(interpreter.isReasonableTitleCandidate(title))
        }
    }

    @Test
    fun interpretationIsDeterministic() {
        val first = interpret(
            "no i want to change the date",
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
        )
        repeat(20) {
            assertEquals(
                first,
                interpret("no i want to change the date", CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
            )
        }
    }

    private fun interpret(text: String, state: CreateTaskDialogState): CreateDraftMove =
        interpreter.interpret(text, state)
}
