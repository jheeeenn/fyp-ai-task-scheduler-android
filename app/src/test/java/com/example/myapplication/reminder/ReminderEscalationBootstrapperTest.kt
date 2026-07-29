package com.example.myapplication.reminder

import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEscalationBootstrapperTest {
    private val futureDate = "30/07/2030"
    private val futureTime = "3:00 PM"
    private val futureEpoch = requireNotNull(
        ReminderScheduleParser.parseToEpochMillis(futureDate, futureTime)
    )

    @Test
    fun bootstrapSelectsOnlyActiveEligibleRootsAndRecordsVersion() = runBlocking {
        val tasks = listOf(
            task(1L),
            task(2L, isDone = true),
            task(3L, parentTaskId = 1L),
            task(4L, dueDate = "30/07/2020"),
            task(5L, dueDate = "31/02/2030"),
            task(6L, dueTime = null)
        )
        val scheduledIds = mutableListOf<Long>()
        val legacyCancellationIds = mutableListOf<Long>()
        val versions = FakeVersionStore()
        val bootstrapper = bootstrapper(
            tasks = tasks,
            versionStore = versions,
            scheduler = {
                scheduledIds += it.id
                true
            },
            legacyCanceller = { legacyCancellationIds += it }
        )

        val result = bootstrapper.runIfNeeded()

        assertEquals(ReminderBootstrapOutcome.COMPLETE, result.outcome)
        assertEquals(1, result.eligibleCount)
        assertEquals(1, result.scheduledCount)
        assertEquals(5, result.rejectedCount)
        assertEquals(listOf(1L), scheduledIds)
        assertEquals(listOf(1L), legacyCancellationIds)
        assertEquals(
            ReminderEscalationBootstrapper.SCHEMA_VERSION,
            versions.version
        )
    }

    @Test
    fun failedSchedulingLeavesBootstrapPendingForRetry() = runBlocking {
        val versions = FakeVersionStore()
        val cancellationIds = mutableListOf<Long>()
        val result = bootstrapper(
            tasks = listOf(task(1L), task(2L)),
            versionStore = versions,
            scheduler = { it.id == 1L },
            legacyCanceller = { cancellationIds += it }
        ).runIfNeeded()

        assertEquals(ReminderBootstrapOutcome.SCHEDULING_FAILED, result.outcome)
        assertEquals(2, result.eligibleCount)
        assertEquals(1, result.scheduledCount)
        assertEquals(0, result.rejectedCount)
        assertEquals(listOf(1L, 2L), cancellationIds)
        assertEquals(0, versions.version)
        assertFalse(versions.recordCalled)
    }

    @Test
    fun unavailableExactAlarmPermissionLeavesBootstrapPending() = runBlocking {
        var sourceCalled = false
        val versions = FakeVersionStore()
        val bootstrapper = ReminderEscalationBootstrapper(
            taskSource = ReminderBootstrapTaskSource {
                sourceCalled = true
                listOf(task(1L))
            },
            scheduler = ReminderBootstrapScheduler { true },
            legacyCanceller = LegacyReminderCanceller {},
            versionStore = versions,
            exactAlarmPermissionReady = { false },
            nowEpochMillis = { futureEpoch - 1 }
        )

        val result = bootstrapper.runIfNeeded()

        assertEquals(ReminderBootstrapOutcome.PERMISSION_UNAVAILABLE, result.outcome)
        assertFalse(sourceCalled)
        assertEquals(0, versions.version)
        assertFalse(versions.recordCalled)
    }

    @Test
    fun completedSchemaDoesNotQueryOrRescheduleTasks() = runBlocking {
        var sourceCalled = false
        var schedulerCalled = false
        val versions = FakeVersionStore(
            version = ReminderEscalationBootstrapper.SCHEMA_VERSION
        )
        val bootstrapper = ReminderEscalationBootstrapper(
            taskSource = ReminderBootstrapTaskSource {
                sourceCalled = true
                emptyList()
            },
            scheduler = ReminderBootstrapScheduler {
                schedulerCalled = true
                true
            },
            legacyCanceller = LegacyReminderCanceller {},
            versionStore = versions,
            exactAlarmPermissionReady = { true }
        )

        val result = bootstrapper.runIfNeeded()

        assertEquals(ReminderBootstrapOutcome.ALREADY_COMPLETE, result.outcome)
        assertFalse(sourceCalled)
        assertFalse(schedulerCalled)
        assertTrue(versions.version >= ReminderEscalationBootstrapper.SCHEMA_VERSION)
    }

    private fun bootstrapper(
        tasks: List<TaskEntity>,
        versionStore: FakeVersionStore,
        scheduler: (TaskEntity) -> Boolean,
        legacyCanceller: (Long) -> Unit
    ) = ReminderEscalationBootstrapper(
        taskSource = ReminderBootstrapTaskSource { tasks },
        scheduler = ReminderBootstrapScheduler(scheduler),
        legacyCanceller = LegacyReminderCanceller(legacyCanceller),
        versionStore = versionStore,
        exactAlarmPermissionReady = { true },
        nowEpochMillis = { futureEpoch - 1 }
    )

    private fun task(
        id: Long,
        dueDate: String? = futureDate,
        dueTime: String? = futureTime,
        isDone: Boolean = false,
        parentTaskId: Long? = null
    ) = TaskEntity(
        id = id,
        title = "Task $id",
        dueDate = dueDate,
        dueTime = dueTime,
        isDone = isDone,
        parentTaskId = parentTaskId
    )

    private class FakeVersionStore(
        var version: Int = 0
    ) : ReminderBootstrapVersionStore {
        var recordCalled = false

        override fun currentVersion(): Int = version

        override fun recordVersion(version: Int): Boolean {
            recordCalled = true
            this.version = version
            return true
        }
    }
}
