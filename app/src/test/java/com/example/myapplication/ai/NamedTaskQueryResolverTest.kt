package com.example.myapplication.ai

import com.example.myapplication.ai.agent.ActionValidator
import com.example.myapplication.ai.agent.TaskActionNormalizer
import com.example.myapplication.ai.agent.TaskAgentResponse
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationOrchestrator
import com.example.myapplication.ai.conversation.taskcontext.ContextFocusCarryForwardPolicy
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextReadValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextResponseRenderer
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextStore
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NamedTaskQueryResolverTest {
    private val resolver = NamedTaskQueryResolver()
    private val book = TaskEntity(id = 81, title = "Read Book", dueDate = "31/08/2026", dueTime = "7:00 PM")

    @Test
    fun modelSuppliesOnlySemanticsWhileRoomAndTaskMatcherSupplyTheRenderedSchedule() {
        mapOf(
            TaskQueryDetail.TIME to "Read Book is scheduled at 7 PM.",
            TaskQueryDetail.DATE to "Read Book is scheduled for 31 August.",
            TaskQueryDetail.DATE_TIME to "Read Book is scheduled for 31 August at 7 PM."
        ).forEach { (detail, expected) ->
            val command = ActionValidator().validate(TaskActionNormalizer().normalize(TaskAgentResponse(
                action = "QUERY_TASK", target_task_title = "Read Book", query_detail = detail.name,
                query_presentation = "DETAILS", confidence = 0.97f
            )))
            val result = resolver.resolve(requireNotNull(command.targetTaskTitle), listOf(book,
                TaskEntity(id = 82, title = "Visit Bank", dueTime = "2:00 PM")))
            assertEquals(NamedTaskQueryStatus.RESOLVED, result.status)
            assertEquals(book, result.task)
            assertEquals(1.0, result.score, 0.0)
            assertNull(command.dateText)
            assertNull(command.timeText)
            assertNull(command.naturalResponse)
            assertEquals(expected, render(requireNotNull(result.task), command.queryDetail))
        }
    }

    @Test
    fun noMatchBlankTitleAndAmbiguousMatchesNeverSelectATask() {
        listOf("Repair Bicycle", "").forEach { title ->
            val result = resolver.resolve(title, listOf(book))
            assertEquals(NamedTaskQueryStatus.NOT_FOUND, result.status)
            assertNull(result.task)
        }
        val ambiguous = resolver.resolve("Read Book", listOf(book, book.copy(id = 82, dueTime = "8:00 PM")))
        assertEquals(NamedTaskQueryStatus.AMBIGUOUS, ambiguous.status)
        assertNull(ambiguous.task)
        assertEquals(1.0, ambiguous.score, 0.0)
    }

    @Test
    fun namedReadUsesTheNormalActiveRootTaskCompletionPolicy() {
        val result = resolver.resolve("Read Book", listOf(book.copy(isDone = true),
            book.copy(id = 82, parentTaskId = 80)))
        assertEquals(NamedTaskQueryStatus.NOT_FOUND, result.status)
        assertNull(result.task)
        assertEquals(book, resolver.resolve("Read Book", listOf(book, book.copy(id = 83, isDone = true))).task)
    }

    @Test
    fun explicitQualifiersNarrowCandidatesButRequestedDetailAndTitleNeverBecomeFilters() {
        val tomorrowBook = book.copy(id = 82, dueDate = "01/09/2026", dueTime = "8:00 PM")
        assertEquals(book, resolver.resolve("Read Book", listOf(book, tomorrowBook),
            targetDateText = "31/08/2026").task)
        assertEquals(tomorrowBook, resolver.resolve("Read Book", listOf(book, tomorrowBook),
            targetTimeText = "8:00 PM").task)
        val titleWithTemporalWords = book.copy(title = "Tomorrow Plan")
        assertEquals(titleWithTemporalWords,
            resolver.resolve("Tomorrow Plan", listOf(titleWithTemporalWords)).task)
        val invalid = resolver.resolve("Read Book", listOf(book), targetTimeText = "time")
        assertEquals(NamedTaskQueryStatus.UNRESOLVED_TEMPORAL, invalid.status)
        assertNull(invalid.task)
    }

    @Test
    fun absentScheduleComponentsUseTheExistingDeterministicSpeech() {
        assertEquals("It does not have a date.", render(book.copy(dueDate = null), TaskQueryDetail.DATE))
        assertEquals("It does not have a time.", render(book.copy(dueTime = ""), TaskQueryDetail.TIME))
        assertEquals("Read Book does not have a date or time set.",
            render(book.copy(dueDate = null, dueTime = null), TaskQueryDetail.DATE_TIME))
        assertEquals("Read Book has no date set. Its time is 7 PM.",
            render(book.copy(dueDate = null), TaskQueryDetail.DATE_TIME))
        assertEquals("Read Book is scheduled for 31 August, with no time set.",
            render(book.copy(dueTime = null), TaskQueryDetail.DATE_TIME))
        assertTrue(runCatching { render(book, TaskQueryDetail.NONE) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun publishingAnAuthoritativeMatchedTaskEstablishesGenerationBoundFocus() {
        val matched = requireNotNull(resolver.resolve("Read Book", listOf(book)).task)
        val store = ReadOnlyTaskContextStore()
        store.replaceTaskDetailResult(matched)
        val capture = store.capture()
        val item = capture.snapshot.items.single()
        val orchestrator = ConversationOrchestrator(ConversationAgentClient(null), ConversationDecisionParser())
        orchestrator.setAuthoritativeContextFocus(item, item.ref, capture.snapshot.generation)
        val focus = requireNotNull(orchestrator.contextFocusForSnapshot(capture.snapshot))
        assertEquals(TaskContextScope.TASK_DETAIL, capture.snapshot.scope)
        assertEquals(book.id, store.resolveRef(focus.ref, capture.snapshot.generation))
        assertEquals(book.title, focus.title)
        val followUp = requireNotNull(ContextFocusCarryForwardPolicy.resolve(
            "when is it", focus, capture.snapshot, isResultInteraction = true
        ))
        val validation = ReadOnlyTaskContextReadValidator.validate(followUp, capture.snapshot, store.currentGeneration())
        assertTrue(validation.isValid)
        assertEquals("Read Book is scheduled for 31 August at 7 PM.",
            ReadOnlyTaskContextResponseRenderer.render(requireNotNull(validation.item), validation.detail))
        store.clear()
        assertNull(orchestrator.contextFocusForSnapshot(store.snapshot()))
    }

    @Test
    fun homeWiresTheNamedBranchToAuthoritativeRefetchRenderingAndExistingFocusPublication() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val handler = source.substringAfter("private fun handleNamedTaskQuery(")
            .substringBefore("private fun handleQueryTask(")
        assertTrue(source.contains("!aiResult.targetTaskTitle.isNullOrBlank() && aiResult.queryDetail != TaskQueryDetail.NONE"))
        listOf("dao.getRootTasks()", "namedTaskQueryResolver.resolve(targetTitle", "dao.getById(matched.id)",
            "currentTask != matched", "ReadOnlyTaskContextResponseRenderer.renderSchedule(",
            "publishTaskDetailAssistantContext(currentTask, subtasks)", "isAssistantRequestCurrent(requestToken)",
            "queryGeneration != queryReadingStateGeneration"
        ).forEach { assertTrue(it, handler.contains(it)) }
        listOf("naturalResponse", "agentOrchestrator", "processTaskCommand", "speakObservation", "\"T1\"",
            "normalized.contains", "Regex(").forEach { assertFalse(it, handler.contains(it)) }
    }

    private fun render(task: TaskEntity, detail: TaskQueryDetail) =
        ReadOnlyTaskContextResponseRenderer.renderSchedule(task.title, task.dueDate, task.dueTime, detail)
}
