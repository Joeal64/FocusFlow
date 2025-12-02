package com.example.project_focusflow

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_streaks")
data class DailyStreak(
    // --- FIX: Add an auto-generating Primary Key to allow multiple entries ---
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val date: String, // Format: "yyyy-MM-dd"
    val count: Int     // This will always be 1 for each entry now
)
