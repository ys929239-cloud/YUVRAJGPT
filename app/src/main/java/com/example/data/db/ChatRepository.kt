package com.example.data.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class ChatRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "chat_database"
    )
    .fallbackToDestructiveMigration()
    .build()

    private val dao = db.chatMessageDao()

    fun getSessions(toolId: String): Flow<List<ChatSession>> = dao.getSessions(toolId)
    
    suspend fun createSession(session: ChatSession) {
        dao.insertSession(session)
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return dao.getMessages(sessionId)
    }

    suspend fun saveMessage(message: ChatMessage) {
        dao.insertMessage(message)
    }

    suspend fun clearMessages(sessionId: String) {
        dao.clearMessages(sessionId)
    }
}
