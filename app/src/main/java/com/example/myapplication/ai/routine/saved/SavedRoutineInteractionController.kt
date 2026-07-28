package com.example.myapplication.ai.routine.saved

import com.example.myapplication.ai.routine.RoutineTitleNormalizer

enum class SavedRoutineInteractionState {
    NONE,
    RESOLVING,
    CHOOSING_MATCH,
    CONFIRMING_DELETE,
    DELETING
}

data class SavedRoutineCandidate(
    val routineId: Long,
    val displayTitle: String
)

data class SavedRoutineInteractionSnapshot(
    val generation: Long,
    val action: SavedRoutineAction,
    val suppliedDatePhrase: String
)

sealed class SavedRoutineChoice {
    data class Selected(val routineId: Long) : SavedRoutineChoice()
    data object Cancelled : SavedRoutineChoice()
    data object Invalid : SavedRoutineChoice()
}

class SavedRoutineInteractionController {
    var state: SavedRoutineInteractionState = SavedRoutineInteractionState.NONE
        private set
    var intendedAction: SavedRoutineAction = SavedRoutineAction.UNKNOWN
        private set
    var suppliedDatePhrase: String = ""
        private set
    var selectedRoutineId: Long? = null
        private set
    val candidates: List<SavedRoutineCandidate>
        get() = internalCandidates.toList()

    private var generation: Long = 0
    private var internalCandidates: List<SavedRoutineCandidate> = emptyList()

    fun begin(action: SavedRoutineAction, suppliedDatePhrase: String): SavedRoutineInteractionSnapshot {
        if (state == SavedRoutineInteractionState.DELETING) return snapshot()
        generation += 1
        state = SavedRoutineInteractionState.RESOLVING
        intendedAction = action
        this.suppliedDatePhrase = suppliedDatePhrase
        selectedRoutineId = null
        internalCandidates = emptyList()
        return snapshot()
    }

    fun chooseCandidates(
        capturedGeneration: Long,
        candidates: List<SavedRoutineCandidate>
    ): Boolean {
        if (!isCurrent(capturedGeneration) || state != SavedRoutineInteractionState.RESOLVING ||
            candidates.size < 2
        ) {
            return false
        }
        internalCandidates = candidates
        state = SavedRoutineInteractionState.CHOOSING_MATCH
        return true
    }

    fun selectSingle(capturedGeneration: Long, routineId: Long): Boolean {
        if (!isCurrent(capturedGeneration) || state != SavedRoutineInteractionState.RESOLVING) {
            return false
        }
        selectedRoutineId = routineId
        return true
    }

    fun choose(input: String): SavedRoutineChoice {
        if (state != SavedRoutineInteractionState.CHOOSING_MATCH) {
            return SavedRoutineChoice.Invalid
        }
        val normalized = RoutineTitleNormalizer.normalize(input)
        if (normalized in CANCELLATIONS) {
            clear()
            return SavedRoutineChoice.Cancelled
        }
        val ordinalIndex = when (normalized) {
            "first", "first option", "one", "option one" -> 0
            "second", "second option", "two", "option two" -> 1
            "third", "third option", "three", "option three" -> 2
            "fourth", "fourth option", "four", "option four" -> 3
            "fifth", "fifth option", "five", "option five" -> 4
            else -> null
        }
        val selected = ordinalIndex?.let(internalCandidates::getOrNull) ?: run {
            val byTitle = internalCandidates.filter {
                RoutineTitleNormalizer.normalize(it.displayTitle) == normalized
            }
            byTitle.singleOrNull()
        } ?: return SavedRoutineChoice.Invalid

        selectedRoutineId = selected.routineId
        state = SavedRoutineInteractionState.RESOLVING
        internalCandidates = emptyList()
        return SavedRoutineChoice.Selected(selected.routineId)
    }

    fun beginDeleteConfirmation(capturedGeneration: Long, routineId: Long): Boolean {
        if (!isCurrent(capturedGeneration) || selectedRoutineId != routineId ||
            state != SavedRoutineInteractionState.RESOLVING
        ) {
            return false
        }
        state = SavedRoutineInteractionState.CONFIRMING_DELETE
        return true
    }

    fun claimDelete(): Long? {
        if (state != SavedRoutineInteractionState.CONFIRMING_DELETE) return null
        val routineId = selectedRoutineId ?: return null
        state = SavedRoutineInteractionState.DELETING
        return routineId
    }

    fun isCurrent(capturedGeneration: Long): Boolean =
        capturedGeneration == generation && state != SavedRoutineInteractionState.NONE

    fun snapshot(): SavedRoutineInteractionSnapshot =
        SavedRoutineInteractionSnapshot(generation, intendedAction, suppliedDatePhrase)

    fun completeDelete(capturedGeneration: Long): Boolean {
        if (!isCurrent(capturedGeneration) ||
            state != SavedRoutineInteractionState.DELETING
        ) {
            return false
        }
        reset()
        return true
    }

    fun clear(): Boolean {
        if (state == SavedRoutineInteractionState.DELETING) return false
        reset()
        return true
    }

    fun clearForActivityDestruction() {
        reset()
    }

    private fun reset() {
        generation += 1
        state = SavedRoutineInteractionState.NONE
        intendedAction = SavedRoutineAction.UNKNOWN
        suppliedDatePhrase = ""
        selectedRoutineId = null
        internalCandidates = emptyList()
    }

    private companion object {
        val CANCELLATIONS = setOf("cancel", "stop", "never mind", "nevermind")
    }
}
