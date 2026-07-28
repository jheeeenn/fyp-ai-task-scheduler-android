package com.example.myapplication.ai.routine

import com.example.myapplication.ai.routine.saved.SavedRoutineAction
import com.example.myapplication.ai.routine.saved.SavedRoutineActionConsistencyPolicy
import com.example.myapplication.ai.routine.saved.SavedRoutineActionDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SavedRoutineActionConsistencyPolicyTest {
    @Test
    fun explicitSingularReadReconcilesModelListUsingOnlyLiteralTitle() {
        listOf(
            "read my routine",
            "what is in my routine",
            "read the routine"
        ).forEach { input ->
            val result = SavedRoutineActionConsistencyPolicy.reconcile(
                input,
                decision(SavedRoutineAction.LIST)
            )

            assertEquals(SavedRoutineAction.READ_DETAILS, result.decision.action)
            assertEquals("routine", result.decision.routineTitle)
            assertEquals("", result.decision.dateText)
            assertTrue(result.titleRecoveredFromLiteralText)
        }
    }

    @Test
    fun pluralListStaysListAndValidReadDetailsStaysUnchanged() {
        listOf(
            "what routines have i saved",
            "list my routines"
        ).forEach { input ->
            val model = decision(SavedRoutineAction.LIST)
            val result = SavedRoutineActionConsistencyPolicy.reconcile(input, model)

            assertEquals(model, result.decision)
            assertFalse(result.titleRecoveredFromLiteralText)
        }

        val details = decision(
            SavedRoutineAction.READ_DETAILS,
            title = "bedtime routine"
        )
        assertEquals(
            details,
            SavedRoutineActionConsistencyPolicy.reconcile(
                "read my bedtime routine",
                details
            ).decision
        )
    }

    @Test
    fun explicitRunAndDeleteRecoverOnlyLiteralUserFieldsFromModelList() {
        val run = SavedRoutineActionConsistencyPolicy.reconcile(
            "use my routine tomorrow",
            decision(SavedRoutineAction.LIST)
        )
        assertEquals(SavedRoutineAction.RUN, run.decision.action)
        assertEquals("routine", run.decision.routineTitle)
        assertEquals("tomorrow", run.decision.dateText)

        val delete = SavedRoutineActionConsistencyPolicy.reconcile(
            "delete my routine",
            decision(SavedRoutineAction.LIST)
        )
        assertEquals(SavedRoutineAction.DELETE, delete.decision.action)
        assertEquals("routine", delete.decision.routineTitle)
        assertEquals("", delete.decision.dateText)
    }

    @Test
    fun ambiguousBareRoutineDoesNotRewriteModelList() {
        val model = decision(SavedRoutineAction.LIST)

        val result = SavedRoutineActionConsistencyPolicy.reconcile("routine", model)

        assertEquals(model, result.decision)
        assertEquals("NO_STRONG_ACTION_CUE", result.reason)
        assertFalse(result.titleRecoveredFromLiteralText)
    }

    @Test
    fun policyHasNoRoomIdsQueriesOrSemanticCalls() {
        val source = File(
            "src/main/java/com/example/myapplication/ai/routine/saved/" +
                "SavedRoutineActionConsistencyPolicy.kt"
        ).readText()

        assertFalse(source.contains("routineId"))
        assertFalse(source.contains("Room"))
        assertFalse(source.contains("AppDatabase"))
        assertFalse(source.contains("RoutineDao"))
        assertFalse(source.contains("SavedRoutineSemanticClient"))
        assertFalse(source.contains("interpretSavedRoutineAction"))
    }

    @Test
    fun unsafeStrongContradictionFailsClosedAsUnknown() {
        val result = SavedRoutineActionConsistencyPolicy.reconcile(
            "read my routine",
            decision(SavedRoutineAction.DELETE, title = "routine")
        )

        assertEquals(SavedRoutineAction.UNKNOWN, result.decision.action)
        assertEquals("", result.decision.routineTitle)
        assertEquals("", result.decision.dateText)
    }

    private fun decision(
        action: SavedRoutineAction,
        title: String = "",
        date: String = ""
    ) = SavedRoutineActionDecision(
        action = action,
        routineTitle = title,
        dateText = date,
        confidence = 0.98
    )
}
