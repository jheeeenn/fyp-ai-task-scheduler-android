package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditTaskNaturalVoiceSourceTest {
    private val source =
        File("src/main/java/com/example/myapplication/EditTaskActivity.kt").readText()

    @Test
    fun oneSentenceTemporalCommandsRunBeforeGenericEditHelp() {
        val voice = source
            .substringAfter("private fun handleVoiceInput(text: String)")
            .substringBefore("private fun repeatPendingTemporalPrompt()")
        val bounded = voice.indexOf("handleOneSentenceTemporalCommand(normalized)")
        val genericHelp = voice.indexOf(
            "Please return to the main assistant for a new command"
        )

        assertTrue(bounded >= 0)
        assertTrue(bounded < genericHelp)
    }

    @Test
    fun validOneSentenceChangeAsksForSaveButNeverSavesDirectly() {
        val handler = source
            .substringAfter("private fun handleOneSentenceTemporalCommand(")
            .substringBefore("private fun enterTemporalCollection(")

        assertTrue(handler.contains("EditTemporalCommandPolicy.resolve("))
        assertTrue(handler.contains("applyTemporalResolution("))
        assertTrue(handler.contains("askToSaveChanges()"))
        assertFalse(handler.contains("saveTask()"))
        assertFalse(handler.contains("dao.updateTask("))
    }

    @Test
    fun saveRemainsBehindExplicitVoiceConfirmation() {
        val voice = source
            .substringAfter("private fun handleVoiceInput(text: String)")
            .substringBefore("private fun repeatPendingTemporalPrompt()")
        val confirmation = voice
            .substringAfter("if (waitingForSaveConfirmation)")
            .substringBefore("// If we already asked what field value to set")

        assertTrue(confirmation.contains("isYes(normalized)"))
        assertTrue(confirmation.contains("saveTask()"))
        assertTrue(source.contains("waitingForSaveConfirmation = true"))
    }

    @Test
    fun emptyAssistantRescheduleEntersFocusedDateOrTimeCollection() {
        val initialization = source
            .substringAfter("val changed = applyProposedTemporalChange(")
            .substringBefore("private fun openDatePicker()")
        val pendingValue = source
            .substringAfter("EditFieldTarget.DATE_OR_TIME -> {")
            .substringBefore("EditFieldTarget.NONE -> false")

        assertTrue(initialization.contains("rescheduleCollectionRequired"))
        assertTrue(initialization.contains("enterTemporalCollection(EditTemporalTarget.DATE_OR_TIME)"))
        assertTrue(source.contains("What date or time would you like to use?"))
        assertTrue(pendingValue.contains("applyProposedTemporalChange("))
        assertTrue(pendingValue.contains("askToSaveChanges()"))
    }

    @Test
    fun legacyExactFieldSelectionCommandsRemainPresent() {
        val dateCommands = source
            .substringAfter("private fun isDateFieldCommand(")
            .substringBefore("private fun isTimeFieldCommand(")
        val timeCommands = source
            .substringAfter("private fun isTimeFieldCommand(")
            .substringBefore("private fun isTitleFieldCommand(")

        assertTrue(dateCommands.contains("normalized == \"date\""))
        assertTrue(dateCommands.contains("normalized == \"change date\""))
        assertTrue(timeCommands.contains("normalized == \"time\""))
        assertTrue(timeCommands.contains("normalized == \"change time\""))
    }
}
