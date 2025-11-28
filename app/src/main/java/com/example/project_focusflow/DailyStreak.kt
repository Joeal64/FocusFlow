package com.example.project_focusflow

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DailyStreak(
    @PrimaryKey val date: String,
    val count: Int
)
