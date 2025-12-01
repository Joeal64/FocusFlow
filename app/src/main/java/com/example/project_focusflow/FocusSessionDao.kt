package com.example.project_focusflow

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSession)

    @Query("SELECT completedAt FROM focus_sessions ORDER BY completedAt DESC")
    suspend fun getAllTimestamps(): List<Long>

    @Query(
        """
        SELECT SUM(durationMinutes) 
        FROM focus_sessions
        WHERE date(completedAt / 1000, 'unixepoch', 'localtime') 
              = date('now', 'localtime')
        """
    )
    suspend fun getMinutesToday(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setStreak(streak: DailyStreak)

    @Query("SELECT * FROM daily_streaks WHERE date = :date LIMIT 1")
    suspend fun getStreak(date: String): DailyStreak?

    // --- FIX: ADD THIS MISSING FUNCTION ---
    @Query("SELECT COUNT(*) FROM daily_streaks")
    suspend fun getTotalStreaks(): Int
}
