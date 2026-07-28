package com.example.myapplication.ai.routine

sealed class RoutineFollowUpMove {
    data object Confirm : RoutineFollowUpMove()
    data object Reject : RoutineFollowUpMove()
    data object Cancel : RoutineFollowUpMove()
    data object Repeat : RoutineFollowUpMove()
    data class ChangeStepTime(val stepIndex: Int, val value: String) : RoutineFollowUpMove()
    data class ChangeStepTitle(val stepIndex: Int, val value: String) : RoutineFollowUpMove()
    data class ChangeSharedDate(val value: String) : RoutineFollowUpMove()
    data object StructuralChange : RoutineFollowUpMove()
    data object Unknown : RoutineFollowUpMove()
}

object RoutineFollowUpInterpreter {
    fun interpret(normalizedText: String): RoutineFollowUpMove {
        val normalized = normalizedText.trim().lowercase()
        val tokens = normalized.split(Regex("""\s+"""))
        val text = if (
            tokens.size >= 2 &&
            tokens.all { it == tokens.first() } &&
            tokens.first() in REPEATABLE_SIMPLE_CONTROLS
        ) {
            tokens.first()
        } else {
            normalized
        }
        if (text in CONFIRMATIONS) return RoutineFollowUpMove.Confirm
        if (text in REJECTIONS) return RoutineFollowUpMove.Reject
        if (text in CANCELLATIONS) return RoutineFollowUpMove.Cancel
        if (text in REPEATS) return RoutineFollowUpMove.Repeat
        if (STRUCTURAL_HINTS.any(text::contains)) return RoutineFollowUpMove.StructuralChange

        CHANGE_TIME.matchEntire(text)?.let { match ->
            val index = ordinalIndex(match.groupValues[1])
                ?: return RoutineFollowUpMove.Unknown
            return RoutineFollowUpMove.ChangeStepTime(index, match.groupValues[2].trim())
        }
        CHANGE_TITLE.matchEntire(text)?.let { match ->
            val index = ordinalIndex(match.groupValues[1])
                ?: return RoutineFollowUpMove.Unknown
            return RoutineFollowUpMove.ChangeStepTitle(index, match.groupValues[2].trim())
        }
        CHANGE_DATE.matchEntire(text)?.let { match ->
            return RoutineFollowUpMove.ChangeSharedDate(match.groupValues[1].trim())
        }
        return RoutineFollowUpMove.Unknown
    }

    private fun ordinalIndex(value: String): Int? = when (value) {
        "first", "1", "1st" -> 0
        "second", "2", "2nd" -> 1
        "third", "3", "3rd" -> 2
        "fourth", "4", "4th" -> 3
        "fifth", "5", "5th" -> 4
        else -> null
    }

    private val CONFIRMATIONS = setOf(
        "yes",
        "create it",
        "create them",
        "save the routine",
        "save it",
        "confirm"
    )
    private val REJECTIONS = setOf("no", "reject", "do not create it", "don't create it")
    private val CANCELLATIONS = setOf("cancel", "stop", "never mind", "nevermind")
    private val REPEATABLE_SIMPLE_CONTROLS = setOf("yes", "no", "cancel", "repeat")
    private val REPEATS = setOf(
        "repeat",
        "repeat the routine",
        "say that again",
        "read it again"
    )
    private val STRUCTURAL_HINTS = listOf(
        "add a step",
        "add another",
        "remove a step",
        "remove the",
        "delete a step"
    )
    private val CHANGE_TIME = Regex(
        """change the (first|second|third|fourth|fifth|[1-5](?:st|nd|rd|th)?) (?:step(?:'s)? )?time to (.+)"""
    )
    private val CHANGE_TITLE = Regex(
        """change the (first|second|third|fourth|fifth|[1-5](?:st|nd|rd|th)?) (?:task|step)(?: title)? to (.+)"""
    )
    private val CHANGE_DATE = Regex("""change (?:the |this routine's )?date to (.+)""")
}
