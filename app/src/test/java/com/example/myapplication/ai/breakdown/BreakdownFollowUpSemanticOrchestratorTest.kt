package com.example.myapplication.ai.breakdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class BreakdownFollowUpSemanticOrchestratorTest {
    @Test
    fun naturalRevisionReturnsCompleteValidatedPlanToConfirmation() = runBlocking {
        val controller = reviewController()
        val captured = requireNotNull(controller.draft)
        val orchestrator = BreakdownFollowUpSemanticOrchestrator(
            client = BreakdownFollowUpSemanticClient { _, _ ->
                """
                {"move":"REVISE","plan":["Review the literature","Compare prior methods","Draft findings"],"confidence":0.97}
                """.trimIndent()
            }
        )

        val decision = orchestrator.interpret(
            "Make the steps more specific",
            BreakdownFollowUpContext.capture(controller.state, captured)
        )
        val update = controller.applyRevision(
            captured.generation,
            captured.revision,
            decision.plan
        ) as BreakdownDraftUpdate.Review

        assertEquals(BreakdownFollowUpMove.REVISE, decision.move)
        assertEquals(BreakdownDraftState.WAITING_FOR_CONFIRMATION, controller.state)
        assertEquals(2L, update.draft.revision)
        assertEquals(decision.plan, update.draft.proposedSubtasks)
    }

    @Test
    fun naturalConfirmationUsesSemanticFallbackButArbitraryFeedbackDoesNot() =
        runBlocking {
            val controller = reviewController()
            val context = BreakdownFollowUpContext.capture(
                controller.state,
                requireNotNull(controller.draft)
            )
            val confirmation = BreakdownFollowUpSemanticOrchestrator(
                client = BreakdownFollowUpSemanticClient { _, _ ->
                    """{"move":"CONFIRM","plan":[],"confidence":0.96}"""
                }
            ).interpret("That looks good, go ahead", context)
            val unknown = BreakdownFollowUpSemanticOrchestrator(
                client = BreakdownFollowUpSemanticClient { _, _ ->
                    """{"move":"UNKNOWN","plan":[],"confidence":0.91}"""
                }
            ).interpret("Tell me something interesting", context)

            assertEquals(BreakdownFollowUpMove.CONFIRM, confirmation.move)
            assertEquals(BreakdownFollowUpMove.UNKNOWN, unknown.move)
            assertEquals(
                BreakdownFollowUpMove.UNKNOWN,
                BreakdownControlInterpreter.interpret("That looks good, go ahead")
            )
        }

    @Test
    fun contextAndDraftDiagnosticsNeverExposePrivateRoomId() {
        val controller = reviewController(parentId = 987654321)
        val draft = requireNotNull(controller.draft)
        val prompt = BreakdownFollowUpContext.capture(
            controller.state,
            draft
        ).toPromptText()

        assertFalse(prompt.contains("987654321"))
        assertFalse(draft.toString().contains("987654321"))
        assertTrue(draft.toString().contains("<redacted>"))
    }

    @Test
    fun nestedPlanObjectsAndLowConfidenceFailClosed() {
        val parser = BreakdownFollowUpDecisionParser()
        assertThrows(BreakdownFollowUpException::class.java) {
            parser.parse(
                """{"move":"REVISE","plan":[{"title":"one"}],"confidence":0.9}"""
            )
        }
        assertThrows(BreakdownFollowUpException::class.java) {
            BreakdownFollowUpDecisionValidator().validate(
                BreakdownFollowUpDecision(
                    BreakdownFollowUpMove.CONFIRM,
                    emptyList(),
                    0.59
                ),
                BreakdownDraftState.WAITING_FOR_CONFIRMATION
            )
        }
    }

    private fun reviewController(parentId: Long? = null): BreakdownDraftController {
        val controller = BreakdownDraftController()
        val resolving = controller.beginDraft(
            parentTitle = "Final year project",
            proposedSubtasks = listOf("Review literature", "Draft methodology"),
            originalRequest = "break down final year project",
            dateText = "30/07/2026",
            timeText = "4 PM"
        ) as BreakdownDraftUpdate.Resolving
        if (parentId == null) {
            controller.applyNewRoot(resolving.draft.generation)
        } else {
            controller.applyExistingRoot(
                resolving.draft.generation,
                com.example.myapplication.data.TaskEntity(
                    id = parentId,
                    title = "Final year project"
                ),
                hasSubtasks = false
            )
        }
        return controller
    }
}
