package com.example.project_focusflow

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSession)

    @Query("SELECT completedAt FROM focus_sessions ORDER BY completedAt DESC")
    suspend fun getAllTimestamps(): List<Long>
}
