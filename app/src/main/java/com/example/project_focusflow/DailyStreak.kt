package com.example.project_focusflow

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_streaks")
data class DailyStreak(
    // add primary key to allow multiple entries
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val date: String, // Format is year,month,day
    val count: Int     // This will always be 1 for each entry now
)
