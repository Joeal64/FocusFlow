package com.example.project_focusflow

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy

// This interface defines ALL database operations for focus sessions and streaks
@Dao
interface FocusSessionDao {

    // Insert a single focus session row into the database
    @Insert
    suspend fun insert(session: FocusSession)

    // Get all session timestamps ordered from newest to oldest
    @Query("SELECT completedAt FROM focus_sessions ORDER BY completedAt DESC")
    suspend fun getAllTimestamps(): List<Long>

    // Get total focused minutes for today only
    @Query(
        """
        SELECT SUM(durationMinutes) 
        FROM focus_sessions
        WHERE date(completedAt / 1000, 'unixepoch', 'localtime') 
              = date('now', 'localtime')
        """
    )
    suspend fun getMinutesToday(): Int?
    // Returns null if there are no sessions today



    // Insert a new streak record
    // Each call adds a new row
    @Insert
    suspend fun addStreak(streak: DailyStreak)

    // Look up a single streak entry for a specific date
    // Returns null if that date does not exist
    @Query("SELECT * FROM daily_streaks WHERE date = :date LIMIT 1")
    suspend fun getStreak(date: String): DailyStreak?

    // Count how many total streak days exist in the table
    @Query("SELECT COUNT(*) FROM daily_streaks")
    suspend fun getTotalStreaks(): Int

    // Get total focused minutes for a specific date (yyyy-MM-dd format)
    @Query(
        """
        SELECT SUM(durationMinutes)
        FROM focus_sessions
        WHERE date(completedAt / 1000, 'unixepoch', 'localtime') = :date
        """
    )
    suspend fun getMinutesForDate(date: String): Int?
    // Returns null if there were no sessions on that day


    // Get totals for the LAST 7 DAYS ending at endDate
    // Result is grouped by day and sorted oldest to newest
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

// data class to hold daily results
data class DailyMinutes(
    val day: String,
    val minutes: Int
)
