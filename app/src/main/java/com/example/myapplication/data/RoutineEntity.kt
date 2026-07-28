package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routines",
    indices = [Index(value = ["normalizedTitle"], name = "index_routines_normalizedTitle")]
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val normalizedTitle: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
