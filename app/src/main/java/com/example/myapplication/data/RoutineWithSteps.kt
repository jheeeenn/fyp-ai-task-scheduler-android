package com.example.myapplication.data

import androidx.room.Embedded
import androidx.room.Relation

data class RoutineWithSteps(
    @Embedded
    val routine: RoutineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val steps: List<RoutineStepEntity>
) {
    fun withOrderedSteps(): RoutineWithSteps =
        copy(steps = steps.sortedWith(compareBy(RoutineStepEntity::stepOrder, RoutineStepEntity::id)))
}
