package com.example.myapplication.reminder

import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.CancellationException

fun interface ReminderBootstrapTaskSource {
    suspend fun loadActiveRootTasks(): List<TaskEntity>
}

fun interface ReminderBootstrapScheduler {
    fun schedule(task: TaskEntity): Boolean
}

fun interface LegacyReminderCanceller {
    fun cancel(taskId: Long)
}

interface ReminderBootstrapVersionStore {
    fun currentVersion(): Int
    fun recordVersion(version: Int): Boolean
}

fun interface ReminderBootstrapLogger {
    fun log(result: ReminderBootstrapResult)
}

enum class ReminderBootstrapOutcome {
    ALREADY_COMPLETE,
    PERMISSION_UNAVAILABLE,
    VERSION_READ_FAILED,
    TASK_LOAD_FAILED,
    SCHEDULING_FAILED,
    VERSION_RECORD_FAILED,
    COMPLETE
}

data class ReminderBootstrapResult(
    val eligibleCount: Int,
    val scheduledCount: Int,
    val rejectedCount: Int,
    val outcome: ReminderBootstrapOutcome
)

class ReminderEscalationBootstrapper(
    private val taskSource: ReminderBootstrapTaskSource,
    private val scheduler: ReminderBootstrapScheduler,
    private val legacyCanceller: LegacyReminderCanceller,
    private val versionStore: ReminderBootstrapVersionStore,
    private val exactAlarmPermissionReady: () -> Boolean,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val logger: ReminderBootstrapLogger = ReminderBootstrapLogger {}
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }

    suspend fun runIfNeeded(): ReminderBootstrapResult {
        val installedVersion = try {
            versionStore.currentVersion()
        } catch (_: Exception) {
            return result(ReminderBootstrapOutcome.VERSION_READ_FAILED)
        }
        if (installedVersion >= SCHEMA_VERSION) {
            return result(ReminderBootstrapOutcome.ALREADY_COMPLETE)
        }
        val permissionReady = try {
            exactAlarmPermissionReady()
        } catch (_: RuntimeException) {
            false
        }
        if (!permissionReady) {
            return result(ReminderBootstrapOutcome.PERMISSION_UNAVAILABLE)
        }

        val tasks = try {
            taskSource.loadActiveRootTasks()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return result(ReminderBootstrapOutcome.TASK_LOAD_FAILED)
        }
        val now = nowEpochMillis()
        val eligibleTasks = tasks.filter {
            ReminderEligibilityPolicy.evaluateForScheduling(
                task = it,
                nowEpochMillis = now
            ) is ReminderSchedulingEligibility.Eligible
        }
        var scheduledCount = 0
        eligibleTasks.forEach { task ->
            try {
                legacyCanceller.cancel(task.id)
            } catch (_: Exception) {
                // The legacy receiver fails closed, so still install the usable sequence.
            }
            val scheduled = try {
                scheduler.schedule(task)
            } catch (_: Exception) {
                false
            }
            if (scheduled) scheduledCount += 1
        }

        if (scheduledCount != eligibleTasks.size) {
            return result(
                outcome = ReminderBootstrapOutcome.SCHEDULING_FAILED,
                eligibleCount = eligibleTasks.size,
                scheduledCount = scheduledCount,
                rejectedCount = tasks.size - eligibleTasks.size
            )
        }
        val versionRecorded = try {
            versionStore.recordVersion(SCHEMA_VERSION)
        } catch (_: Exception) {
            false
        }
        if (!versionRecorded) {
            return result(
                outcome = ReminderBootstrapOutcome.VERSION_RECORD_FAILED,
                eligibleCount = eligibleTasks.size,
                scheduledCount = scheduledCount,
                rejectedCount = tasks.size - eligibleTasks.size
            )
        }
        return result(
            outcome = ReminderBootstrapOutcome.COMPLETE,
            eligibleCount = eligibleTasks.size,
            scheduledCount = scheduledCount,
            rejectedCount = tasks.size - eligibleTasks.size
        )
    }

    private fun result(
        outcome: ReminderBootstrapOutcome,
        eligibleCount: Int = 0,
        scheduledCount: Int = 0,
        rejectedCount: Int = 0
    ): ReminderBootstrapResult = ReminderBootstrapResult(
        eligibleCount = eligibleCount,
        scheduledCount = scheduledCount,
        rejectedCount = rejectedCount,
        outcome = outcome
    ).also(logger::log)
}
