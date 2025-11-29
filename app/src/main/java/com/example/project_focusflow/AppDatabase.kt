package com.example.project_focusflow

import androidx.room.Database
import androidx.room.RoomDatabase


@Database(
    entities = [FocusSession::class, DailyStreak::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun focusSessionDao(): FocusSessionDao
}
