package com.example.myapplication.ai.conversation.taskedit

import com.example.myapplication.ai.temporal.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Multi-turn foreground transitions using the real policies, semantic adapter and proposal session. */
class EditTaskPendingCollectionTransitionTest {
    @Test
    fun titleRequestThenYesCannotConfirmOrBecomeTheTitleThenValuePreservesProposal() = runBlocking {
        val session = session()
        val originalProposal = session.currentProposal
        val originalRevision = session.revision
        var field = EditFieldTarget.NONE
        var title = "Take Medicine"
        val orchestrator = interpreter(
            json("REQUEST_TITLE_CHANGE"), json("CHANGE_TITLE", title = "Take Supplements")
        )
        val request = orchestrator.resolve("Change the title instead", context(field))
        assertEquals(EditTaskSemanticMove.REQUEST_TITLE_CHANGE, request.move)
        field = EditFieldTarget.TITLE
        assertEquals(EditTaskInteractionState.WAITING_FOR_TITLE, foreground(field))
        assertNull(local("yes", field))
        val rejected = orchestrator.resolveImmediate("yes", context(field))!!
        assertEquals(EditTaskSemanticMove.UNKNOWN, rejected.move)
        assertFalse(rejected.agentAttempted)
        assertEquals("Take Medicine", title)
        assertFalse(EditTaskSemanticMove.CONFIRM_SAVE in context(field).allowedMoves)

        val value = orchestrator.resolve("Take Supplements", context(field))
        assertEquals(EditTaskSemanticMove.CHANGE_TITLE, value.move)
        assertEquals(EditTaskRelativeProposalSemanticRoute.EDIT_SEMANTIC,
            EditTaskRelativeProposalRoutingPolicy.semanticRoute(value, session.state == RelativeTemporalProposalState.ACTIVE))
        title = value.title
        field = EditFieldTarget.NONE
        assertEquals("Take Supplements", title)
        assertEquals(originalProposal, session.currentProposal)
        assertEquals(originalRevision, session.revision)
        assertNotNull(session.correctionContext())
        assertEquals(RelativeTemporalProposalState.ACTIVE, session.state)
        assertEquals(EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION, foreground(field))
        assertEquals(EditTaskRelativeProposalLocalAction.CONFIRM, local("Yes please", field))
    }

    @Test
    fun dateRequestThenSuccessfulCorrectionCompletesCollectionBeforeSaveConfirmation() = runBlocking {
        checkTemporalTransition(
            field = EditFieldTarget.DATE,
            requestMove = "REQUEST_DATE_CHANGE",
            input = "Sunday",
            response = json("CHANGE_DATE", date = "Sunday"),
            proposal = temporalProposal(date = "Sunday")
        )
    }

    @Test
    fun timeRequestThenOffsetStillUsesProposalCalculationAndCompletesCollection() = runBlocking {
        checkTemporalTransition(
            field = EditFieldTarget.TIME,
            requestMove = "REQUEST_TIME_CHANGE",
            input = "two hours later",
            response = json("CHANGE_TIME", time = "two hours later"),
            proposal = temporalProposal(timeOffset = 120)
        )
    }

