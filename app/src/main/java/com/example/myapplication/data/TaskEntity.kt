package com.example.myapplication.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val dueTime: String? = null,
    val isDone: Boolean = false,
    val dueDate: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val parentTaskId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val subtaskOrder: Int = 0
)
