package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class, ChatSession::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
}