    @Test
    fun pendingCollectionPrecedenceAndControlGatesCoverEveryCollectionState() {
        val expected = mapOf(
            EditFieldTarget.TITLE to EditTaskInteractionState.WAITING_FOR_TITLE,
            EditFieldTarget.DATE to EditTaskInteractionState.WAITING_FOR_DATE,
            EditFieldTarget.TIME to EditTaskInteractionState.WAITING_FOR_TIME,
            EditFieldTarget.DATE_OR_TIME to EditTaskInteractionState.WAITING_FOR_DATE_OR_TIME
        )
        expected.forEach { (field, state) ->
            assertEquals(state, foreground(field))
            listOf("yes", "no", "save it", "repeat the proposal").forEach { input ->
                assertNull(local(input, field))
            }
        }
        assertEquals(EditTaskInteractionState.WAITING_FOR_TEMPORAL_CLARIFICATION,
            foreground(EditFieldTarget.DATE, clarification = true))
        assertNull(local("yes", EditFieldTarget.NONE, clarification = true))
        assertEquals(EditTaskInteractionState.WAITING_FOR_DELETE_CONFIRMATION,
            EditTaskInteractionPolicy.foregroundState(false, true, true, EditFieldTarget.TITLE, true, true))
        assertEquals(EditTaskInteractionState.OPERATION_IN_FLIGHT,
            EditTaskInteractionPolicy.foregroundState(true, true, true, EditFieldTarget.TITLE, true, true))
        listOf(EditTaskInteractionState.WAITING_FOR_DATE_OR_TIME,
            EditTaskInteractionState.WAITING_FOR_TEMPORAL_CLARIFICATION).forEach { state ->
            assertTrue(EditTaskInteractionPolicy.completesTemporalCollection(state, RelativeTemporalRevisionResult.APPLIED))
            assertFalse(EditTaskInteractionPolicy.completesTemporalCollection(state, RelativeTemporalRevisionResult.STALE_REQUEST))
            assertFalse(EditTaskInteractionPolicy.completesTemporalCollection(state, RelativeTemporalRevisionResult.INACTIVE))
        }
        assertFalse(EditTaskInteractionPolicy.completesTemporalCollection(
            EditTaskInteractionState.WAITING_FOR_TITLE, RelativeTemporalRevisionResult.APPLIED))
    }

    @Test
    fun failedAndStaleCorrectionsRetainCollectionAndCannotEnableConfirmation() {
        val session = session()
        val before = session.currentProposal
        val token = session.beginCorrection()
        val invalid = RelativeTemporalChangeCalculator().calculate(
            session.authoritativeOriginal, session.currentProposal,
            temporalProposal(date = "not a calendar date"), now()
        )
        assertTrue(invalid is RelativeTemporalCalculationResult.Failure)
        assertEquals(before, session.currentProposal)
        assertNull(local("yes", EditFieldTarget.DATE))

        session.invalidatePendingCorrection()
        val result = session.applyCorrection(token, ExactTemporalSchedule("06/09/2026", "11:00 PM"),
            temporalProposal(timeOffset = 120))
        assertEquals(RelativeTemporalRevisionResult.STALE_REQUEST, result)
        assertFalse(EditTaskInteractionPolicy.completesTemporalCollection(foreground(EditFieldTarget.DATE), result))
        assertEquals(before, session.currentProposal)
        assertEquals(RelativeTemporalProposalState.ACTIVE, session.state)
        assertEquals(EditTaskInteractionState.WAITING_FOR_DATE, foreground(EditFieldTarget.DATE))
        assertNull(local("save it", EditFieldTarget.DATE))
    }

    @Test
    fun directControlsAndTemporalRoutingRemainAvailableWithoutCollection() {
        assertEquals(EditTaskRelativeProposalLocalAction.CONFIRM, local("Yes please", EditFieldTarget.NONE))
        assertEquals(EditTaskRelativeProposalLocalAction.REJECT, local("No", EditFieldTarget.NONE))
        assertEquals(EditTaskRelativeProposalLocalAction.REPEAT, local("repeat the proposal", EditFieldTarget.NONE))
        val resolution = EditTaskMoveResolution(EditTaskSemanticMove.CHANGE_DATE, dateText = "same time tomorrow",
            source = EditTaskMoveSource.CONVERSATION_AGENT_PRIMARY, confidence = .97, agentAttempted = true, reason = "test")
        assertEquals(EditTaskRelativeProposalSemanticRoute.RELATIVE_TEMPORAL_CORRECTION,
            EditTaskRelativeProposalRoutingPolicy.semanticRoute(resolution, relativeProposalActive = true))
        assertEquals(EditTaskRelativeProposalSemanticRoute.EDIT_SEMANTIC,
            EditTaskRelativeProposalRoutingPolicy.semanticRoute(resolution, relativeProposalActive = false))
    }

