package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        LearnedTimePreferenceEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class
    ],
    version = 7,
    exportSchema = false
)
// version 2 dated on 03/03/2026
// version 3 dated on 06/03/2026
// version 4 dated on 07/03/2026
// version 5 dated on 20/03/2026 -> added learnedTimePreferenceDao
// version 6 dated on 12/06/2026 -> added parent-child task breakdown columns
// version 7 dated on 28/07/2026 -> added reusable routine templates and ordered steps
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun learnedTimePreferenceDao(): LearnedTimePreferenceDao
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN parentTaskId INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE tasks ADD COLUMN subtaskOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routines` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `normalizedTitle` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_routines_normalizedTitle` " +
                        "ON `routines` (`normalizedTitle`)"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routine_steps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `routineId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `dueTime` TEXT NOT NULL,
                        `stepOrder` INTEGER NOT NULL,
                        FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_routine_steps_routineId` " +
                        "ON `routine_steps` (`routineId`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_db"
                ).addMigrations(MIGRATION_5_6, MIGRATION_6_7).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
