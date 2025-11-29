package com.example.project_focusflow

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_streaks")
data class DailyStreak(
    @PrimaryKey
    val date: String, // Format: "yyyy-MM-dd"
    val count: Int     // Total number of streaks achieved for that day
)
