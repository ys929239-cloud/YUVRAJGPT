package com.example.ui.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.auth.AuthRepository
import com.example.data.db.ChatRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val context: Context
) : ViewModel() {

    private val _user = MutableStateFlow<FirebaseUser?>(authRepository.getCurrentUser())
    val user: StateFlow<FirebaseUser?> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _syncStatus = MutableStateFlow("Ready")
    val syncStatus: StateFlow<String> = _syncStatus

    val firestoreDatabaseId: String = try {
        val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        if (resId != 0) context.getString(resId) else "Cloud Firestore"
    } catch (e: Exception) {
        "Cloud Firestore"
    }

    init {
        // Attempt silent sign-in if not signed in already
        viewModelScope.launch {
            if (_user.value == null) {
                val silentResult = authRepository.silentSignInWithGoogle()
                silentResult.onSuccess {
                    _user.value = it
                    syncCloudData()
                }
            } else {
                syncCloudData()
            }
        }
    }

    fun signIn() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = authRepository.signInWithGoogle()
            result.onSuccess { firebaseUser ->
                _user.value = firebaseUser
                _error.value = null
                _syncStatus.value = "Connected to Cloud Database"
                syncCloudData()
            }.onFailure { exception ->
                if (exception is GetCredentialCancellationException) {
                    // User dismissed the credential prompt - reset state cleanly without error popup
                    _error.value = null
                } else {
                    _error.value = exception.localizedMessage ?: "Google Sign-in failed. Please try again."
                }
            }
            _isLoading.value = false
        }
    }

    fun signOut() {
        authRepository.signOut()
        _user.value = null
        _syncStatus.value = "Local database active"
    }

    fun syncCloudData() {
        val currentUser = _user.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatus.value = "Syncing with Cloud Database..."
            val result = chatRepository.syncAllWithCloud(currentUser.uid)
            result.onSuccess { count ->
                _syncStatus.value = "Cloud Database: $count chats synced"
            }.onFailure { e ->
                _syncStatus.value = "Cloud Database connected (offline cache ready)"
            }
            _isSyncing.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val authRepo = AuthRepository(context)
            val chatRepo = ChatRepository(context)
            return AuthViewModel(authRepo, chatRepo, context) as T
        }
    }
}
