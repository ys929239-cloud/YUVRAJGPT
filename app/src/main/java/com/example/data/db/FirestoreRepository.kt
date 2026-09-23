package com.example.data.db

import android.content.Context
import com.example.util.AppLog
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreRepository(private val context: Context) {
    private val TAG = "FirestoreRepository"

    val firestore: FirebaseFirestore? by lazy {
        try {
            val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
            val dbId = if (resId != 0) context.getString(resId) else null
            if (!dbId.isNullOrBlank()) {
                FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
            } else {
                FirebaseFirestore.getInstance()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to get custom database, falling back to default", e)
            try {
                FirebaseFirestore.getInstance()
            } catch (ex: Exception) {
                AppLog.e(TAG, "Failed to initialize Firestore", ex)
                null
            }
        }
    }

    suspend fun saveUserProfile(user: FirebaseUser): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            val userData = hashMapOf(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: "User"),
                "email" to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "lastActive" to System.currentTimeMillis()
            )
            db.collection("users").document(user.uid)
                .set(userData, SetOptions.merge())
                .await()
            AppLog.d(TAG, "Saved user profile for ${user.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to save user profile", e)
            Result.failure(e)
        }
    }

    suspend fun saveSession(userId: String, session: ChatSession): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            val sessionData = hashMapOf(
                "id" to session.id,
                "toolId" to session.toolId,
                "title" to session.title,
                "timestamp" to session.timestamp,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("users").document(userId)
                .collection("sessions").document(session.id)
                .set(sessionData, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to save session to Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun saveMessage(userId: String, message: ChatMessage): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            val messageData = hashMapOf(
                "id" to message.id,
                "sessionId" to message.sessionId,
                "text" to message.text,
                "isUser" to message.isUser,
                "timestamp" to message.timestamp
            )
            db.collection("users").document(userId)
                .collection("sessions").document(message.sessionId)
                .collection("messages").document(message.id)
                .set(messageData, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to save message to Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun loadCloudSessions(userId: String): Result<List<ChatSession>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            val snapshot = db.collection("users").document(userId)
                .collection("sessions")
                .get()
                .await()
            val sessions = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val toolId = doc.getString("toolId") ?: "chat"
                val title = doc.getString("title") ?: "Chat"
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                ChatSession(id = id, toolId = toolId, title = title, timestamp = timestamp)
            }
            Result.success(sessions)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load sessions from Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun loadCloudMessages(userId: String, sessionId: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            val snapshot = db.collection("users").document(userId)
                .collection("sessions").document(sessionId)
                .collection("messages")
                .orderBy("timestamp")
                .get()
                .await()
            val messages = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val text = doc.getString("text") ?: ""
                val isUser = doc.getBoolean("isUser") ?: false
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                ChatMessage(id = id, sessionId = sessionId, text = text, isUser = isUser, timestamp = timestamp)
            }
            Result.success(messages)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load messages from Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        try {
            db.collection("users").document(userId)
                .collection("sessions").document(sessionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFeedback(
        userId: String?,
        messageId: String,
        isPositive: Boolean,
        reason: String? = null,
        comment: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.success(Unit)
        try {
            val feedbackData = hashMapOf(
                "userId" to (userId ?: "anonymous"),
                "messageId" to messageId,
                "isPositive" to isPositive,
                "reason" to (reason ?: if (isPositive) "helpful" else "unhelpful"),
                "comment" to (comment ?: ""),
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("feedback").add(feedbackData).await()
            AppLog.d(TAG, "Feedback saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to save feedback", e)
            Result.failure(e)
        }
    }
}
