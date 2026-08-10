package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResponseVerbalizationSafetyTest {
    private val parser = ResponseVerbalizationParser()

    @Test
    fun strictSchemaContainsOnlyPresentationFields() {
        val format = AgentResponseSchemas.responseVerbalizationResponseFormat()
        val schema = format.getJSONObject("json_schema").getJSONObject("schema")

        assertEquals(
            setOf("use_verbalization", "speech_template", "confidence"),
            schema.getJSONObject("properties").keys().asSequence().toSet()
        )
        assertEquals(3, schema.getJSONArray("required").length())
        assertFalse(schema.getBoolean("additionalProperties"))
        assertFalse(schema.toString().contains("response_type"))
        assertFalse(schema.toString().contains("listen_again"))
    }

    @Test
    fun parserRejectsMalformedExtraMissingAndWrongTypes() {
        listOf(
            "not json",
            """```json {"use_verbalization":true,"speech_template":"{authoritative_message}","confidence":0.9}```""",
            """{"use_verbalization":true,"speech_template":"{authoritative_message}","confidence":0.9}{"extra":true}""",
            """{"use_verbalization":true,"speech_template":"{authoritative_message}","confidence":0.9,"outcome":"SUCCESS"}""",
            """{"use_verbalization":true,"confidence":0.9}""",
            """{"use_verbalization":"true","speech_template":"{authoritative_message}","confidence":0.9}"""
        ).forEach { raw ->
            assertThrows(ConversationSchemaException::class.java) { parser.parse(raw) }
        }
    }

    @Test
    fun validatorRequiresExactlyOneKnownProtectedPlaceholder() {
        val plan = messageSuccessPlan()

        assertEquals(
            ResponseVerbalizationValidationReason.UNKNOWN_PLACEHOLDER,
            evaluate(plan, "Sure — {task_title}").reason
        )
        assertEquals(
            ResponseVerbalizationValidationReason.MISSING_REQUIRED_PLACEHOLDER,
            evaluate(plan, "Sure.").reason
        )
        assertEquals(
            ResponseVerbalizationValidationReason.DUPLICATE_REQUIRED_PLACEHOLDER,
            evaluate(
                plan,
                "{authoritative_message} Okay. {authoritative_message}"
            ).reason
        )
    }

    @Test
    fun validatorRejectsFactsControlsInternalsAndLowConfidence() {
        val plan = messageSuccessPlan()
        listOf(
            "I deleted the task. {authoritative_message}",
            "Please confirm. {authoritative_message}",
            "For T1. {authoritative_message}",
            "Android says {authoritative_message}",
            "At 9 PM. {authoritative_message}",
            "It worked. {authoritative_message}",
            "That failed. {authoritative_message}",
            "```text {authoritative_message}```"
        ).forEach { template ->
            assertFalse(evaluate(plan, template).accepted)
        }
        assertEquals(
            ResponseVerbalizationValidationReason.LOW_CONFIDENCE,
            ResponseVerbalizationValidator.evaluate(
                plan,
                ResponseVerbalizationEnvelope(
                    true,
                    "All set — {authoritative_message}",
                    0.84
                )
            ).reason
        )
        assertEquals(
            ResponseVerbalizationValidationReason.TEMPLATE_TOO_LONG,
            evaluate(
                plan,
                "Certainly okay sure absolutely alright well here now then {authoritative_message}"
            ).reason
        )
    }

    @Test
    fun deleteConfirmationIsNaturalButCannotTurnIntoSuccess() {
        val confirmationPlan = plan(
            ExecutionObservation(
                operation = ExecutionOperation.DELETE_TASK,
                outcome = ExecutionOutcome.NEEDS_CONFIRMATION,
                taskTitle = "private title",
                requiredInput = RequiredInput.CONFIRMATION,
                allowedUserMoves = listOf(AllowedUserMove.CONFIRM, AllowedUserMove.REJECT),
                listenAgain = true,
                fallbackSpeech = "Delete private title? Please say yes or no."
            )
        )

        assertEquals(
            ResponseVerbalizationValidationReason.UNSAFE_PRESENTATION_TEXT,
            evaluate(confirmationPlan, "I've deleted {task_title}.").reason
        )
        assertTrue(
            evaluate(
                confirmationPlan,
                "Would you like me to delete {task_title}?"
            ).accepted
        )
        assertEquals(
            ResponseVerbalizationContract.TASK_CONFIRMATION,
            confirmationPlan.contract
        )
        assertEquals(ExecutionOutcome.NEEDS_CONFIRMATION, confirmationPlan.outcome)
        assertEquals(RequiredInput.CONFIRMATION, confirmationPlan.requiredInput)
        assertTrue(confirmationPlan.continuedInteractionExpected)
    }

    @Test
    fun compositionSubstitutesProtectedValueOnceWithoutChangingPlan() {
        val plan = successPlan()
        assertEquals(
            ResponseVerbalizationValidationReason.UNKNOWN_PLACEHOLDER,
            evaluate(
                plan,
                "All set — I've {authoritative_action} {task_title}."
            ).reason
        )
        val envelope = ResponseVerbalizationEnvelope(
            true,
            "All set — I've deleted {task_title}.",
            0.97
        )

        val speech = ResponseVerbalizationComposer.compose(plan, envelope)

        assertEquals("All set — I've deleted private title.", speech)
        assertEquals(
            "private title",
            plan.protectedValues.getValue(ResponseVerbalizationPlan.TASK_TITLE)
        )
        assertEquals(
            setOf(ResponseVerbalizationPlan.TASK_TITLE),
            plan.protectedValues.keys
        )
        assertEquals(ExecutionOutcome.SUCCESS, plan.outcome)
    }

    @Test
    fun taskActionResultsUseOperationSpecificNaturalGrammar() {
        val markDone = plan(
            taskResultObservation(
                operation = ExecutionOperation.MARK_DONE,
                fallback = "private title was marked as complete."
            )
        )
        val markUndone = plan(
            taskResultObservation(
                operation = ExecutionOperation.MARK_UNDONE,
                fallback = "private title was marked as incomplete."
            )
        )

        assertTrue(evaluate(markDone, "I've marked {task_title} as complete.").accepted)
        assertTrue(evaluate(markDone, "{task_title} is now complete.").accepted)
        assertFalse(evaluate(markDone, "I've marked as complete {task_title}.").accepted)
        assertFalse(evaluate(markDone, "{task_title} is active again.").accepted)

        assertTrue(evaluate(markUndone, "I've marked {task_title} as incomplete.").accepted)
        assertTrue(evaluate(markUndone, "{task_title} is active again.").accepted)
        assertFalse(evaluate(markUndone, "{task_title} is now complete.").accepted)
    }

    @Test
    fun simpleHandoffsUseProtectedTargetsAndDoNotClaimCompletedChanges() {
        val create = plan(
            ExecutionObservation(
                operation = ExecutionOperation.CREATE_TASK,
                outcome = ExecutionOutcome.INFORMATION,
                taskTitle = "private draft title",
                dateText = "private date",
                listenAgain = false,
                fallbackSpeech = "Opening task creation."
            )
        )
        val update = transitionPlan(ExecutionOperation.UPDATE_TASK, "Opening edit task.")
        val reschedule = transitionPlan(
            ExecutionOperation.RESCHEDULE_TASK,
            "Opening reschedule task."
        )

        assertEquals(ResponseVerbalizationContract.TASK_TRANSITION, create.contract)
        assertEquals(
            setOf(ResponseVerbalizationPlan.TRANSITION_TARGET),
            create.requiredPlaceholders
        )
        assertEquals(
            "task creation",
            create.protectedValues.getValue(ResponseVerbalizationPlan.TRANSITION_TARGET)
        )
        assertFalse(create.toSafeAgentJson().contains("task creation"))
        assertFalse(create.toSafeAgentJson().contains("private draft title"))
        assertFalse(create.toSafeAgentJson().contains("private date"))
        assertTrue(
            evaluate(create, "Okay — I'll open {transition_target} for you.").accepted
        )
        assertTrue(
            evaluate(
                update,
                "Sure — I'll open {task_title} so you can make that change."
            ).accepted
        )
        assertTrue(
            evaluate(
                reschedule,
                "Okay — let's update the schedule for {task_title}."
            ).accepted
        )
        assertFalse(evaluate(update, "I've updated {task_title}.").accepted)
        assertFalse(evaluate(reschedule, "I've rescheduled {task_title}.").accepted)
    }

    @Test
    fun complexBriefingAndSuggestionRemainOpaqueProtectedMessages() {
        val briefing = plan(
            ExecutionObservation(
                operation = ExecutionOperation.DAILY_BRIEFING,
                outcome = ExecutionOutcome.INFORMATION,
                taskCount = 2,
                tasks = listOf(ObservedTask("private first"), ObservedTask("private second")),
                dateText = "private date",
                listenAgain = true,
                fallbackSpeech = "Private daily briefing with two task facts."
            )
        )
        val suggestion = plan(
            ExecutionObservation(
                operation = ExecutionOperation.CONTEXT_SUGGESTION,
                outcome = ExecutionOutcome.INFORMATION,
                taskTitle = "private suggestion",
                facts = listOf("private reason"),
                listenAgain = true,
                fallbackSpeech = "Private suggestion and its factual reason."
            )
        )

        listOf(briefing, suggestion).forEach { complexPlan ->
            assertEquals(
                ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE,
                complexPlan.contract
            )
            assertEquals(
                setOf(ResponseVerbalizationPlan.AUTHORITATIVE_MESSAGE),
                complexPlan.requiredPlaceholders
            )
            assertTrue(evaluate(complexPlan, "Sure — {authoritative_message}").accepted)
            assertFalse(complexPlan.toSafeAgentJson().contains("private", ignoreCase = true))
            assertFalse(
                evaluate(
                    complexPlan,
                    "Here are two tasks. {authoritative_message}"
                ).accepted
            )
        }
    }

    @Test
    fun sourceLayerHasNoDatabaseReminderOrNavigationAuthority() {
        val source = File(
            "src/main/java/com/example/myapplication/ai/conversation/ResponseVerbalization.kt"
        ).readText()

        listOf(
            "AppDatabase",
            "TaskDao",
            "ReminderHelper",
            "startActivity(",
            "pendingDeleteTaskId",
            "homeFollowUpContext",
            "AssistantVoiceSession"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
        assertFalse(source.contains("ExecutionObservation.toAgentJson"))
        assertTrue(source.contains("protectedValues"))
        assertTrue(source.contains("toSafeAgentJson"))
    }

    @Test
    fun directRepliesRemainSinglePassAndBypassObservationVerbalization() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val directReply = source
            .substringAfter("ConversationRoute.DIRECT_REPLY ->")
            .substringBefore("ConversationRoute.ASK_CLARIFICATION ->")

        assertTrue(directReply.contains("conversationDecision.reply"))
        assertTrue(directReply.contains("assistantSession.speak("))
        assertFalse(directReply.contains("respondToObservation"))
        assertFalse(directReply.contains("renderObservationResponse"))
    }

    @Test
    fun roomAndReminderMutationsCompleteBeforeVerbalizationAndAreNotRetried() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val delete = source
            .substringAfter("private fun confirmPendingDelete()")
            .substringBefore("private suspend fun beginBreakdownTargetResolution")

        assertTrue(delete.indexOf("dao.deleteTaskAndSubtasks(taskId)") >= 0)
        assertTrue(delete.indexOf("ReminderHelper.cancelReminder") >= 0)
        assertTrue(delete.indexOf("speakObservation(") >= 0)
        assertTrue(
            delete.indexOf("dao.deleteTaskAndSubtasks(taskId)") <
                delete.indexOf("speakObservation(")
        )
        assertTrue(
            delete.indexOf("ReminderHelper.cancelReminder") <
                delete.indexOf("speakObservation(")
        )
        assertEquals(1, delete.windowed("dao.deleteTaskAndSubtasks".length).count {
            it == "dao.deleteTaskAndSubtasks"
        })

        val responseLayer = source
            .substringAfter("private suspend fun renderObservationResponse(")
            .substringBefore("private fun captureSafeObservationDeliveryState(")
        assertFalse(responseLayer.contains("AppDatabase"))
        assertFalse(responseLayer.contains("ReminderHelper"))
        assertFalse(responseLayer.contains("deleteTaskAndSubtasks"))
        assertFalse(responseLayer.contains("updateDoneStatus"))
    }

    @Test
    fun confirmationAndNavigationStateExistBeforeVerbalization() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val confirmation = source
            .substringAfter("private fun askDeleteConfirmation(")
            .substringBefore("private fun clearPendingDeleteState()")
        val speakThenRun = source
            .substringAfter("private suspend fun speakObservationThenRun(")
            .substringBefore("private fun captureResponseVerbalizationDeliveryState()")

        assertTrue(
            confirmation.indexOf("pendingDeleteTaskId = task.id") <
                confirmation.indexOf("speakObservation(")
        )
        assertTrue(
            confirmation.indexOf("pendingDeleteTaskTitle = task.title") <
                confirmation.indexOf("speakObservation(")
        )
        assertTrue(
            confirmation.indexOf(
                "homeFollowUpContext = HomeFollowUpContext.DELETE_CONFIRMATION"
            ) < confirmation.indexOf("speakObservation(")
        )
        assertTrue(speakThenRun.contains("deliverObservationResponse(observation, response, action)"))
        assertFalse(speakThenRun.contains("response.speech.contains"))
        assertFalse(speakThenRun.contains("when (response.speech"))
    }

    @Test
    fun activityStopInvalidatesRequestBeforeStoppingSessionResources() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val onStop = source
            .substringAfter("override fun onStop()")
            .substringBefore("override fun onDestroy()")

        assertTrue(onStop.contains("AssistantRequestInvalidationReason.SESSION_STOPPED"))
        assertTrue(
            onStop.indexOf("invalidateAssistantRequest(") <
                onStop.indexOf("assistantSession.stopForLifecycle()")
        )
    }

    @Test
    fun newerRequestAndStoppedActivityDiscardLateModelResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = object : ConversationAgentClient(null) {
            override suspend fun respondToObservation(
                observationJson: String,
                memorySnapshot: String,
                appContextSummary: String
            ): String {
                started.complete(Unit)
                release.await()
                return verbalization(
                    "All set — I've deleted {task_title}."
                )
            }
        }
        val captured = deliveryState(generation = 4)
        var delivered = 0
        val response = async {
            ConversationOrchestrator(client, ConversationDecisionParser())
                .respondToObservation(successObservation())
        }
        started.await()
        release.complete(Unit)
        val completed = response.await()

        val newerReason = ResponseVerbalizationDeliveryGuard.runIfCurrent(
            captured,
            captured.copy(requestGeneration = 5)
        ) { delivered += 1 }
        val stoppedReason = ResponseVerbalizationDeliveryGuard.runIfCurrent(
            captured,
            captured.copy(activityActive = false)
        ) { delivered += 1 }

        assertEquals("conversation_agent_verbalization", completed.source)
        assertEquals(ResponseVerbalizationStaleReason.REQUEST_CHANGED, newerReason)
        assertEquals(ResponseVerbalizationStaleReason.ACTIVITY_STOPPED, stoppedReason)
        assertEquals(0, delivered)
    }

    @Test
    fun endedSessionDiscardsLateResponseButCurrentSessionDeliversOnce() {
        val captured = deliveryState(generation = 7)
        var delivered = 0

        val ended = ResponseVerbalizationDeliveryGuard.runIfCurrent(
            captured,
            captured.copy(assistantRequestActive = false)
        ) { delivered += 1 }
        val current = ResponseVerbalizationDeliveryGuard.runIfCurrent(
            captured,
            captured
        ) { delivered += 1 }

        assertEquals(ResponseVerbalizationStaleReason.SESSION_ENDED, ended)
        assertEquals(null, current)
        assertEquals(1, delivered)
    }

    private fun successPlan() = plan(successObservation())

    private fun messageSuccessPlan() = plan(
        ExecutionObservation(
            operation = ExecutionOperation.SYSTEM,
            outcome = ExecutionOutcome.SUCCESS,
            listenAgain = false,
            fallbackSpeech = "The operation completed."
        )
    )

    private fun successObservation() = ExecutionObservation(
        operation = ExecutionOperation.DELETE_TASK,
        outcome = ExecutionOutcome.SUCCESS,
        taskTitle = "private title",
        listenAgain = false,
        fallbackSpeech = "private title was deleted."
    )

    private fun taskResultObservation(
        operation: ExecutionOperation,
        fallback: String
    ) = ExecutionObservation(
        operation = operation,
        outcome = ExecutionOutcome.SUCCESS,
        taskTitle = "private title",
        listenAgain = false,
        fallbackSpeech = fallback
    )

    private fun transitionPlan(
        operation: ExecutionOperation,
        fallback: String
    ) = plan(
        ExecutionObservation(
            operation = operation,
            outcome = ExecutionOutcome.INFORMATION,
            taskTitle = "private title",
            tasks = listOf(ObservedTask("private title")),
            listenAgain = false,
            fallbackSpeech = fallback
        )
    )

    private fun plan(observation: ExecutionObservation) = requireNotNull(
        ResponseVerbalizationPlanner.createOrNull(
            observation,
            ResponseVerbalizationTone.FRIENDLY,
            ResponseVerbalizationVerbosity.NORMAL
        )
    )

    private fun evaluate(plan: ResponseVerbalizationPlan, template: String) =
        ResponseVerbalizationValidator.evaluate(
            plan,
            ResponseVerbalizationEnvelope(true, template, 0.96)
        )

    private fun deliveryState(generation: Long) = ResponseVerbalizationDeliveryState(
        requestGeneration = generation,
        assistantRequestActive = true,
        activityActive = true
    )

    private fun verbalization(template: String) =
        """{"use_verbalization":true,"speech_template":"$template","confidence":0.96}"""
}
