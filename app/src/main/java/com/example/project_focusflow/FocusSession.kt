package com.example.project_focusflow

import androidx.room.Entity
import androidx.room.PrimaryKey

// This data class represents ONE focus session record in the database
@Entity(tableName = "focus_sessions")
data class FocusSession(

    // Unique ID for each session
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // How long the session lasted, in minutes
    val durationMinutes: Int,

    // Time the session finished
    // Stored as a Long timestamp
    val completedAt: Long
)
