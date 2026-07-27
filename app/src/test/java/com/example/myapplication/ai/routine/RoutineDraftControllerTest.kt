package com.example.myapplication.ai.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RoutineDraftControllerTest {
    private fun base(): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 27, 7, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun controller() = RoutineDraftController(baseCalendarProvider = ::base)

    @Test
    fun lowConfidenceAndClarificationFailClosed() {
        val low = controller()
        val lowToken = low.beginExtraction()
        assertEquals(
            RoutineDraftIssue.LOW_CONFIDENCE,
            (low.applyExtraction(lowToken, validResponse(confidence = 0.79))
                as RoutineDraftUpdate.Rejected).reason
        )
        assertEquals(RoutineDraftState.NONE, low.state)
        assertNull(low.draft)

        val unclear = controller()
        val unclearToken = unclear.beginExtraction()
        assertEquals(
            RoutineDraftIssue.EXTRACTION_NEEDS_CLARIFICATION,
            (unclear.applyExtraction(
                unclearToken,
                validResponse(needClarification = true)
            ) as RoutineDraftUpdate.Rejected).reason
        )
    }

    @Test
    fun emptyStepTitlesAreRejectedAndMissingRoutineTitleUsesDeterministicFallback() {
        val invalid = controller()
        val invalidToken = invalid.beginExtraction()
        assertEquals(
            RoutineDraftIssue.EMPTY_STEP_TITLE,
            (invalid.applyExtraction(
                invalidToken,
                validResponse(
                    steps = listOf(
                        step("", "tomorrow", "8 AM"),
                        step("two", "tomorrow", "9 AM")
                    )
                )
            ) as RoutineDraftUpdate.Rejected).reason
        )

        val fallback = controller()
        val fallbackToken = fallback.beginExtraction()
        fallback.applyExtraction(fallbackToken, validResponse(title = ""))
        assertEquals("My routine", fallback.draft!!.title)
    }

    @Test
    fun sharedExactDateAppliesToMissingStepDates() {
        val controller = controller()
        val token = controller.beginExtraction()
        controller.applyExtraction(
            token,
            validResponse(
                steps = listOf(
                    step("medicine", "tomorrow", "8 AM"),
                    step("breakfast", "", "8:15 AM"),
                    step("leave", "", "9 AM")
                )
            )
        )

        assertEquals(RoutineDraftState.WAITING_FOR_CONFIRMATION, controller.state)
        assertEquals(
            listOf("28/07/2026", "28/07/2026", "28/07/2026"),
            controller.draft!!.steps.map(PendingRoutineStep::resolvedDate)
        )
    }

    @Test
    fun missingSharedDateTriggersOneQuestionAndAppliesAnswerToAll() {
        val controller = controller()
        val token = controller.beginExtraction()
        val first = controller.applyExtraction(
            token,
            validResponse(
                steps = listOf(
                    step("medicine", "", "8 AM"),
                    step("breakfast", "", "8:15 AM")
                )
            )
        )

        assertTrue(first is RoutineDraftUpdate.Ask)
        assertEquals("What date should I use for this routine?", (first as RoutineDraftUpdate.Ask).prompt)
        assertEquals(RoutineDraftState.COLLECTING_SHARED_DATE, controller.state)
        controller.provideSharedDate("tomorrow")
        assertEquals(
            listOf("28/07/2026", "28/07/2026"),
            controller.draft!!.steps.map(PendingRoutineStep::resolvedDate)
        )
        assertEquals(RoutineDraftState.WAITING_FOR_CONFIRMATION, controller.state)
    }

    @Test
    fun missingStepTimesAreCollectedInOrderAndBroadPeriodsNeedExactTime() {
        val controller = controller()
        val token = controller.beginExtraction()
        val first = controller.applyExtraction(
            token,
            validResponse(
                steps = listOf(
                    step("medicine", "tomorrow", "8 AM"),
                    step("breakfast", "tomorrow", "morning"),
                    step("leave", "tomorrow", "")
                )
            )
        )

        assertTrue((first as RoutineDraftUpdate.Ask).prompt.contains("second step, breakfast"))
        assertEquals(
            RoutineDraftIssue.INVALID_TIME,
            (controller.provideNextStepTime("evening") as RoutineDraftUpdate.Rejected).reason
        )
        val second = controller.provideNextStepTime("8:15 AM") as RoutineDraftUpdate.Ask
        assertTrue(second.prompt.contains("third step, leave"))
        controller.provideNextStepTime("9 AM")
        assertEquals(RoutineDraftState.WAITING_FOR_CONFIRMATION, controller.state)
        assertEquals(
            listOf("8:00 AM", "8:15 AM", "9:00 AM"),
            controller.draft!!.steps.map(PendingRoutineStep::resolvedTime)
        )
    }

    @Test
    fun malformedDatesAndTimesAreRejectedAsUnresolved() {
        val dateController = controller()
        val dateToken = dateController.beginExtraction()
        dateController.applyExtraction(
            dateToken,
            validResponse(
                steps = listOf(
                    step("one", "not a date", "8 AM"),
                    step("two", "not a date", "9 AM")
                )
            )
        )
        assertEquals(RoutineDraftState.COLLECTING_SHARED_DATE, dateController.state)
        assertEquals(
            RoutineDraftIssue.INVALID_DATE,
            (dateController.provideSharedDate("also invalid") as RoutineDraftUpdate.Rejected).reason
        )

        val timeController = controller()
        val timeToken = timeController.beginExtraction()
        timeController.applyExtraction(
            timeToken,
            validResponse(
                steps = listOf(
                    step("one", "tomorrow", "after breakfast"),
                    step("two", "tomorrow", "9 AM")
                )
            )
        )
        assertEquals(RoutineDraftState.COLLECTING_STEP_TIME, timeController.state)
        assertEquals(
            RoutineDraftIssue.INVALID_TIME,
            (timeController.provideNextStepTime("after breakfast")
                as RoutineDraftUpdate.Rejected).reason
        )
    }

    @Test
    fun pastSchedulesAreRejectedBeforeReviewAndSaving() {
        val controller = controller()
        val token = controller.beginExtraction()
        val update = controller.applyExtraction(
            token,
            validResponse(
                steps = listOf(
                    step("one", "today", "6 AM"),
                    step("two", "today", "6:30 AM")
                )
            )
        )

        assertTrue(update is RoutineDraftUpdate.Ask)
        assertTrue((update as RoutineDraftUpdate.Ask).prompt.contains("past"))
        assertEquals(RoutineDraftState.COLLECTING_SHARED_DATE, controller.state)
        assertNull(controller.markSaving())
    }

    @Test
    fun proposalPreservesOrderNumbersEveryStepAndIncludesExactSchedule() {
        val controller = reviewedController()
        val proposal = controller.authoritativeProposal!!

        assertTrue(proposal.contains("Morning routine"))
        assertTrue(proposal.contains("Tuesday, 28 July"))
        assertTrue(proposal.contains("First, medicine at 8 AM."))
        assertTrue(proposal.contains("Second, breakfast at 8:15 AM."))
        assertTrue(proposal.contains("Third, leave at 9 AM."))
        assertTrue(proposal.indexOf("medicine") < proposal.indexOf("breakfast"))
        assertTrue(proposal.indexOf("breakfast") < proposal.indexOf("leave"))
        assertTrue(proposal.endsWith("create these 3 tasks?"))
    }

    @Test
    fun revisionsUpdateOnlySelectedValuesIncrementRevisionAndReturnToReview() {
        val controller = reviewedController()
        val original = controller.draft!!
        val timeReview = controller.changeStepTime(0, "8:30 AM") as RoutineDraftUpdate.Review
        assertEquals("8:30 AM", timeReview.draft.steps[0].resolvedTime)
        assertEquals(original.steps[1], timeReview.draft.steps[1])
        assertEquals(original.revision + 1, timeReview.draft.revision)
        assertEquals(RoutineDraftState.WAITING_FOR_CONFIRMATION, controller.state)

        val titleBefore = controller.draft!!
        val titleReview = controller.changeStepTitle(1, "prepare lunch") as RoutineDraftUpdate.Review
        assertEquals("prepare lunch", titleReview.draft.steps[1].title)
        assertEquals(titleBefore.steps[0], titleReview.draft.steps[0])

        val dateReview = controller.changeSharedDate("next Monday") as RoutineDraftUpdate.Review
        assertEquals(1, dateReview.draft.steps.map(PendingRoutineStep::resolvedDate).distinct().size)
        assertNotEquals(original.steps[0].resolvedDate, dateReview.draft.steps[0].resolvedDate)
        assertEquals(RoutineDraftState.WAITING_FOR_CONFIRMATION, controller.state)
    }

    @Test
    fun repeatMoveUsesStoredAuthoritativeProposalAndStructuralChangesAreBounded() {
        val controller = reviewedController()
        val stored = controller.authoritativeProposal
        assertEquals(
            RoutineFollowUpMove.Repeat,
            RoutineFollowUpInterpreter.interpret("repeat the routine")
        )
        assertEquals(stored, controller.authoritativeProposal)
        assertEquals(
            RoutineFollowUpMove.StructuralChange,
            RoutineFollowUpInterpreter.interpret("add another step")
        )
    }

    @Test
    fun staleExtractionCannotReplaceNewerDraftAndClearCancelsPendingState() {
        val controller = controller()
        val staleToken = controller.beginExtraction()
        val currentToken = controller.beginExtraction()
        val stale = controller.applyExtraction(
            staleToken,
            validResponse(title = "Stale routine")
        )
        assertEquals(RoutineDraftUpdate.Stale, stale)
        controller.applyExtraction(currentToken, validResponse(title = "Current routine"))
        assertEquals("Current routine", controller.draft!!.title)

        controller.clear()
        assertEquals(RoutineDraftState.NONE, controller.state)
        assertNull(controller.draft)
        assertNull(controller.authoritativeProposal)
    }

    private fun reviewedController(): RoutineDraftController {
        val controller = controller()
        val token = controller.beginExtraction()
        controller.applyExtraction(
            token,
            validResponse(
                steps = listOf(
                    step("medicine", "tomorrow", "8 AM"),
                    step("breakfast", "tomorrow", "8:15 AM"),
                    step("leave", "tomorrow", "9 AM")
                )
            )
        )
        return controller
    }

    private fun validResponse(
        title: String = "Morning routine",
        steps: List<RoutineStepExtraction> = listOf(
            step("one", "tomorrow", "8 AM"),
            step("two", "tomorrow", "9 AM")
        ),
        confidence: Double = 0.98,
        needClarification: Boolean = false
    ) = RoutineExtractionResponse(title, steps, confidence, needClarification)

    private fun step(title: String, date: String, time: String) =
        RoutineStepExtraction(title, date, time)
}
