package com.example.project_focusflow

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSession)

    // Get all session timestamps
    @Query("SELECT completedAt FROM focus_sessions ORDER BY completedAt DESC")
    suspend fun getAllTimestamps(): List<Long>

    // Get total minutes studied today
    @Query(
        """
        SELECT SUM(durationMinutes) 
        FROM focus_sessions
        WHERE date(completedAt / 1000, 'unixepoch', 'localtime') 
              = date('now', 'localtime')
        """
    )
    suspend fun getMinutesToday(): Int?

    // Set streak record
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setStreak(streak: DailyStreak)

    // Get streak by date
    @Query("SELECT * FROM daily_streaks WHERE date = :date LIMIT 1")
    suspend fun getStreak(date: String): DailyStreak?

    // Total number of streaks
    @Query("SELECT COUNT(*) FROM daily_streaks")
    suspend fun getTotalStreaks(): Int

    // Get total minutes for a specific date
    @Query(
        """
        SELECT SUM(durationMinutes)
        FROM focus_sessions
        WHERE date(completedAt / 1000, 'unixepoch', 'localtime') = :date
        """
    )
    suspend fun getMinutesForDate(date: String): Int?

    // --- FIX: This function is modified to accept an endDate parameter ---
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

// Helper data class for weekly chart
data class DailyMinutes(
    val day: String,
    val minutes: Int
)
