package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_sessions WHERE toolId = :toolId ORDER BY timestamp DESC")
    fun getSessions(toolId: String): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessions(): List<ChatSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ChatSession>)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(sessionId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getAllMessagesForSession(sessionId: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearMessages(sessionId: String)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM messages WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 20")
    suspend fun searchMessages(query: String): List<ChatMessage>

    @Query("SELECT * FROM chat_sessions WHERE title LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 10")
    suspend fun searchSessions(query: String): List<ChatSession>
}
