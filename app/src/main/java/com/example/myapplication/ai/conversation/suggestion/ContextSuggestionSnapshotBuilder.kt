package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ContextSuggestionSnapshotBuilder {
    const val MAX_CANDIDATES = 8
    const val MAX_CLOSE_PAIRS = 5
    const val UPCOMING_WINDOW_DAYS = 7
    const val MAX_TITLE_LENGTH = 120

    fun build(
        now: Calendar,
        rootTasks: List<TaskEntity>,
        subtasksByParentId: Map<Long, List<TaskEntity>>
    ): ContextSuggestionSnapshot {
        val allSeeds = rootTasks.asSequence()
            .filter { it.parentTaskId == null && !it.isDone }
            .mapNotNull { task ->
                val schedule = validatedSchedule(task, now) ?: return@mapNotNull null
                CandidateSeed(
                    task = task,
                    subtasks = subtasksByParentId[task.id]
                        .orEmpty()
                        .sortedWith(compareBy<TaskEntity> { it.subtaskOrder }.thenBy { it.id }),
                    schedule = schedule
                )
            }
            .distinctBy { it.task.id }
            .sortedWith(candidateComparator)
            .toList()
        val pairAnalysis = analyzeClosePairs(allSeeds, now)
        val reservedPairSeeds = pairAnalysis.actionablePairs.firstOrNull()?.let { pair ->
            listOf(pair.first, pair.second)
        }.orEmpty()
        val reservedIds = reservedPairSeeds.mapTo(mutableSetOf()) { it.task.id }
        val generalSeeds = allSeeds.asSequence()
            .filterNot { it.task.id in reservedIds }
            .take(MAX_CANDIDATES - reservedPairSeeds.size)
            .toList()
        val selectedSeeds = (reservedPairSeeds + generalSeeds)
            .sortedWith(candidateComparator)

        val orderedCandidates = selectedSeeds
            .mapIndexed { index, seed ->
                val subtasks = seed.subtasks.toList()
                ContextSuggestionCandidate(
                    ref = "S${index + 1}",
                    title = sanitizeTitle(seed.task.title),
                    dueDate = seed.schedule.dueDate,
                    dueTime = seed.schedule.dueTime,
                    attentionCategory = seed.schedule.category,
                    subtaskCount = subtasks.size,
                    unfinishedSubtaskCount = subtasks.count { !it.isDone },
                    structurallyEligibleForBreakdown = subtasks.isEmpty(),
                    capturedTask = seed.task,
                    capturedSubtasks = subtasks
                )
            }
            .toList()
        val refByTaskId = orderedCandidates.associate { candidate ->
            candidate.taskId to candidate.ref
        }

        return ContextSuggestionSnapshot(
            candidates = orderedCandidates,
            closePairs = pairAnalysis.actionablePairs.mapNotNull { pair ->
                val primaryRef = refByTaskId[pair.first.task.id] ?: return@mapNotNull null
                val secondaryRef = refByTaskId[pair.second.task.id] ?: return@mapNotNull null
                ContextSuggestionClosePair(
                    primaryRef = primaryRef,
                    secondaryRef = secondaryRef,
                    gapMinutes = pair.gapMinutes,
                    dueDate = pair.first.schedule.dueDate,
                    dueDateSortMillis = pair.first.schedule.dateSortMillis,
                    firstTaskMinute = requireNotNull(pair.first.schedule.minuteOfDay)
                )
            }.take(MAX_CLOSE_PAIRS),
            capturedAtMillis = now.timeInMillis,
            actionableClosePairCount = pairAnalysis.actionablePairs.size,
            reservedClosePairCandidateCount = reservedPairSeeds.size,
            excludedPastClosePairCount = pairAnalysis.excludedPastPairCount
        )
    }

    internal fun sanitizeTitle(title: String): String =
        sanitizeField(title).take(MAX_TITLE_LENGTH).trim()

    internal fun attentionFor(
        task: TaskEntity,
        now: Calendar
    ): ContextSuggestionAttentionCategory? = validatedSchedule(task, now)?.category

    internal fun exactGapMinutes(
        first: TaskEntity,
        second: TaskEntity,
        now: Calendar
    ): Int? {
        val firstSchedule = validatedSchedule(first, now) ?: return null
        val secondSchedule = validatedSchedule(second, now) ?: return null
        if (
            firstSchedule.dateSortMillis != secondSchedule.dateSortMillis ||
            firstSchedule.minuteOfDay == null ||
            secondSchedule.minuteOfDay == null ||
            firstSchedule.exactDueMillis == null ||
            secondSchedule.exactDueMillis == null ||
            firstSchedule.exactDueMillis < now.timeInMillis ||
            secondSchedule.exactDueMillis < now.timeInMillis
        ) {
            return null
        }
        return kotlin.math.abs(firstSchedule.minuteOfDay - secondSchedule.minuteOfDay)
    }

    internal fun parseTimeMinute(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        TIME_PATTERNS.forEach { pattern ->
            try {
                val parsed = SimpleDateFormat(pattern, Locale.UK).apply {
                    isLenient = false
                }.parse(value.trim().uppercase(Locale.UK)) ?: return@forEach
                val calendar = Calendar.getInstance(Locale.UK).apply { time = parsed }
                return calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                    calendar.get(Calendar.MINUTE)
            } catch (_: Exception) {
                // Try the next supported exact-time format.
            }
        }
        return null
    }

    private fun analyzeClosePairs(
        seeds: List<CandidateSeed>,
        now: Calendar
    ): ClosePairAnalysis {
        val actionablePairs = mutableListOf<ClosePairSeed>()
        var excludedPastPairCount = 0
        for (firstIndex in seeds.indices) {
            val first = seeds[firstIndex]
            val firstMinute = first.schedule.minuteOfDay ?: continue
            val firstDueMillis = first.schedule.exactDueMillis ?: continue
            for (secondIndex in firstIndex + 1 until seeds.size) {
                val second = seeds[secondIndex]
                val secondMinute = second.schedule.minuteOfDay ?: continue
                val secondDueMillis = second.schedule.exactDueMillis ?: continue
                if (first.schedule.dateSortMillis != second.schedule.dateSortMillis) {
                    continue
                }
                val gap = kotlin.math.abs(firstMinute - secondMinute)
                if (gap > MAX_CLOSE_GAP_MINUTES) continue
                val ordered = if (
                    firstDueMillis < secondDueMillis ||
                    (firstDueMillis == secondDueMillis && first.task.id < second.task.id)
                ) {
                    first to second
                } else {
                    second to first
                }
                if (firstDueMillis < now.timeInMillis || secondDueMillis < now.timeInMillis) {
                    excludedPastPairCount += 1
                } else {
                    actionablePairs += ClosePairSeed(
                        first = ordered.first,
                        second = ordered.second,
                        gapMinutes = gap
                    )
                }
            }
        }
        return ClosePairAnalysis(
            actionablePairs = actionablePairs.sortedWith(
                compareBy<ClosePairSeed> { requireNotNull(it.first.schedule.exactDueMillis) }
                    .thenBy { requireNotNull(it.second.schedule.exactDueMillis) }
                    .thenBy { it.gapMinutes }
                    .thenBy { it.first.task.id }
                    .thenBy { it.second.task.id }
            ),
            excludedPastPairCount = excludedPastPairCount
        )
    }

    private fun validatedSchedule(
        task: TaskEntity,
        now: Calendar
    ): ValidatedSchedule? {
        val dueDate = sanitizeField(task.dueDate.orEmpty())
        val dueTime = sanitizeField(task.dueTime.orEmpty())
        if (dueDate.isBlank() && dueTime.isBlank()) {
            return ValidatedSchedule(
                dueDate = "",
                dueTime = "",
                category = ContextSuggestionAttentionCategory.UNSCHEDULED,
                dateSortMillis = Long.MAX_VALUE,
                exactDueMillis = null,
                minuteOfDay = null
            )
        }
        if (dueDate.isBlank()) return null

        val dateCalendar = parseDate(dueDate, now) ?: return null
        val minute = if (dueTime.isBlank()) null else parseTimeMinute(dueTime) ?: return null
        val today = startOfDay(now)
        val dateStart = startOfDay(dateCalendar)
        if (dateStart < today && minute == null) {
            return null
        }
        val exactDueMillis = minute?.let {
            (dateCalendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, it / 60)
                set(Calendar.MINUTE, it % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val upcomingBoundary = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, UPCOMING_WINDOW_DAYS)
        }.timeInMillis
        val category = when {
            exactDueMillis != null && exactDueMillis < now.timeInMillis ->
                ContextSuggestionAttentionCategory.OVERDUE
            dateStart == today -> ContextSuggestionAttentionCategory.DUE_TODAY
            dateStart > today && dateStart <= upcomingBoundary ->
                ContextSuggestionAttentionCategory.UPCOMING
            dateStart > upcomingBoundary -> ContextSuggestionAttentionCategory.LATER
            else -> return null
        }
        return ValidatedSchedule(
            dueDate = dueDate,
            dueTime = dueTime,
            category = category,
            dateSortMillis = dateStart,
            exactDueMillis = exactDueMillis,
            minuteOfDay = minute
        )
    }

    private fun parseDate(value: String, now: Calendar): Calendar? = try {
        val parsed = SimpleDateFormat(DATE_PATTERN, Locale.UK).apply {
            isLenient = false
            timeZone = now.timeZone
        }.parse(value) ?: return null
        Calendar.getInstance(now.timeZone, Locale.UK).apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    } catch (_: Exception) {
        null
    }

    private fun startOfDay(calendar: Calendar): Long =
        (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun sanitizeField(value: String): String {
        val flattened = buildString(value.length) {
            value.forEach { character ->
                when {
                    character.isISOControl() || character.isWhitespace() -> append(' ')
                    else -> append(character)
                }
            }
        }
        return flattened.replace(WHITESPACE, " ").trim()
    }

    private data class CandidateSeed(
        val task: TaskEntity,
        val subtasks: List<TaskEntity>,
        val schedule: ValidatedSchedule
    )

    private data class ClosePairSeed(
        val first: CandidateSeed,
        val second: CandidateSeed,
        val gapMinutes: Int
    )

    private data class ClosePairAnalysis(
        val actionablePairs: List<ClosePairSeed>,
        val excludedPastPairCount: Int
    )

    private data class ValidatedSchedule(
        val dueDate: String,
        val dueTime: String,
        val category: ContextSuggestionAttentionCategory,
        val dateSortMillis: Long,
        val exactDueMillis: Long?,
        val minuteOfDay: Int?
    )

    private val candidateComparator =
        compareBy<CandidateSeed> { it.schedule.category.ordinal }
            .thenBy {
                when (it.schedule.category) {
                    ContextSuggestionAttentionCategory.OVERDUE ->
                        it.schedule.exactDueMillis ?: Long.MAX_VALUE
                    ContextSuggestionAttentionCategory.DUE_TODAY ->
                        it.schedule.minuteOfDay?.toLong() ?: Long.MAX_VALUE
                    ContextSuggestionAttentionCategory.UPCOMING,
                    ContextSuggestionAttentionCategory.LATER ->
                        it.schedule.dateSortMillis
                    ContextSuggestionAttentionCategory.UNSCHEDULED -> Long.MAX_VALUE
                }
            }
            .thenBy {
                when (it.schedule.category) {
                    ContextSuggestionAttentionCategory.UPCOMING,
                    ContextSuggestionAttentionCategory.LATER ->
                        it.schedule.minuteOfDay ?: Int.MAX_VALUE
                    else -> 0
                }
            }
            .thenBy { it.task.title.lowercase(Locale.UK) }
            .thenBy { it.task.id }

    private const val DATE_PATTERN = "dd/MM/yyyy"
    private const val MAX_CLOSE_GAP_MINUTES = 30
    private val TIME_PATTERNS = listOf("h:mm a", "h a", "HH:mm")
    private val WHITESPACE = Regex("\\s+")
}
