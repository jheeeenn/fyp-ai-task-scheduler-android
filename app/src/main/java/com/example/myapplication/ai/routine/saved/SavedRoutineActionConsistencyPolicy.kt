package com.example.myapplication.ai.routine.saved

import com.example.myapplication.ai.routine.RoutineTitleNormalizer

data class SavedRoutineActionConsistencyResult(
    val decision: SavedRoutineActionDecision,
    val reason: String,
    val titleRecoveredFromLiteralText: Boolean
)

object SavedRoutineActionConsistencyPolicy {
    fun reconcile(
        userText: String,
        modelDecision: SavedRoutineActionDecision
    ): SavedRoutineActionConsistencyResult {
        val normalized = RoutineTitleNormalizer.normalize(userText)
        val cue = strongCue(normalized)

        if (cue == null) {
            return result(modelDecision, "NO_STRONG_ACTION_CUE")
        }
        if (cue.action == modelDecision.action) {
            return result(modelDecision, "MODEL_ACTION_CONSISTENT")
        }

        return when {
            cue.action == SavedRoutineAction.READ_DETAILS &&
                modelDecision.action == SavedRoutineAction.LIST &&
                cue.literalTitle.isNotEmpty() ->
                result(
                    modelDecision.copy(
                        action = SavedRoutineAction.READ_DETAILS,
                        routineTitle = cue.literalTitle,
                        dateText = ""
                    ),
                    reason = "RECONCILED_SINGULAR_READ_FROM_LIST",
                    titleRecovered = true
                )

            cue.action == SavedRoutineAction.LIST &&
                modelDecision.action == SavedRoutineAction.READ_DETAILS ->
                result(
                    modelDecision.copy(
                        action = SavedRoutineAction.LIST,
                        routineTitle = "",
                        dateText = ""
                    ),
                    reason = "RECONCILED_PLURAL_LIST_FROM_DETAILS"
                )

            cue.action == SavedRoutineAction.RUN &&
                modelDecision.action == SavedRoutineAction.LIST &&
                cue.literalTitle.isNotEmpty() ->
                result(
                    modelDecision.copy(
                        action = SavedRoutineAction.RUN,
                        routineTitle = cue.literalTitle,
                        dateText = cue.literalDate
                    ),
                    reason = "RECONCILED_RUN_FROM_LIST",
                    titleRecovered = true
                )

            cue.action == SavedRoutineAction.DELETE &&
                modelDecision.action == SavedRoutineAction.LIST &&
                cue.literalTitle.isNotEmpty() ->
                result(
                    modelDecision.copy(
                        action = SavedRoutineAction.DELETE,
                        routineTitle = cue.literalTitle,
                        dateText = ""
                    ),
                    reason = "RECONCILED_DELETE_FROM_LIST",
                    titleRecovered = true
                )

            else -> result(
                modelDecision.copy(
                    action = SavedRoutineAction.UNKNOWN,
                    routineTitle = "",
                    dateText = ""
                ),
                reason = "CONTRADICTORY_ACTION_UNSAFE_TO_RECONCILE"
            )
        }
    }

    private fun strongCue(normalized: String): StrongCue? {
        if (normalized.isEmpty()) return null
        if (PLURAL_LIST_PATTERNS.any { it.matches(normalized) }) {
            return StrongCue(SavedRoutineAction.LIST)
        }

        READ_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }?.let { title ->
            return StrongCue(SavedRoutineAction.READ_DETAILS, literalTitle = title)
        }

        DELETE_PATTERN.matchEntire(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { title ->
                return StrongCue(SavedRoutineAction.DELETE, literalTitle = title)
            }

        val dateMatch = TRAILING_DATE_PATTERN.find(normalized)
        val withoutDate = if (dateMatch != null) {
            normalized.removeRange(dateMatch.range).trim()
        } else {
            normalized
        }
        RUN_PATTERN.matchEntire(withoutDate)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { title ->
                return StrongCue(
                    action = SavedRoutineAction.RUN,
                    literalTitle = title,
                    literalDate = dateMatch?.groupValues?.get(1).orEmpty()
                )
            }
        return null
    }

    private fun result(
        decision: SavedRoutineActionDecision,
        reason: String,
        titleRecovered: Boolean = false
    ) = SavedRoutineActionConsistencyResult(
        decision = decision,
        reason = reason,
        titleRecoveredFromLiteralText = titleRecovered
    )

    private data class StrongCue(
        val action: SavedRoutineAction,
        val literalTitle: String = "",
        val literalDate: String = ""
    )

    private val PLURAL_LIST_PATTERNS = listOf(
        Regex("^(?:please )?(?:list|name)(?: all)? (?:my |the )?(?:saved )?routines(?: for me)?$"),
        Regex("^(?:what|which) (?:saved )?routines (?:do i have|have i saved|are saved)$"),
        Regex("^how many (?:saved )?routines (?:do i have|have i saved|are saved)$")
    )
    private val READ_PATTERNS = listOf(
        Regex("^(?:please )?(?:read|describe|explain) (?:my |the )?(.+)$"),
        Regex("^(?:please )?what is in (?:my |the )?(.+)$")
    )
    private val DELETE_PATTERN =
        Regex("^(?:please )?delete (?:my |the )?(.+)$")
    private val RUN_PATTERN =
        Regex("^(?:please )?(?:use|run) (?:my |the )?(.+)$")
    private val TRAILING_DATE_PATTERN = Regex(
        "\\s+(today|tomorrow|next (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|week)|on .+)$"
    )
}
