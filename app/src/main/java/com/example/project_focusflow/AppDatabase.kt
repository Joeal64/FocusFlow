package com.example.project_focusflow

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FocusSession::class,],
    version = 3
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusSessionDao(): FocusSessionDao
}
