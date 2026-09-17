package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.*
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthoritativeSubtaskReaderTest {
    private val parent = TaskEntity(id = 918273645, title = "Prepare presentation")
    // Deliberately not ID or alphabetic order: preserve the DAO's order.
    private val children = listOf(
        TaskEntity(id = 827364519, title = "create the outline", parentTaskId = parent.id, subtaskOrder = 0),
        TaskEntity(id = 736451928, title = "prepare the slides", parentTaskId = parent.id, subtaskOrder = 1),
        TaskEntity(id = 945182736, title = "rehearse the demo", parentTaskId = parent.id, subtaskOrder = 2)
    )

    @Test fun publishesOrderedPrivateRefsAndReadsExactTitles() = runBlocking {
        val store = ReadOnlyTaskContextStore().apply { replaceTaskDetailResult(parent) }
        val speech = AuthoritativeSubtaskReader(store, { parent }, { children })
            .read("T1", store.currentGeneration())
        assertEquals("Prepare presentation has three subtasks. First, create the outline. Second, prepare the slides. Third, rehearse the demo.", speech)
        val capture = store.capture()
        assertEquals(TaskContextScope.SUBTASK_LIST, capture.snapshot.scope)
        assertEquals(listOf("T1", "T2", "T3"), capture.snapshot.items.map { it.ref })
        assertEquals(children.map { it.title }, capture.snapshot.items.map { it.title })
        children.forEachIndexed { index, child ->
            val ref = "T${index + 1}"
            assertEquals(child.id, store.resolveRef(ref, capture.snapshot.generation))
            assertTrue(store.matchesResolvedTask(ref, capture.snapshot.generation, child))
            assertFalse(store.matchesResolvedTask(ref, capture.snapshot.generation, child.copy(title = "Changed")))
        }
        (children + parent).forEach {
            assertFalse(capture.promptText.contains(it.id.toString()))
            assertFalse(requireNotNull(speech).contains(it.id.toString()))
        }
        store.clear()
        assertNull(store.resolveRef("T1", capture.snapshot.generation))
    }

    @Test fun noChildrenUsesFetchedParentAndPreservesContext() = runBlocking {
        val store = ReadOnlyTaskContextStore().apply { replaceTaskDetailResult(parent) }
        assertEquals("Prepare presentation has no subtasks.",
            AuthoritativeSubtaskReader(store, { parent }, { emptyList() }).read("T1", store.currentGeneration()))
        assertEquals(TaskContextScope.TASK_DETAIL, store.snapshot().scope)
    }

    @Test fun staleGenerationAndChangedParentFailClosed() = runBlocking {
        val store = ReadOnlyTaskContextStore().apply { replaceTaskDetailResult(parent) }
        val generation = store.currentGeneration()
        assertNull(AuthoritativeSubtaskReader(store, { parent.copy(title = "Changed") }, { error("must not fetch") })
            .read("T1", generation))
        store.clear()
        assertNull(AuthoritativeSubtaskReader(store, { error("must not fetch") }, { children }).read("T1", generation))
    }

    @Test fun replacementDuringFetchCannotPublishStaleChildren() = runBlocking {
        val store = ReadOnlyTaskContextStore().apply { replaceTaskDetailResult(parent) }
        val reader = AuthoritativeSubtaskReader(store, { parent }, {
            store.clear()
            children
        })
        assertNull(reader.read("T1", store.currentGeneration()))
        assertEquals(TaskContextScope.NONE, store.snapshot().scope)
    }

    @Test fun longSavedTitleIsSpokenWithoutPromptTruncation() = runBlocking {
        val store = ReadOnlyTaskContextStore().apply { replaceTaskDetailResult(parent) }
        val child = children.first().copy(title = "Exact title ".repeat(20).trim())
        val speech = AuthoritativeSubtaskReader(store, { parent }, { listOf(child) }).read("T1", store.currentGeneration())
        assertTrue(requireNotNull(speech).contains(child.title))
    }

    @Test fun childContextDoesNotAuthorizeMutations() {
        val store = ReadOnlyTaskContextStore().apply { replaceSubtaskList(children) }
        ConversationContextAction.entries.filter { it != ConversationContextAction.NONE }.forEach { action ->
            assertFalse(ContextActionDecisionValidator.validate(
                ConversationDecision(route = ConversationRoute.CONTEXT_ACTION, contextRef = "T1", contextAction = action, confidence = 1.0),
                store.snapshot(), store.currentGeneration()
            ).isValid)
        }
    }
}
