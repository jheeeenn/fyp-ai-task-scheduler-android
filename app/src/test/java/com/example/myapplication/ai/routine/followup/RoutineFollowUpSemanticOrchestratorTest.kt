package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.routine.PendingRoutineDraft
import com.example.myapplication.ai.routine.PendingRoutineStep
import com.example.myapplication.ai.routine.RoutineDraftController
import com.example.myapplication.ai.routine.RoutineDraftState
import com.example.myapplication.ai.routine.RoutineDraftUpdate
import com.example.myapplication.ai.routine.RoutineExtractionResponse
import com.example.myapplication.ai.routine.RoutineFollowUpMove
import com.example.myapplication.ai.routine.RoutineStepExtraction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RoutineFollowUpSemanticOrchestratorTest {
    @Test
    fun semanticNaturalLanguageCasesProduceBoundedMoves() = runBlocking {
        val cases = listOf(
            Case(
                RoutineDraftState.COLLECTING_SHARED_DATE,
                "use the first of August 2026",
                "PROVIDE_SHARED_DATE", 0, "the first of August 2026",
                RoutineFollowUpMove.ProvideSharedDate("the first of August 2026")
            ),
            Case(
                RoutineDraftState.COLLECTING_SHARED_DATE,
                "actually make it next Tuesday",
                "PROVIDE_SHARED_DATE", 0, "next Tuesday",
                RoutineFollowUpMove.ProvideSharedDate("next Tuesday")
            ),
            Case(
                RoutineDraftState.COLLECTING_STEP_TIME,
                "use 8:15 AM for that one",
                "PROVIDE_STEP_TIME", 0, "8:15 AM",
                RoutineFollowUpMove.ProvideStepTime("8:15 AM")
            ),
            Case(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                "that looks right, go ahead",
                "CONFIRM", 0, "", RoutineFollowUpMove.Confirm
            ),
            Case(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                "no, make the second one 8:30 PM",
                "CHANGE_STEP_TIME", 2, "8:30 PM",
                RoutineFollowUpMove.ChangeStepTime(1, "8:30 PM")
            ),
            Case(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                "could you call the third step charge my phone",
                "CHANGE_STEP_TITLE", 3, "charge my phone",
                RoutineFollowUpMove.ChangeStepTitle(2, "charge my phone")
            ),
            Case(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                "say the routine again",
                "REPEAT", 0, "", RoutineFollowUpMove.Repeat
            ),
            Case(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                "add another step",
                "STRUCTURAL_CHANGE", 0, "", RoutineFollowUpMove.StructuralChange
            ),
            Case(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                "yes no",
                "UNKNOWN", 0, "", RoutineFollowUpMove.Unknown,
                confidence = 0.40
            )
        )
        cases.forEach { case ->
            var calls = 0
            val client = RoutineFollowUpSemanticClient { _, _ ->
                calls += 1
                json(case.move, case.stepIndex, case.value, case.confidence)
            }
            val orchestrator = RoutineFollowUpSemanticOrchestrator(client)
            val local = orchestrator.proposeLocal(case.input)
            val context = RoutineFollowUpAgentContext.capture(
                case.state,
                draft(),
                local
            )
            val result = orchestrator.resolveSemantic(case.input, context, local)
            assertEquals("input=${case.input}", case.expected, result.move)
            assertEquals("input=${case.input}", 1, calls)
        }
    }

    @Test
    fun safeLocalControlsAndExactRevisionsNeedNoSemanticCall() {
        var calls = 0
        val orchestrator = RoutineFollowUpSemanticOrchestrator(
            RoutineFollowUpSemanticClient { _, _ ->
                calls += 1
                json("UNKNOWN", 0, "")
            }
        )
        listOf("no", "no no", "yes", "yes yes", "cancel", "repeat").forEach {
            val local = orchestrator.proposeLocal(it)
            val immediate = orchestrator.resolveImmediate(
                local,
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                draft()
            )
            assertTrue("input=$it", immediate != null)
        }
        listOf(
            "change the second time to 8:30 PM",
            "change the third task to charge my phone",
            "change the date to next Tuesday"
        ).forEach {
            val immediate = orchestrator.resolveImmediate(
                orchestrator.proposeLocal(it),
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                draft()
            )
            assertTrue("input=$it", immediate != null)
        }
        assertEquals(0, calls)
    }

    @Test
    fun oneSemanticResolutionMakesAtMostOneClientCall() = runBlocking {
        var calls = 0
        val orchestrator = RoutineFollowUpSemanticOrchestrator(
            RoutineFollowUpSemanticClient { _, _ ->
                calls += 1
                """{"not":"the schema"}"""
            }
        )
        val local = RoutineFollowUpMove.Unknown
        val result = orchestrator.resolveSemantic(
            "unclear follow up",
            RoutineFollowUpAgentContext.capture(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                draft(),
                local
            ),
            local
        )
        assertEquals(RoutineFollowUpMove.Unknown, result.move)
        assertEquals(1, calls)
        assertTrue(result.agentAttempted)
    }

    @Test
    fun validRawDateAndTimeSkipSemanticWhileRejectedValuesPermitOneRevalidatedCall() =
        runBlocking {
            var calls = 0
            var response = json("PROVIDE_SHARED_DATE", 0, "1st August 2026")
            val orchestrator = RoutineFollowUpSemanticOrchestrator(
                RoutineFollowUpSemanticClient { _, _ ->
                    calls += 1
                    response
                }
            )
            val controller = collectionController()
            val token = controller.beginExtraction()
            controller.applyExtraction(token, collectionExtraction())

            val validRawDate = controller.provideSharedDate("1st August 2026")
            assertFalse(
                RoutineFollowUpSemanticFallbackPolicy.afterRawSharedDate(
                    validRawDate,
                    controller.state
                )
            )
            assertEquals(0, calls)
            assertEquals(RoutineDraftState.COLLECTING_STEP_TIME, controller.state)

            val validRawTime = controller.provideNextStepTime("8:15 AM")
            assertFalse(
                RoutineFollowUpSemanticFallbackPolicy.afterRawStepTime(
                    validRawTime,
                    controller.state
                )
            )
            assertEquals(0, calls)
            assertEquals(RoutineDraftState.WAITING_FOR_CONFIRMATION, controller.state)

            val dateController = collectionController()
            val dateToken = dateController.beginExtraction()
            dateController.applyExtraction(dateToken, collectionExtraction())
            val rejectedDate = dateController.provideSharedDate("use the date I mentioned")
            assertTrue(
                RoutineFollowUpSemanticFallbackPolicy.afterRawSharedDate(
                    rejectedDate,
                    dateController.state
                )
            )
            val dateLocal = RoutineFollowUpMove.Unknown
            val dateResolution = orchestrator.resolveSemantic(
                "use the date I mentioned",
                RoutineFollowUpAgentContext.capture(
                    dateController.state,
                    dateController.draft,
                    dateLocal
                ),
                dateLocal
            )
            val revalidatedDate = dateController.provideSharedDate(
                (dateResolution.move as RoutineFollowUpMove.ProvideSharedDate).value
            )
            assertTrue(revalidatedDate is RoutineDraftUpdate.Ask)
            assertEquals(1, calls)

            response = json("PROVIDE_STEP_TIME", 0, "8:15 AM")
            val rejectedTime = dateController.provideNextStepTime("use that time")
            assertTrue(
                RoutineFollowUpSemanticFallbackPolicy.afterRawStepTime(
                    rejectedTime,
                    dateController.state
                )
            )
            val timeResolution = orchestrator.resolveSemantic(
                "use that time",
                RoutineFollowUpAgentContext.capture(
                    dateController.state,
                    dateController.draft,
                    RoutineFollowUpMove.Unknown
                ),
                RoutineFollowUpMove.Unknown
            )
            val revalidatedTime = dateController.provideNextStepTime(
                (timeResolution.move as RoutineFollowUpMove.ProvideStepTime).value
            )
            assertTrue(revalidatedTime is RoutineDraftUpdate.Review)
            assertEquals(2, calls)
        }

    @Test
    fun cancellationExceptionIsRethrown() {
        val orchestrator = RoutineFollowUpSemanticOrchestrator(
            RoutineFollowUpSemanticClient { _, _ -> throw CancellationException("cancel") }
        )
        assertThrows(CancellationException::class.java) {
            runBlocking {
                orchestrator.resolveSemantic(
                    "candidate",
                    RoutineFollowUpAgentContext.capture(
                        RoutineDraftState.COLLECTING_SHARED_DATE,
                        draft(),
                        RoutineFollowUpMove.Unknown
                    ),
                    RoutineFollowUpMove.Unknown
                )
            }
        }
    }

    @Test
    fun deliveryGuardDropsStaleRequestStateAndRevision() {
        assertTrue(
            RoutineFollowUpDeliveryGuard.shouldDeliver(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                4,
                4,
                requestCurrent = true
            )
        )
        assertFalse(
            RoutineFollowUpDeliveryGuard.shouldDeliver(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                4,
                4,
                requestCurrent = false
            )
        )
        assertFalse(
            RoutineFollowUpDeliveryGuard.shouldDeliver(
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                RoutineDraftState.WAITING_FOR_CONFIRMATION,
                4,
                5,
                requestCurrent = true
            )
        )
        assertFalse(
            RoutineFollowUpDeliveryGuard.shouldDeliver(
                RoutineDraftState.COLLECTING_SHARED_DATE,
                RoutineDraftState.COLLECTING_STEP_TIME,
                4,
                4,
                requestCurrent = true
            )
        )
    }

    private fun draft() = PendingRoutineDraft(
        title = "Morning routine",
        steps = listOf(
            step("take medicine", "08:00"),
            step("prepare breakfast", ""),
            step("leave home", "09:00")
        ),
        revision = 4
    )

    private fun collectionController() = RoutineDraftController {
        Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 28, 7, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun collectionExtraction() = RoutineExtractionResponse(
        routineTitle = "Morning routine",
        steps = listOf(
            RoutineStepExtraction("take medicine", "", "8 AM"),
            RoutineStepExtraction("prepare breakfast", "", ""),
            RoutineStepExtraction("leave home", "", "9 AM")
        ),
        confidence = 0.95,
        needClarification = false
    )

    private fun step(title: String, time: String) = PendingRoutineStep(
        title = title,
        originalDateText = "4 August 2026",
        originalTimeText = time,
        resolvedDate = "04/08/2026",
        resolvedTime = time.ifBlank { null }
    )

    private fun json(
        move: String,
        stepIndex: Int,
        value: String,
        confidence: Double = 0.96
    ) = """{"move":"$move","step_index":$stepIndex,"value":"$value","confidence":$confidence}"""

    private data class Case(
        val state: RoutineDraftState,
        val input: String,
        val move: String,
        val stepIndex: Int,
        val value: String,
        val expected: RoutineFollowUpMove,
        val confidence: Double = 0.96
    )
}
