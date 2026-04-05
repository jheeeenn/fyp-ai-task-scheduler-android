package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TaskEntity::class,
                     LearnedTimePreferenceEntity::class
                     ], version = 5, exportSchema = false)
// version 2 dated on 03/03/2026
// version 3 dated on 06/03/2026
// version 4 dated on 07/03/2026
// version 5 dated on 20/03/2026 -> added learnedTimePreferenceDao
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun learnedTimePreferenceDao(): LearnedTimePreferenceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}