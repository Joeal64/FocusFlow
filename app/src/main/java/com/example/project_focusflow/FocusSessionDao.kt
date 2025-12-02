package com.example.project_focusflow

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy

@Dao
interface FocusSessionDao {

    @Insert    suspend fun insert(session: FocusSession)

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

    // --- FIX: Change this to a simple Insert to add a new row every time ---
    @Insert
    suspend fun addStreak(streak: DailyStreak)

    @Query("SELECT * FROM daily_streaks WHERE date = :date LIMIT 1")
    suspend fun getStreak(date: String): DailyStreak?

    @Query("SELECT COUNT(*) FROM daily_streaks")
    suspend fun getTotalStreaks(): Int

    @Query(
        """
        SELECT SUM(durationMinutes)
        FROM focus_sessions
        WHERE date(completedAt / 1000, 'unixepoch', 'localtime') = :date
        """
    )
    suspend fun getMinutesForDate(date: String): Int?

    @Query(
        """
        SELECT 
            date(completedAt / 1000, 'unixepoch', 'localtime') AS day, 
            SUM(durationMinutes) AS minutes 
        FROM focus_sessions 
        WHERE date(completedAt / 1000, 'unixepoch', 'localtime') 
              BETWEEN date(:endDate, '-6 days') AND :endDate
        GROUP BY day
        ORDER BY day ASC
        """
    )
    suspend fun getLast7DaysMinutes(endDate: String): List<DailyMinutes>
}

data class DailyMinutes(
    val day: String,
    val minutes: Int
)
