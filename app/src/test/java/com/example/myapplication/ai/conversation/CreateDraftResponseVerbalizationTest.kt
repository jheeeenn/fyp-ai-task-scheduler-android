package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.createdraft.CreateDraftResponseVerbalization
import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftReadTarget
import com.example.myapplication.voice.CreateTaskDialogState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

class CreateDraftResponseVerbalizationTest {
    @Test
    fun protectedDraftFactsNeverEnterSafeAgentJson() {
        val plan = requireNotNull(saveConfirmationPlan())

        val safeJson = plan.toSafeAgentJson()

        listOf(PRIVATE_TITLE, RAW_DATE, RAW_TIME).forEach {
            assertFalse(safeJson.contains(it, ignoreCase = true))
        }
        assertTrue(safeJson.contains(ResponseVerbalizationPlan.TASK_TITLE))
        assertTrue(safeJson.contains(ResponseVerbalizationPlan.DATE_TEXT))
        assertTrue(safeJson.contains(ResponseVerbalizationPlan.TIME_TEXT))
        assertTrue(safeJson.contains(ResponseMeaningDetail.CREATE_DRAFT_SAVE_CONFIRMATION.name))
    }

    @Test
    fun naturalReadAndContinueTemplatesAreAccepted() {
        val titleRead = requireNotNull(
            CreateDraftResponseVerbalization.readOrNull(
                target = CreateDraftReadTarget.TITLE,
                title = PRIVATE_TITLE,
                date = null,
                time = null,
                state = CreateTaskDialogState.WAITING_FOR_DATE,
                fallbackSpeech = "The current title is $PRIVATE_TITLE. What date would you like?",
                tone = TONE,
                verbosity = VERBOSITY
            )
        )
        val dateRead = requireNotNull(
            CreateDraftResponseVerbalization.readOrNull(
                target = CreateDraftReadTarget.DATE,
                title = PRIVATE_TITLE,
                date = RAW_DATE,
                time = null,
                state = CreateTaskDialogState.WAITING_FOR_TIME,
                fallbackSpeech = "The date is set. What time would you like?",
                tone = TONE,
                verbosity = VERBOSITY
            )
        )
        val scheduleRead = requireNotNull(
            CreateDraftResponseVerbalization.readOrNull(
                target = CreateDraftReadTarget.SCHEDULE,
                title = PRIVATE_TITLE,
                date = RAW_DATE,
                time = RAW_TIME,
                state = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
                fallbackSpeech = "The schedule is set. Would you like to save this task?",
                tone = TONE,
                verbosity = VERBOSITY
            )
        )

        assertEquals(ResponseAct.REPORT_AND_REQUEST_INPUT, titleRead.responseAct)
        assertTrue(evaluate(titleRead, "The title is {task_title}. What date would you like?").accepted)
        assertTrue(evaluate(dateRead, "I've got {date_text}. What time works for you?").accepted)
        assertTrue(
            evaluate(
                scheduleRead,
                "The schedule is {date_text} at {time_text}. Ready to save?"
            ).accepted
        )
    }

    @Test
    fun naturalUpdateConfirmationAndSaveResultsAreAccepted() {
        val update = requireNotNull(
            CreateDraftResponseVerbalization.updateConfirmationOrNull(
                updatedField = CreateDraftField.TIME,
                title = PRIVATE_TITLE,
                date = RAW_DATE,
                time = RAW_TIME,
                fallbackSpeech = "Updated. Should I save it?",
                tone = TONE,
                verbosity = VERBOSITY
            )
        )
        val confirmation = requireNotNull(saveConfirmationPlan())
        val success = saveResult(reminderScheduled = true)
        val partial = saveResult(reminderScheduled = false)

        assertTrue(
            evaluate(
                update,
                "I changed the time. The draft is {task_title}, {date_text}, at {time_text}. Should I save it?"
            ).accepted
        )
        assertTrue(
            evaluate(
                confirmation,
                "Here's the plan: {task_title} on {date_text} at {time_text}. Shall I save it?"
            ).accepted
        )
        assertTrue(evaluate(success, "Your task has been saved.").accepted)
        assertTrue(
            evaluate(
                success,
                "All set — {task_title} is saved for {date_text} at {time_text}."
            ).accepted
        )
        assertTrue(
            evaluate(
                partial,
                "I saved {task_title}, but I couldn't schedule the reminder."
            ).accepted
        )
    }

    @Test
    fun literalDraftFactsAndPrematureSaveClaimsAreRejected() {
        val success = saveResult(reminderScheduled = true)
        val confirmation = requireNotNull(saveConfirmationPlan())

        assertFalse(evaluate(success, "$PRIVATE_TITLE has been saved.").accepted)
        assertFalse(
            evaluate(
                confirmation,
                "I saved {task_title} for {date_text} at {time_text}."
            ).accepted
        )
        assertFalse(
            evaluate(
                confirmation,
                "{task_title} is all set for {date_text} at {time_text}. Save it?"
            ).accepted
        )
        assertFalse(
            evaluate(
                confirmation,
                "I'll update {task_title} on {date_text} at {time_text}. Save it?"
            ).accepted
        )
        assertTrue(
            evaluate(
                confirmation,
                "{task_title}, {date_text}, at {time_text}. Would you like me to save it?"
            ).accepted
        )
    }

