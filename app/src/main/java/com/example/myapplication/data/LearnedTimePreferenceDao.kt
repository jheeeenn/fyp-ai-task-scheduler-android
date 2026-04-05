package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface LearnedTimePreferenceDao {

    @Query("SELECT * FROM learned_time_preferences WHERE phrase = :phrase LIMIT 1")
    suspend fun getByPhrase(phrase: String): LearnedTimePreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: LearnedTimePreferenceEntity)

    @Update
    suspend fun update(preference: LearnedTimePreferenceEntity)

    @Query("DELETE FROM learned_time_preferences")
    suspend fun clearAll()
}