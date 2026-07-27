package com.example.myapplication.ai.routine

import com.example.myapplication.ScheduleTextParser
import com.example.myapplication.ai.temporal.TemporalActionPolicy
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalPolicyResult
import com.example.myapplication.ai.temporal.TemporalResolution
import com.example.myapplication.ai.temporal.TemporalUseCase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class RoutineDraftState {
    NONE,
    EXTRACTING,
    COLLECTING_SHARED_DATE,
    COLLECTING_STEP_TIME,
    WAITING_FOR_CONFIRMATION,
    SAVING
}

data class PendingRoutineDraft(
    val title: String,
    val steps: List<PendingRoutineStep>,
    val revision: Long
)

data class PendingRoutineStep(
    val title: String,
    val originalDateText: String?,
    val originalTimeText: String?,
    val resolvedDate: String?,
    val resolvedTime: String?
)

sealed class RoutineDraftUpdate {
    data class Ask(val prompt: String, val draft: PendingRoutineDraft) : RoutineDraftUpdate()
    data class Review(
        val proposal: String,
        val draft: PendingRoutineDraft
    ) : RoutineDraftUpdate()
    data class Rejected(val reason: RoutineDraftIssue) : RoutineDraftUpdate()
    data object Stale : RoutineDraftUpdate()
}

enum class RoutineDraftIssue {
    LOW_CONFIDENCE,
    EXTRACTION_NEEDS_CLARIFICATION,
    INVALID_STEP_COUNT,
    EMPTY_STEP_TITLE,
    INVALID_DATE,
    INVALID_TIME,
    PAST_SCHEDULE,
    INVALID_STATE
}

