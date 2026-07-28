package com.example.myapplication.ai.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineMigrationContractTest {
    @Test
    fun databaseVersionSevenMigrationCreatesRoutineTablesIndexesAndCascade() {
        val database = File("src/main/java/com/example/myapplication/data/AppDatabase.kt").readText()

        assertTrue(database.contains("version = 7"))
        assertTrue(database.contains("MIGRATION_6_7 = object : Migration(6, 7)"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS `routines`"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS `routine_steps`"))
        assertTrue(database.contains("index_routines_normalizedTitle"))
        assertTrue(database.contains("index_routine_steps_routineId"))
        assertTrue(database.contains("ON DELETE CASCADE"))
        assertTrue(database.contains("addMigrations(MIGRATION_5_6, MIGRATION_6_7)"))
        assertFalse(database.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun existingTaskColumnsRemainUnchangedAndRoutineDaoUsesTransactions() {
        val task = File("src/main/java/com/example/myapplication/data/TaskEntity.kt").readText()
        val dao = File("src/main/java/com/example/myapplication/data/RoutineDao.kt").readText()

        assertEquals(
            7,
            Regex("""^\s*val\s+\w+:""", RegexOption.MULTILINE).findAll(task).count()
        )
        assertFalse(task.contains("routineId"))
        assertTrue(dao.contains("insertRoutineWithSteps"))
        assertTrue(dao.contains("insertRoutineWithFirstOccurrence"))
        assertTrue(dao.contains("insertSavedRoutineOccurrence"))
        assertTrue(dao.contains("deleteRoutineAndSteps"))
        assertTrue(dao.contains("@Transaction"))
    }
}