    @Test
    fun saveSuccessWordingRequiresAuthoritativeSaveSuccessMeaning() {
        val confirmation = requireNotNull(saveConfirmationPlan())
        val success = saveResult(reminderScheduled = true)
        val partial = saveResult(reminderScheduled = false)

        assertFalse(evaluate(confirmation, "I saved {task_title} on {date_text} at {time_text}.").accepted)
        assertTrue(evaluate(success, "I've saved {task_title}.").accepted)
        assertFalse(evaluate(partial, "I've saved {task_title}.").accepted)
        assertFalse(
            evaluate(
                success,
                "I saved {task_title}, but I couldn't schedule the reminder."
            ).accepted
        )
    }

    @Test
    fun sharedEngineRetainsExactCreateDraftFallback() = runBlocking {
        val plan = requireNotNull(saveConfirmationPlan())
        val invalidSchemaClient = object : ConversationAgentClient(null) {
            override suspend fun respondToObservation(
                observationJson: String,
                memorySnapshot: String,
                appContextSummary: String
            ): String = "not json"
        }
        val unsafeClient = object : ConversationAgentClient(null) {
            override suspend fun respondToObservation(
                observationJson: String,
                memorySnapshot: String,
                appContextSummary: String
            ): String = verbalization("I saved {task_title} for {date_text} at {time_text}.")
        }
        val failedClient = object : ConversationAgentClient(null) {
            override suspend fun respondToObservation(
                observationJson: String,
                memorySnapshot: String,
                appContextSummary: String
            ): String = throw IOException("request failed")
        }
        val timedOutClient = object : ConversationAgentClient(null) {
            override suspend fun respondToObservation(
                observationJson: String,
                memorySnapshot: String,
                appContextSummary: String
            ): String = throw SocketTimeoutException("timed out")
        }

        listOf(invalidSchemaClient, unsafeClient, failedClient, timedOutClient).forEach { client ->
            assertEquals(
                plan.deterministicResponse,
                ResponseVerbalizationEngine(client).verbalize(plan)
            )
        }
    }

    @Test
    fun createTaskUsesV2OnlyAtMeaningfulPresentationPoints() {
        val source = File("src/main/java/com/example/myapplication/CreateTaskActivity.kt").readText()
        val nextStep = source
            .substringAfter("private fun moveToNextMissingStep()")
            .substringBefore("private fun isCurrentLearnedTimeRequest(")
        val read = source
            .substringAfter("private fun readCurrentDraft(")
            .substringBefore("private fun applyProvidedTitle(")
        val update = source
            .substringAfter("private fun returnToSaveConfirmation(")
            .substringBefore("private fun isDraftCompleteForSaveConfirmation(")
        val save = source
            .substringAfter("private fun saveTask()")
            .substringBefore("private fun formatDateForSpeech(")

        assertTrue(nextStep.contains("promptHelper.askTitle()"))
        assertTrue(nextStep.contains("promptHelper.askDate()"))
        assertTrue(nextStep.contains("promptHelper.askTime()"))
        assertTrue(nextStep.contains("verbalizeSaveConfirmation()"))
        assertTrue(read.contains("CreateDraftReadResponseRenderer.render("))
        assertTrue(read.contains("CreateDraftResponseVerbalization.readOrNull("))
        assertTrue(update.contains("CreateDraftResponseVerbalization.updateConfirmationOrNull("))
        assertTrue(save.contains("CreateDraftResponseVerbalization.saveResult("))
        assertTrue(save.indexOf("dao.insert(taskToInsert)") < save.indexOf("saveResult("))
        assertTrue(save.indexOf("scheduleReminderFromTask(") < save.indexOf("saveResult("))
        assertEquals(1, source.windowed("ConversationAgentClient(this)".length).count {
            it == "ConversationAgentClient(this)"
        })
    }

    private fun saveConfirmationPlan() = CreateDraftResponseVerbalization.saveConfirmationOrNull(
        title = PRIVATE_TITLE,
        date = RAW_DATE,
        time = RAW_TIME,
        fallbackSpeech = FALLBACK_CONFIRMATION,
        tone = TONE,
        verbosity = VERBOSITY
    )

    private fun saveResult(reminderScheduled: Boolean) =
        CreateDraftResponseVerbalization.saveResult(
            title = PRIVATE_TITLE,
            date = RAW_DATE,
            time = RAW_TIME,
            reminderScheduled = reminderScheduled,
            fallbackSpeech = if (reminderScheduled) {
                "Done. Your task has been saved."
            } else {
                "Your task was saved, but I could not schedule the reminder."
            },
            tone = TONE,
            verbosity = VERBOSITY
        )

    private fun evaluate(plan: ResponseVerbalizationPlan, template: String) =
        ResponseVerbalizationValidator.evaluate(
            plan,
            ResponseVerbalizationEnvelope(true, template, 0.96)
        )

    private fun verbalization(template: String) =
        """{"use_verbalization":true,"speech_template":"$template","confidence":0.96}"""

    companion object {
        private const val PRIVATE_TITLE = "Buy vitamins"
        private const val RAW_DATE = "13/09/2026"
        private const val RAW_TIME = "9:00 PM"
        private const val FALLBACK_CONFIRMATION =
            "Okay, I've got Buy vitamins, 13 September 2026, 9:00 PM. Should I save it?"
        private val TONE = ResponseVerbalizationTone.FRIENDLY
        private val VERBOSITY = ResponseVerbalizationVerbosity.NORMAL
    }
}