class RoutineDraftController(
    private val resolver: TemporalExpressionResolver = TemporalExpressionResolver(),
    private val baseCalendarProvider: () -> Calendar = { Calendar.getInstance() }
) {
    var state: RoutineDraftState = RoutineDraftState.NONE
        private set
    var draft: PendingRoutineDraft? = null
        private set
    var authoritativeProposal: String? = null
        private set

    private var extractionGeneration: Long = 0

    fun beginExtraction(): Long {
        extractionGeneration += 1
        draft = null
        authoritativeProposal = null
        state = RoutineDraftState.EXTRACTING
        return extractionGeneration
    }

    fun applyExtraction(
        generation: Long,
        extraction: RoutineExtractionResponse
    ): RoutineDraftUpdate {
        if (generation != extractionGeneration || state != RoutineDraftState.EXTRACTING) {
            return RoutineDraftUpdate.Stale
        }
        if (!extraction.confidence.isFinite() ||
            extraction.confidence < MIN_EXTRACTION_CONFIDENCE
        ) {
            clear()
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.LOW_CONFIDENCE)
        }
        if (extraction.needClarification) {
            clear()
            return RoutineDraftUpdate.Rejected(
                RoutineDraftIssue.EXTRACTION_NEEDS_CLARIFICATION
            )
        }
        if (extraction.steps.size !in MIN_STEPS..MAX_STEPS) {
            clear()
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STEP_COUNT)
        }
        if (extraction.steps.any { it.title.trim().isEmpty() }) {
            clear()
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.EMPTY_STEP_TITLE)
        }

        val base = baseCalendarProvider()
        val parsedSteps = extraction.steps.map { step ->
            val date = resolveDate(step.dateText, base)
            val time = resolveTime(step.timeText, base)
            PendingRoutineStep(
                title = step.title.trim(),
                originalDateText = step.dateText.trim().takeIf(String::isNotEmpty),
                originalTimeText = step.timeText.trim().takeIf(String::isNotEmpty),
                resolvedDate = date,
                resolvedTime = time
            )
        }
        val suppliedDates = parsedSteps.mapNotNull(PendingRoutineStep::resolvedDate).distinct()
        val stepsWithSharedDate = if (suppliedDates.size == 1) {
            parsedSteps.map { it.copy(resolvedDate = it.resolvedDate ?: suppliedDates.single()) }
        } else {
            parsedSteps
        }
        draft = PendingRoutineDraft(
            title = extraction.routineTitle.trim().ifEmpty { DEFAULT_ROUTINE_TITLE },
            steps = stepsWithSharedDate,
            revision = 1L
        )
        return advance()
    }

    fun provideSharedDate(dateText: String): RoutineDraftUpdate {
        if (state != RoutineDraftState.COLLECTING_SHARED_DATE) {
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STATE)
        }
        val current = draft
            ?: return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STATE)
        val resolved = resolveDate(dateText, baseCalendarProvider())
            ?: return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_DATE)
        draft = current.copy(
            steps = current.steps.map { step ->
                if (step.resolvedDate == null) {
                    step.copy(
                        originalDateText = dateText.trim(),
                        resolvedDate = resolved
                    )
                } else {
                    step
                }
            },
            revision = current.revision + 1
        )
        return advance()
    }

    fun provideNextStepTime(timeText: String): RoutineDraftUpdate {
        if (state != RoutineDraftState.COLLECTING_STEP_TIME) {
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STATE)
        }
        val current = draft
            ?: return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STATE)
        val index = current.steps.indexOfFirst { it.resolvedTime == null }
        if (index < 0) return advance()
        val resolution = resolveTimeResolution(timeText, baseCalendarProvider())
        val minute = resolution?.startMinuteInclusive
        if (resolution == null || !resolution.isExactTime || minute == null) {
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_TIME)
        }
        val originalConstraint = current.steps[index].originalTimeText
            ?.takeIf(String::isNotBlank)
            ?.let { resolveTimeResolution(it, baseCalendarProvider()) }
        if (originalConstraint != null &&
            originalConstraint.hasTimeConstraint &&
            !TemporalActionPolicy.validateClarification(
                original = originalConstraint,
                exactDate = null,
                exactMinute = minute
            )
        ) {
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_TIME)
        }
        val updated = current.steps.toMutableList()
        updated[index] = updated[index].copy(
            originalTimeText = timeText.trim(),
            resolvedTime = formatMinute(minute)
        )
        draft = current.copy(steps = updated, revision = current.revision + 1)
        return advance()
    }

    fun changeStepTime(stepIndex: Int, timeText: String): RoutineDraftUpdate {
        val current = requireReviewDraft()
            ?: return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STATE)
        if (stepIndex !in current.steps.indices) {
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_TIME)
        }
        val resolution = resolveTimeResolution(timeText, baseCalendarProvider())
        val minute = resolution?.startMinuteInclusive
        if (resolution == null || !resolution.isExactTime || minute == null) {
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_TIME)
        }
        val steps = current.steps.toMutableList()
        steps[stepIndex] = steps[stepIndex].copy(
            originalTimeText = timeText.trim(),
            resolvedTime = formatMinute(minute)
        )
        draft = current.copy(steps = steps, revision = current.revision + 1)
        return advance()
    }

    fun changeStepTitle(stepIndex: Int, title: String): RoutineDraftUpdate {
        val current = requireReviewDraft()
            ?: return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STATE)
        val replacement = title.trim()
        if (stepIndex !in current.steps.indices || replacement.isEmpty()) {
            return RoutineDraftUpdate.Rejected(RoutineDraftIssue.EMPTY_STEP_TITLE)
        }
        val steps = current.steps.toMutableList()
        steps[stepIndex] = steps[stepIndex].copy(title = replacement)
        draft = current.copy(steps = steps, revision = current.revision + 1)
        return advance()
    }

    fun changeSharedDate(dateText: String): RoutineDraftUpdate {
        val current = requireReviewDraft()
            ?: return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STATE)
        val resolved = resolveDate(dateText, baseCalendarProvider())
            ?: return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_DATE)
        draft = current.copy(
            steps = current.steps.map {
                it.copy(originalDateText = dateText.trim(), resolvedDate = resolved)
            },
            revision = current.revision + 1
        )
        return advance()
    }

    fun markSaving(): PendingRoutineDraft? {
        val current = requireReviewDraft() ?: return null
        state = RoutineDraftState.SAVING
        return current
    }

    fun clear() {
        extractionGeneration += 1
        state = RoutineDraftState.NONE
        draft = null
        authoritativeProposal = null
    }

    private fun requireReviewDraft(): PendingRoutineDraft? =
        draft.takeIf { state == RoutineDraftState.WAITING_FOR_CONFIRMATION }

    private fun advance(): RoutineDraftUpdate {
        val current = draft
            ?: return RoutineDraftUpdate.Rejected(RoutineDraftIssue.INVALID_STATE)
        if (current.steps.any { it.resolvedDate == null }) {
            state = RoutineDraftState.COLLECTING_SHARED_DATE
            authoritativeProposal = null
            return RoutineDraftUpdate.Ask(DATE_QUESTION, current)
        }
        val missingTimeIndex = current.steps.indexOfFirst { it.resolvedTime == null }
        if (missingTimeIndex >= 0) {
            state = RoutineDraftState.COLLECTING_STEP_TIME
            authoritativeProposal = null
            val step = current.steps[missingTimeIndex]
            return RoutineDraftUpdate.Ask(
                "What time should I use for the ${ordinal(missingTimeIndex)} step, ${step.title}?",
                current
            )
        }
        if (current.steps.any { isPast(it) }) {
            val replacement = current.copy(
                steps = current.steps.map { it.copy(resolvedDate = null) },
                revision = current.revision + 1
            )
            draft = replacement
            state = RoutineDraftState.COLLECTING_SHARED_DATE
            authoritativeProposal = null
            return RoutineDraftUpdate.Ask(PAST_DATE_QUESTION, replacement)
        }
        val proposal = RoutineProposalRenderer.render(current)
        authoritativeProposal = proposal
        state = RoutineDraftState.WAITING_FOR_CONFIRMATION
        return RoutineDraftUpdate.Review(proposal, current)
    }

    private fun isPast(step: PendingRoutineStep): Boolean {
        val date = step.resolvedDate ?: return false
        val time = step.resolvedTime ?: return false
        val base = baseCalendarProvider()
        val resolution = resolver.resolve(date, time, "$date $time", base)
        val rejectedByPolicy = TemporalActionPolicy.evaluate(
            resolution,
            TemporalUseCase.CREATE,
            base
        ) is TemporalPolicyResult.InvalidPastSchedule
        val formatter = SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.UK).apply {
            isLenient = false
            timeZone = base.timeZone
        }
        val scheduledAt = try {
            formatter.parse("$date $time")?.time
        } catch (_: Exception) {
            null
        }
        return rejectedByPolicy || scheduledAt == null || scheduledAt <= base.timeInMillis
    }

    private fun resolveDate(text: String, base: Calendar): String? {
        if (text.isBlank()) return null
        val resolution = resolver.resolve(text, null, text, base)
        return resolution.takeIf { it.isExactDate }?.startDateInclusive
    }

    private fun resolveTime(text: String, base: Calendar): String? {
        val resolution = resolveTimeResolution(text, base) ?: return null
        val minute = resolution.startMinuteInclusive
        return minute?.takeIf { resolution.isExactTime }?.let(::formatMinute)
    }

    private fun resolveTimeResolution(text: String, base: Calendar): TemporalResolution? {
        if (text.isBlank()) return null
        return resolver.resolve(null, text, text, base)
    }

    private fun formatMinute(minute: Int): String =
        ScheduleTextParser.formatTime(minute / 60, minute % 60)

    private fun ordinal(index: Int): String =
        listOf("first", "second", "third", "fourth", "fifth").getOrElse(index) {
            "${index + 1}th"
        }

    private companion object {
        const val MIN_STEPS = 2
        const val MAX_STEPS = 5
        const val MIN_EXTRACTION_CONFIDENCE = 0.80
        const val DEFAULT_ROUTINE_TITLE = "My routine"
        const val DATE_QUESTION = "What date should I use for this routine?"
        const val PAST_DATE_QUESTION =
            "That schedule is in the past. What future date should I use for this routine?"
    }
}

