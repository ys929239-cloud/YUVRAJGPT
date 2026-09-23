package com.example.data.auth

import android.content.Context
import com.example.util.AppLog
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.db.FirestoreRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {
    private val TAG = "AuthRepository"

    private val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            AppLog.e(TAG, "FirebaseAuth initialization failed", e)
            null
        }

    private val credentialManager = CredentialManager.create(context)
    val firestoreRepository = FirestoreRepository(context)

    fun getCurrentUser(): FirebaseUser? = auth?.currentUser

    private fun getServerClientId(): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) {
            context.getString(resId)
        } else {
            try {
                context.getString(com.example.R.string.default_web_client_id)
            } catch (e: Exception) {
                "1020212718432-ai-studio-web-client.apps.googleusercontent.com"
            }
        }
    }

    /**
     * Interactive Google Sign-In using GetSignInWithGoogleOption exclusively.
     * Each GetCredentialRequest carries exactly one credential option for API 36 responsiveness.
     * Catches GetCredentialCancellationException separately and logs a diagnostic warning.
     */
    suspend fun signInWithGoogle(): Result<FirebaseUser> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase Auth is not initialized."))

        val serverClientId = getServerClientId()
        return try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            val idToken = try {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (e: Exception) {
                if (credential is GoogleIdTokenCredential) credential.idToken else null
            }

            if (!idToken.isNullOrEmpty()) {
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return Result.failure(Exception("Firebase user is null after sign in."))
                // Persist user profile to Firestore Enterprise Database
                firestoreRepository.saveUserProfile(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Received invalid Google credential."))
            }
        } catch (e: GetCredentialCancellationException) {
            AppLog.w(TAG, "Google Sign-in was cancelled by user: ${e.message}")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            AppLog.e(TAG, "Credential Manager error during Google Sign-in: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "Unexpected error during Google Sign-in: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Silent auto sign-in using GetGoogleIdOption in a separate request.
     */
    suspend fun silentSignInWithGoogle(): Result<FirebaseUser> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase Auth is not initialized."))
        val existingUser = firebaseAuth.currentUser
        if (existingUser != null) {
            firestoreRepository.saveUserProfile(existingUser)
            return Result.success(existingUser)
        }

        val serverClientId = getServerClientId()
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            val idToken = try {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (e: Exception) {
                if (credential is GoogleIdTokenCredential) credential.idToken else null
            }

            if (!idToken.isNullOrEmpty()) {
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return Result.failure(Exception("Firebase user is null after silent sign in."))
                firestoreRepository.saveUserProfile(user)
                Result.success(user)
            } else {
                Result.failure(Exception("No Google credential returned in silent sign in."))
            }
        } catch (e: Exception) {
            AppLog.d(TAG, "Silent sign in not available or cancelled: ${e.message}")
            Result.failure(e)
        }
    }

    fun signOut() {
        auth?.signOut()
    }
}