    private suspend fun checkTemporalTransition(
        field: EditFieldTarget, requestMove: String, input: String, response: String,
        proposal: RelativeTemporalProposal
    ) {
        val session = session()
        val orchestrator = interpreter(json(requestMove), response)
        val request = orchestrator.resolve("Change the ${field.name.lowercase()} instead", context(EditFieldTarget.NONE))
        assertEquals(EditTaskSemanticMove.valueOf(requestMove), request.move)
        val collection = foreground(field)
        val value = orchestrator.resolve(input, context(field))
        assertTrue(context(field).relativeTemporalProposalActive)
        assertFalse(collection == EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION)
        assertEquals(EditTaskRelativeProposalSemanticRoute.RELATIVE_TEMPORAL_CORRECTION,
            EditTaskRelativeProposalRoutingPolicy.semanticRoute(value, session.state == RelativeTemporalProposalState.ACTIVE))
        val token = session.beginCorrection()
        val calculated = RelativeTemporalChangeCalculator().calculate(
            session.authoritativeOriginal, session.currentProposal, proposal, now()
        ) as RelativeTemporalCalculationResult.Success
        val result = session.applyCorrection(token, calculated.schedule, proposal)
        assertTrue(EditTaskInteractionPolicy.completesTemporalCollection(collection, result))
        val nextField = if (EditTaskInteractionPolicy.completesTemporalCollection(collection, result)) EditFieldTarget.NONE else field
        assertEquals(EditFieldTarget.NONE, nextField)
        assertEquals(EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION, foreground(nextField))
        assertEquals(RelativeTemporalProposalState.ACTIVE, session.state)
        assertEquals(2, session.revision)
        assertEquals(if (field == EditFieldTarget.TIME) "07/09/2026" else "06/09/2026", session.currentProposal.date)
        assertEquals(if (field == EditFieldTarget.TIME) "11:00 PM" else "09:00 PM", session.currentProposal.time)
    }

    private fun foreground(field: EditFieldTarget, clarification: Boolean = false) =
        EditTaskInteractionPolicy.foregroundState(false, false, clarification, field, true, true)

    private fun local(input: String, field: EditFieldTarget, clarification: Boolean = false) =
        EditTaskRelativeProposalRoutingPolicy.localAction(input,
            EditTaskRelativeProposalControlSignals(input == "save it", false, input == "repeat the proposal", false),
            foreground(field, clarification))

    private fun context(field: EditFieldTarget) = EditTaskAgentContext(
        foreground(field), 1, field.name, false, true, false, false,
        "Take Medicine", "07/09/2026", "09:00 PM", "Take Medicine", "05/09/2026", "09:00 PM",
        "05/09/2026", "01:00 AM", "Asia/Kuala_Lumpur", EditTaskAgentContext.allowedMoves(foreground(field)))

    private fun interpreter(vararg responses: String): EditTaskSemanticOrchestrator {
        val queue = ArrayDeque(responses.toList())
        return EditTaskSemanticOrchestrator(EditTaskSemanticClient { _, _ -> queue.removeFirst() })
    }

    private fun session() = RelativeTemporalProposalSession(
        ExactTemporalSchedule("05/09/2026", "09:00 PM"),
        ExactTemporalSchedule("07/09/2026", "09:00 PM"), temporalProposal(date = "Monday"))

    private fun temporalProposal(date: String = "", timeOffset: Int = 0) = RelativeTemporalProposal(
        if (date.isNotEmpty()) RelativeTemporalOperation.SET else RelativeTemporalOperation.KEEP,
        if (timeOffset != 0) RelativeTemporalOperation.OFFSET else RelativeTemporalOperation.KEEP,
        RelativeTemporalBase.CURRENT_PROPOSAL, date, "", 0, timeOffset, .97, false)

    private fun now() = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur")).apply {
        clear()
        set(2026, java.util.Calendar.SEPTEMBER, 5, 1, 0)
    }

    private fun json(move: String, title: String = "", date: String = "", time: String = "") =
        """{"move":"$move","title":"$title","date_text":"$date","time_text":"$time","confidence":0.97}"""
}
