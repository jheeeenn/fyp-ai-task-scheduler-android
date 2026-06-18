package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskEntity::class,
                     LearnedTimePreferenceEntity::class
                     ], version = 6, exportSchema = false)
// version 2 dated on 03/03/2026
// version 3 dated on 06/03/2026
// version 4 dated on 07/03/2026
// version 5 dated on 20/03/2026 -> added learnedTimePreferenceDao
// version 6 dated on 12/06/2026 -> added parent-child task breakdown columns
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun learnedTimePreferenceDao(): LearnedTimePreferenceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN parentTaskId INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE tasks ADD COLUMN subtaskOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_db"
                ).addMigrations(MIGRATION_5_6).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
