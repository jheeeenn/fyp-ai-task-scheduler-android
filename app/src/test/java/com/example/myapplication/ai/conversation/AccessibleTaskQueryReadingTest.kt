package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.conversation.query.AccessibleTaskQuerySession
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextStore
import com.example.myapplication.ai.temporal.TemporalDateScope
import com.example.myapplication.ai.temporal.TemporalQueryWindow
import com.example.myapplication.ai.temporal.TemporalResolutionStatus
import com.example.myapplication.data.TaskEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibleTaskQueryReadingTest {
    @Test
    fun oneTwoAndFiveMatchingTasksAreAllInTheSinglePage() {
        listOf(1, 2, 5).forEach { count ->
            val session = session(count)
            assertEquals(count, session.currentPageTasks.size)
            assertEquals((1..count).map { "Task $it" }, session.currentPageTasks.map { it.title })
            assertFalse(session.hasNextPage)
        }
    }

    @Test
    fun oneMatchingTaskIsActuallySpoken() {
        val response = renderPage(session(1), TaskQuerySpeechDetail.BRIEF)

        assertTrue(response.speech.contains("First, Task 1 at 08:00."))
    }

    @Test
    fun eightTasksProduceFiveThenThreeWithoutRequerying() {
        val first = session(8)
        val second = requireNotNull(first.advanceOnePage())

        assertEquals(5, first.currentPageTasks.size)
        assertEquals(listOf("Task 1", "Task 2", "Task 3", "Task 4", "Task 5"), first.currentPageTasks.map { it.title })
        assertEquals(3, second.currentPageTasks.size)
        assertEquals(listOf("Task 6", "Task 7", "Task 8"), second.currentPageTasks.map { it.title })
        assertFalse(second.hasNextPage)
        val secondSpeech = renderPage(second, TaskQuerySpeechDetail.BRIEF).speech
        assertTrue(secondSpeech.contains("First in this group, Task 6"))
        assertTrue(secondSpeech.contains("Third in this group, Task 8"))
    }

    @Test
    fun briefReplyLengthStillSpeaksEveryTaskInThePage() {
        val response = renderPage(session(2), TaskQuerySpeechDetail.BRIEF)

        assertTrue(response.speech.contains("First, Task 1"))
        assertTrue(response.speech.contains("Second, Task 2"))
    }

    @Test
    fun replyLengthChangesDetailButNeverCoverage() {
        val page = session(5)
        val brief = renderPage(page, TaskQuerySpeechDetail.BRIEF)
        val detailed = renderPage(page, TaskQuerySpeechDetail.DETAILED)

        (1..5).forEach { number ->
            assertTrue(brief.speech.contains("Task $number"))
            assertTrue(detailed.speech.contains("Task $number"))
        }
        assertFalse(brief.speech.contains("unfinished:"))
        assertTrue(detailed.speech.contains("unfinished: Step one"))
    }

    @Test
    fun explicitDetailsUsesFullerRepresentationEvenWhenDefaultWouldBeBrief() {
        val page = session(1, presentation = TaskQueryPresentation.DETAILS)
        val response = renderPage(page, TaskQuerySpeechDetail.DETAILED)

        assertTrue(response.speech.contains("on 20 July 2026 at 08:00"))
        assertTrue(response.speech.contains("one is unfinished: Step one"))
    }

    @Test
    fun multiDateOverviewIncludesDatesAndExactDateOverviewDoesNotRepeatThem() {
        val multiDate = renderPage(
            session(2, exactDate = false),
            TaskQuerySpeechDetail.BRIEF,
            includeDates = true
        )
        val exactDate = renderPage(
            session(2, exactDate = true),
            TaskQuerySpeechDetail.BRIEF,
            includeDates = false
        )

        assertTrue(multiDate.speech.contains("20 July 2026"))
        assertFalse(exactDate.speech.contains("20 July 2026"))
        assertTrue(exactDate.speech.contains("at 08:00"))
    }

    @Test
    fun continuationReplacesContextOnceAndMakesOldGenerationStale() {
        val store = ReadOnlyTaskContextStore()
        val first = session(8)
        store.replaceRecentQueryResults(first.currentPageTasks)
        val firstCapture = store.capture()
        val firstGeneration = store.currentGeneration()

        val second = requireNotNull(first.advanceOnePage())
        store.replaceRecentQueryResults(second.currentPageTasks)

        assertEquals(firstGeneration + 1, store.currentGeneration())
        assertEquals(listOf("Task 6", "Task 7", "Task 8"), store.snapshot().items.map { it.title })
        assertNotEquals(firstCapture.snapshot.generation, store.currentGeneration())
        assertEquals(7L, store.resolveRef("T2", store.currentGeneration()))
    }

    @Test
    fun pageOneStructuredFocusIsStaleAfterPublishingPageTwo() {
        val store = ReadOnlyTaskContextStore()
        val memory = ConversationSessionMemory()
        val first = session(8)
        store.replaceRecentQueryResults(first.currentPageTasks)
        val firstSnapshot = store.snapshot()
        memory.recordAuthoritativeContextRead(
            item = firstSnapshot.items[1],
            selectedRef = "T2",
            selectedDetail = ConversationContextDetail.TIME,
            capturedGeneration = firstSnapshot.generation,
            finalSpeech = "Task two is at eight."
        )

        val second = requireNotNull(first.advanceOnePage())
        store.replaceRecentQueryResults(second.currentPageTasks)
        val secondSnapshot = store.snapshot()

        assertNull(
            memory.contextFocusForGeneration(
                secondSnapshot.generation,
                secondSnapshot.items.map { it.ref }.toSet()
            )
        )
        assertTrue(
            memory.clearInvalidContextFocus(
                secondSnapshot.generation,
                secondSnapshot.items.map { it.ref }.toSet()
            )
        )
    }

    @Test
    fun repeatKeepsPageOrderingAndContextGeneration() {
        val store = ReadOnlyTaskContextStore()
        val page = requireNotNull(session(8).advanceOnePage())
        store.replaceRecentQueryResults(page.currentPageTasks)
        val generation = store.currentGeneration()
        val firstSpeech = renderPage(page, TaskQuerySpeechDetail.BRIEF).speech
        val repeatedSpeech = renderPage(page, TaskQuerySpeechDetail.BRIEF).speech

        assertEquals(generation, store.currentGeneration())
        assertEquals(firstSpeech, repeatedSpeech)
        assertEquals(listOf("Task 6", "Task 7", "Task 8"), store.snapshot().items.map { it.title })
    }

    @Test
    fun countOnlyExposesNoTasksOrRefs() {
        val store = ReadOnlyTaskContextStore()
        store.replaceRecentQueryResults(tasks(3))
        store.clear()
        val countObservation = countObservation(session(8))
        val json = JSONObject(countObservation.toAgentJson())

        assertTrue(store.snapshot().items.isEmpty())
        assertTrue(countObservation.tasks.isEmpty())
        assertEquals(0, json.getJSONArray("tasks").length())
        assertTrue(AndroidObservationResponseRenderer.render(countObservation).speech.contains("Would you like me to read them?"))
    }

    @Test
    fun deterministicObservationContainsOnlyTheCurrentPageAndNoIds() {
        val page = requireNotNull(session(8).advanceOnePage())
        val observation = pageObservation(page, TaskQuerySpeechDetail.BALANCED)
        val json = observation.toAgentJson()

        assertEquals(3, observation.tasks.size)
        assertEquals(listOf("Task 6", "Task 7", "Task 8"), observation.tasks.map { it.title })
        assertFalse(json.contains("task_id"))
        assertFalse(json.contains("\"id\""))
        assertFalse(json.contains("Room"))
    }

    @Test
    fun finalContinueCannotWrapToPageOne() {
        val finalPage = requireNotNull(session(8).advanceOnePage())

        assertEquals(null, finalPage.advanceOnePage())
        assertEquals(1, finalPage.currentPageIndex)
    }

    private fun renderPage(
        session: AccessibleTaskQuerySession,
        detail: TaskQuerySpeechDetail,
        includeDates: Boolean = !session.queryWindow.isExactDate ||
            session.presentation == TaskQueryPresentation.DETAILS
    ): ConversationResponse = AndroidObservationResponseRenderer.render(
        pageObservation(session, detail, includeDates)
    )

    private fun pageObservation(
        session: AccessibleTaskQuerySession,
        detail: TaskQuerySpeechDetail,
        includeDates: Boolean = !session.queryWindow.isExactDate ||
            session.presentation == TaskQueryPresentation.DETAILS
    ) = ExecutionObservation(
        operation = ExecutionOperation.QUERY_TASK,
        outcome = ExecutionOutcome.INFORMATION,
        taskCount = session.orderedTasks.size,
        tasks = session.currentPageTasks.map { task ->
            TaskObservationMapper.observedTask(
                task,
                listOf(TaskEntity(id = task.id + 100, title = "Step one", parentTaskId = task.id))
            )
        },
        queryPage = TaskQueryPageObservation(
            totalTaskCount = session.orderedTasks.size,
            pageStartPosition = session.currentPageStartPosition,
            pageEndPosition = session.currentPageEndPosition,
            pageNumber = session.currentPageIndex + 1,
            pageCount = session.pageCount,
            pageSize = session.pageSize,
            hasNextPage = session.hasNextPage,
            presentation = if (session.presentation == TaskQueryPresentation.DETAILS) {
                TaskQueryPresentationLevel.DETAILS
            } else {
                TaskQueryPresentationLevel.OVERVIEW
            },
            detailLevel = detail,
            tone = TaskQuerySpeechTone.NEUTRAL,
            includeTaskDates = includeDates,
            temporalLabel = if (session.queryWindow.isExactDate) "tomorrow" else "this week"
        ),
        listenAgain = true,
        fallbackSpeech = ""
    )

    private fun countObservation(session: AccessibleTaskQuerySession) = ExecutionObservation(
        operation = ExecutionOperation.QUERY_TASK,
        outcome = ExecutionOutcome.INFORMATION,
        taskCount = session.orderedTasks.size,
        tasks = emptyList(),
        queryPage = TaskQueryPageObservation(
            totalTaskCount = session.orderedTasks.size,
            pageStartPosition = 0,
            pageEndPosition = 0,
            pageNumber = 0,
            pageCount = session.pageCount,
            pageSize = session.pageSize,
            hasNextPage = session.hasNextPage,
            presentation = TaskQueryPresentationLevel.COUNT_ONLY,
            detailLevel = TaskQuerySpeechDetail.BRIEF,
            tone = TaskQuerySpeechTone.NEUTRAL,
            includeTaskDates = false,
            temporalLabel = "tomorrow"
        ),
        listenAgain = true,
        fallbackSpeech = ""
    )

    private fun session(
        count: Int,
        presentation: TaskQueryPresentation = TaskQueryPresentation.OVERVIEW,
        exactDate: Boolean = true
    ) = AccessibleTaskQuerySession(
        orderedTasks = tasks(count),
        subtasksByParentId = emptyMap(),
        queryWindow = TemporalQueryWindow(
            status = TemporalResolutionStatus.RESOLVED,
            dateScope = if (exactDate) TemporalDateScope.EXACT_DATE else TemporalDateScope.DATE_RANGE,
            startDateInclusive = "20/07/2026",
            endDateInclusive = if (exactDate) "20/07/2026" else "26/07/2026",
            spokenLabel = if (exactDate) "tomorrow" else "this week"
        ),
        presentation = presentation
    )

    private fun tasks(count: Int) = (1..count).map { number ->
        TaskEntity(
            id = number.toLong(),
            title = "Task $number",
            dueDate = "20/07/2026",
            dueTime = "08:00"
        )
    }
}
