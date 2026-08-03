package com.example.myapplication

import com.example.myapplication.ai.temporal.TemporalActionPolicy
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalPolicyResult
import com.example.myapplication.ai.temporal.TemporalUseCase
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftMoveInterpreter
import com.example.myapplication.voice.CreateTaskDialogState
import com.example.myapplication.voice.TextNormalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class EditableTaskDraft(
    val title: String,
    val dueDate: String?,
    val dueTime: String?,
    val revision: Long
)

data class TaskDetailAuthoritativeBase(
    val taskId: Long,
    val title: String,
    val dueDate: String?,
    val dueTime: String?,
    val isDone: Boolean
)

data class TaskDetailSaveClaim(
    val base: TaskDetailAuthoritativeBase,
    val draft: EditableTaskDraft
)

class TaskDetailDraftController private constructor(
    base: TaskDetailAuthoritativeBase,
    draft: EditableTaskDraft
) {
    var base: TaskDetailAuthoritativeBase = base
        private set
    var draft: EditableTaskDraft = draft
        private set

    val isDirty: Boolean
        get() = draft.title != base.title ||
            draft.dueDate != base.dueDate ||
            draft.dueTime != base.dueTime

    fun changeTitle(title: String): Boolean = replaceDraft(title = title.trim())

    fun changeDate(dueDate: String?): Boolean = replaceDraft(dueDate = dueDate.clean())

    fun changeTime(dueTime: String?): Boolean = replaceDraft(dueTime = dueTime.clean())

    fun changeSchedule(dueDate: String?, dueTime: String?): Boolean = replaceDraft(
        dueDate = dueDate.clean(),
        dueTime = dueTime.clean()
    )

    fun updateAuthoritativeCompletion(isDone: Boolean) {
        base = base.copy(isDone = isDone)
    }

    fun freezeSaveClaim(): TaskDetailSaveClaim? =
        draft.takeIf { isDirty }?.let { TaskDetailSaveClaim(base, it) }

    fun isCurrent(claim: TaskDetailSaveClaim): Boolean =
        claim.base == base && claim.draft.revision == draft.revision && claim.draft == draft

    fun replaceFromRoom(task: TaskEntity) {
        val nextRevision = draft.revision + 1L
        base = task.toTaskDetailBase()
        draft = EditableTaskDraft(task.title, task.dueDate, task.dueTime, nextRevision)
    }

    fun discard() {
        draft = EditableTaskDraft(
            title = base.title,
            dueDate = base.dueDate,
            dueTime = base.dueTime,
            revision = draft.revision + 1L
        )
    }

    private fun replaceDraft(
        title: String = draft.title,
        dueDate: String? = draft.dueDate,
        dueTime: String? = draft.dueTime
    ): Boolean {
        if (title == draft.title && dueDate == draft.dueDate && dueTime == draft.dueTime) {
            return false
        }
        draft = EditableTaskDraft(title, dueDate, dueTime, draft.revision + 1L)
        return true
    }

    companion object {
        fun from(task: TaskEntity): TaskDetailDraftController {
            val base = task.toTaskDetailBase()
            return TaskDetailDraftController(
                base,
                EditableTaskDraft(base.title, base.dueDate, base.dueTime, revision = 0L)
            )
        }
    }
}

enum class TaskDetailEditInteraction {
    IDLE,
    WAITING_FOR_TITLE,
    WAITING_FOR_DATE,
    WAITING_FOR_TIME,
    WAITING_FOR_SAVE_CONFIRMATION,
    WAITING_FOR_HOME_CONFIRMATION,
    WAITING_FOR_BACK_CONFIRMATION,
    WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION,
    WAITING_FOR_DELETE_DISCARD_CONFIRMATION,
    SAVING
}

enum class TaskDetailConfirmation { YES, NO, CANCEL, UNCLEAR }

object TaskDetailConfirmationInterpreter {
    fun interpret(value: String): TaskDetailConfirmation = when (TextNormalizer.normalize(value)) {
        "yes", "yes yes", "yeah", "yep", "sure", "okay", "ok", "confirm", "save" ->
            TaskDetailConfirmation.YES
        "no", "no no", "nope", "do not save", "don't save", "not now" ->
            TaskDetailConfirmation.NO
        "cancel", "stop", "never mind", "nevermind", "go back" ->
            TaskDetailConfirmation.CANCEL
        else -> TaskDetailConfirmation.UNCLEAR
    }
}