object RoutineProposalRenderer {
    private val inputDate = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply {
        isLenient = false
    }

    fun render(draft: PendingRoutineDraft): String {
        require(draft.steps.size in 2..5)
        require(draft.steps.all {
            it.title.isNotBlank() &&
                !it.resolvedDate.isNullOrBlank() &&
                !it.resolvedTime.isNullOrBlank()
        })
        val distinctDates = draft.steps.mapNotNull(PendingRoutineStep::resolvedDate).distinct()
        val introduction = if (distinctDates.size == 1) {
            "I prepared your ${draft.title} for ${speakDate(distinctDates.single())}."
        } else {
            "I prepared your ${draft.title}."
        }
        val steps = draft.steps.mapIndexed { index, step ->
            val datePhrase = if (distinctDates.size == 1) {
                ""
            } else {
                " on ${speakDate(requireNotNull(step.resolvedDate))}"
            }
            "${ordinal(index)}, ${step.title}$datePhrase at ${speakTime(requireNotNull(step.resolvedTime))}."
        }.joinToString(" ")
        val count = draft.steps.size
        val noun = if (count == 1) "task" else "tasks"
        return "$introduction $steps Would you like me to create these $count $noun?"
    }

    private fun speakDate(value: String): String {
        val parsed = inputDate.parse(value) ?: return value
        val calendar = Calendar.getInstance().apply { time = parsed }
        val pattern = if (calendar.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
            "EEEE, d MMMM"
        } else {
            "EEEE, d MMMM yyyy"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(parsed)
    }

    private fun speakTime(value: String): String =
        value.replace(":00 ", " ")

    private fun ordinal(index: Int): String =
        listOf("First", "Second", "Third", "Fourth", "Fifth")[index]
}
