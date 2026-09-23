package com.example.data.db

import android.content.Context
import com.example.util.AppLog
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatRepository(private val context: Context) {
    private val TAG = "ChatRepository"

    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "chat_database"
    )
    .fallbackToDestructiveMigration(false)
    .build()

    private val dao = db.chatMessageDao()
    val firestoreRepository = FirestoreRepository(context)

    private val currentUserId: String?
        get() = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }

    fun getSessions(toolId: String): Flow<List<ChatSession>> = dao.getSessions(toolId)
    
    suspend fun createSession(session: ChatSession) {
        dao.insertSession(session)
        val uid = currentUserId
        if (uid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestoreRepository.saveSession(uid, session)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to background-sync session to cloud", e)
                }
            }
        }
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return dao.getMessages(sessionId)
    }

    suspend fun saveMessage(message: ChatMessage) {
        dao.insertMessage(message)
        val uid = currentUserId
        if (uid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestoreRepository.saveMessage(uid, message)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to background-sync message to cloud", e)
                }
            }
        }
    }

    suspend fun clearMessages(sessionId: String) {
        dao.clearMessages(sessionId)
    }

    suspend fun searchMessages(query: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else dao.searchMessages(query)
    }

    suspend fun searchSessions(query: String): List<ChatSession> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else dao.searchSessions(query)
    }

    suspend fun syncAllWithCloud(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var syncedCount = 0
            // 1. Upload local sessions to cloud
            val localSessions = dao.getAllSessions()
            for (session in localSessions) {
                firestoreRepository.saveSession(userId, session)
                val messages = dao.getAllMessagesForSession(session.id)
                for (msg in messages) {
                    firestoreRepository.saveMessage(userId, msg)
                }
                syncedCount++
            }

            // 2. Fetch cloud sessions and merge to local
            val cloudSessionsResult = firestoreRepository.loadCloudSessions(userId)
            val cloudSessions = cloudSessionsResult.getOrNull() ?: emptyList()
            if (cloudSessions.isNotEmpty()) {
                dao.insertSessions(cloudSessions)
                for (cloudSession in cloudSessions) {
                    val cloudMessagesResult = firestoreRepository.loadCloudMessages(userId, cloudSession.id)
                    val cloudMessages = cloudMessagesResult.getOrNull() ?: emptyList()
                    if (cloudMessages.isNotEmpty()) {
                        dao.insertMessages(cloudMessages)
                    }
                }
            }

            AppLog.d(TAG, "Synced $syncedCount sessions with Firestore")
            Result.success(syncedCount)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to sync with cloud", e)
            Result.failure(e)
        }
    }
}
