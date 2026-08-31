package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {
    private val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }

    private val credentialManager = CredentialManager.create(context)

    fun getCurrentUser(): FirebaseUser? = auth?.currentUser

    suspend fun signInWithGoogle(): Result<FirebaseUser> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase is not connected."))

        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val webClientId = if (resId != 0) context.getString(resId) else "YOUR_WEB_CLIENT_ID"

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is GoogleIdTokenCredential) {
                val googleIdToken = credential.idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                Result.success(authResult.user!!)
            } else {
                Result.failure(Exception("Received an invalid credential type."))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign-in failed", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        auth?.signOut()
    }
}