sealed class TaskFieldEditResult {
    data class Title(val value: String) : TaskFieldEditResult()
    data class Schedule(val dueDate: String?, val dueTime: String?) : TaskFieldEditResult()
    data class NeedsClarification(val prompt: String) : TaskFieldEditResult()
    data object Invalid : TaskFieldEditResult()
    data object PastSchedule : TaskFieldEditResult()
}

class TaskFieldEditResolver(
    private val temporalResolver: TemporalExpressionResolver = TemporalExpressionResolver(),
    private val titleInterpreter: CreateDraftMoveInterpreter = CreateDraftMoveInterpreter()
) {
    fun resolveTitle(raw: String): TaskFieldEditResult {
        val normalized = TextNormalizer.normalize(raw)
        val directCandidate = TITLE_WRAPPER.matchEntire(normalized)
            ?.groupValues
            ?.get(1)
            ?.trim()
        val move = titleInterpreter.interpret(
            directCandidate ?: normalized,
            CreateTaskDialogState.WAITING_FOR_TITLE
        )
        val title = when (move) {
            is CreateDraftMove.ProvideField -> move.value.takeIf { move.field == CreateDraftField.TITLE }
            is CreateDraftMove.ChangeField -> move.value.takeIf { move.field == CreateDraftField.TITLE }
            else -> null
        }?.trim() ?: return TaskFieldEditResult.Invalid
        return TaskFieldEditResult.Title(title)
    }

    fun resolveDate(
        raw: String,
        draft: EditableTaskDraft,
        now: Calendar = Calendar.getInstance()
    ): TaskFieldEditResult {
        val normalized = TextNormalizer.normalize(raw)
        relativeOffset(normalized, DATE_OFFSET_PATTERN)?.let { days ->
            val base = parseDate(draft.dueDate, now) ?: return TaskFieldEditResult.Invalid
            base.add(Calendar.DAY_OF_MONTH, days)
            return checkedSchedule(formatDate(base), draft.dueTime, now)
        }
        val resolution = temporalResolver.resolve(normalized, null, normalized, clone(now))
        if (!resolution.isExactDate || resolution.startDateInclusive.isNullOrBlank()) {
            return TaskFieldEditResult.Invalid
        }
        return checkedSchedule(resolution.startDateInclusive, draft.dueTime, now)
    }

    fun resolveTime(
        raw: String,
        draft: EditableTaskDraft,
        now: Calendar = Calendar.getInstance()
    ): TaskFieldEditResult {
        val normalized = TextNormalizer.normalize(raw)
        relativeOffset(normalized, TIME_OFFSET_PATTERN)?.let { hours ->
            val base = parseSchedule(draft.dueDate, draft.dueTime, now)
                ?: return TaskFieldEditResult.Invalid
            base.add(Calendar.HOUR_OF_DAY, hours)
            return checkedSchedule(formatDate(base), formatTime(base), now)
        }
        val halfPast = HALF_PAST_PATTERN.matchEntire(stripTemporalWrapper(normalized))
        val timeText = if (halfPast != null) {
            val hour = number(halfPast.groupValues[1]) ?: return TaskFieldEditResult.Invalid
            val meridiem = halfPast.groupValues[2]
            if (meridiem.isBlank()) {
                return TaskFieldEditResult.NeedsClarification(
                    "Did you mean ${hour}:30 AM or ${hour}:30 PM?"
                )
            }
            "$hour:30 $meridiem"
        } else {
            stripTemporalWrapper(normalized)
        }
        val resolution = temporalResolver.resolve(null, timeText, timeText, clone(now))
        if (!resolution.isExactTime || resolution.startMinuteInclusive == null) {
            return if (resolution.hasTimeConstraint) {
                TaskFieldEditResult.NeedsClarification("What exact time would you like to use?")
            } else {
                TaskFieldEditResult.Invalid
            }
        }
        return checkedSchedule(
            draft.dueDate,
            formatTime(resolution.startMinuteInclusive),
            now
        )
    }

    fun validateDraft(
        draft: EditableTaskDraft,
        now: Calendar = Calendar.getInstance()
    ): TaskFieldEditResult {
        if (draft.title.isBlank()) {
            return TaskFieldEditResult.Invalid
        }
        return checkedSchedule(draft.dueDate, draft.dueTime, now)
    }

    private fun checkedSchedule(
        dueDate: String?,
        dueTime: String?,
        now: Calendar
    ): TaskFieldEditResult {
        if (dueDate.isNullOrBlank() && dueTime.isNullOrBlank()) {
            return TaskFieldEditResult.Schedule(null, null)
        }
        val resolution = temporalResolver.resolve(
            dueDate,
            dueTime,
            listOfNotNull(dueDate, dueTime).joinToString(" "),
            clone(now)
        )
        return when (TemporalActionPolicy.evaluate(resolution, TemporalUseCase.UPDATE, clone(now))) {
            is TemporalPolicyResult.Unresolved -> TaskFieldEditResult.Invalid
            is TemporalPolicyResult.InvalidPastSchedule -> TaskFieldEditResult.PastSchedule
            else -> TaskFieldEditResult.Schedule(dueDate.clean(), dueTime.clean())
        }
    }

    private fun relativeOffset(text: String, pattern: Regex): Int? {
        val match = pattern.matchEntire(stripTemporalWrapper(text)) ?: return null
        return number(match.groupValues[1])
    }

    private fun stripTemporalWrapper(value: String): String = value
        .replace(Regex("^(?:move|change|set) it (?:to )?"), "")
        .removePrefix("to ")
        .removePrefix("the ")
        .trim()

    private fun number(value: String): Int? = value.toIntOrNull() ?: NUMBERS[value]

    private fun parseDate(value: String?, base: Calendar): Calendar? {
        if (value.isNullOrBlank()) return null
        return parse(DATE_PATTERN, value, base)
    }

    private fun parseSchedule(date: String?, time: String?, base: Calendar): Calendar? {
        if (date.isNullOrBlank() || time.isNullOrBlank()) return null
        return parse(DATE_TIME_PATTERN, "$date $time", base)
    }

    private fun parse(pattern: String, value: String, base: Calendar): Calendar? = runCatching {
        val formatter = SimpleDateFormat(pattern, Locale.UK).apply {
            isLenient = false
            timeZone = base.timeZone
        }
        formatter.parse(value)?.let { parsed ->
            Calendar.getInstance(base.timeZone).apply { time = parsed }
        }
    }.getOrNull()

    private fun formatDate(value: Calendar): String =
        SimpleDateFormat(DATE_PATTERN, Locale.UK).apply { timeZone = value.timeZone }.format(value.time)

    private fun formatTime(value: Calendar): String =
        formatTime(value.get(Calendar.HOUR_OF_DAY) * 60 + value.get(Calendar.MINUTE))

    private fun formatTime(minute: Int): String =
        SimpleDateFormat(TIME_PATTERN, Locale.UK).format(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
            }.time
        ).uppercase(Locale.UK)

    private fun clone(value: Calendar): Calendar = value.clone() as Calendar

    private companion object {
        const val DATE_PATTERN = "dd/MM/yyyy"
        const val TIME_PATTERN = "hh:mm a"
        const val DATE_TIME_PATTERN = "$DATE_PATTERN $TIME_PATTERN"
        val DATE_OFFSET_PATTERN = Regex("^(one|two|three|four|five|six|seven|\\d+) days? later$")
        val TIME_OFFSET_PATTERN = Regex("^(one|two|three|four|five|six|seven|\\d+) hours? later$")
        val HALF_PAST_PATTERN = Regex(
            "^half past (one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|\\d{1,2})(?: (am|pm))?$"
        )
        val TITLE_WRAPPER = Regex(
            "^i (?:want|would like) (?:the )?(?:task )?(?:title|name) to be (.+)$"
        )
        val NUMBERS = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4,
            "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
            "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12
        )
    }
}

private fun TaskEntity.toTaskDetailBase() = TaskDetailAuthoritativeBase(
    taskId = id,
    title = title,
    dueDate = dueDate,
    dueTime = dueTime,
    isDone = isDone
)

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
