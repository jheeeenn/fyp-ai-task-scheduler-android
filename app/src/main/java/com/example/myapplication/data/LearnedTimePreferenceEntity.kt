package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_time_preferences")
data class LearnedTimePreferenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val phrase: String,
    val resolvedTime: String,
    val usageCount: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)