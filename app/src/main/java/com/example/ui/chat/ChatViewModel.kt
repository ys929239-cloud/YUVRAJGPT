package com.example.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiRepository
import com.example.data.db.ChatMessage
import com.example.data.db.ChatRepository
import com.example.data.db.ChatSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val toolId: String,
    private val geminiRepository: GeminiRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId
    
    val sessions: StateFlow<List<ChatSession>> = chatRepository.getSessions(toolId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                chatRepository.getMessages(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // We do not auto-create a session. We wait for the user to select one, or 
        // type a message which will create one. Or `AppNavigation` can tell it to start a new one.
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun startNewSession() {
        val newSessionId = UUID.randomUUID().toString()
        val session = ChatSession(newSessionId, toolId, "New Chat", System.currentTimeMillis())
        viewModelScope.launch {
            chatRepository.createSession(session)
            _currentSessionId.value = newSessionId
        }
    }

    fun loadSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            var sessionId = _currentSessionId.value
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString()
                val sessionTitle = text.take(20) + if (text.length > 20) "..." else ""
                val session = ChatSession(sessionId, toolId, sessionTitle, System.currentTimeMillis())
                chatRepository.createSession(session)
                _currentSessionId.value = sessionId
            } else {
                // Check if this is the first message for an empty session, maybe rename it
                // We'll skip for now to keep it simple
            }

            _isLoading.value = true
            _error.value = null
            
            val userMsg = ChatMessage(id = UUID.randomUUID().toString(), sessionId = sessionId, text = text, isUser = true, timestamp = System.currentTimeMillis())
            chatRepository.saveMessage(userMsg)

            if (toolId == "music") {
                val response = geminiRepository.generateMusic(text)
                response.onSuccess { base64Audio ->
                    val aiMsg = ChatMessage(id = UUID.randomUUID().toString(), sessionId = sessionId, text = "[AUDIO_BASE64] $base64Audio", isUser = false, timestamp = System.currentTimeMillis())
                    chatRepository.saveMessage(aiMsg)
                }.onFailure { e ->
                    _error.value = e.message
                }
            } else {
                val systemInstruction = when (toolId) {
                    "homework" -> "You are a helpful homework assistant. Provide step-by-step explanations."
                    "youtube" -> "You are a YouTube strategist. Generate video ideas, titles, hooks, scripts, and thumbnail concepts."
                    else -> "You are YUVRAJGPT, a powerful AI assistant."
                }
                val response = geminiRepository.generateChatResponse(text, systemInstruction)
                response.onSuccess { replyText ->
                    val aiMsg = ChatMessage(id = UUID.randomUUID().toString(), sessionId = sessionId, text = replyText, isUser = false, timestamp = System.currentTimeMillis())
                    chatRepository.saveMessage(aiMsg)
                }.onFailure { e ->
                    _error.value = e.message
                }
            }
            
            _isLoading.value = false
        }
    }

    fun sendLiveMessage(text: String, onResponse: (String, String?) -> Unit) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            var sessionId = _currentSessionId.value
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString()
                val sessionTitle = "Voice Chat"
                val session = ChatSession(sessionId, toolId, sessionTitle, System.currentTimeMillis())
                chatRepository.createSession(session)
                _currentSessionId.value = sessionId
            }

            val userMsg = ChatMessage(id = UUID.randomUUID().toString(), sessionId = sessionId, text = text, isUser = true, timestamp = System.currentTimeMillis())
            chatRepository.saveMessage(userMsg)

            val response = geminiRepository.generateLiveVoiceResponse(text)
            
            response.onSuccess { pair ->
                val replyText = pair.first
                val base64Audio = pair.second
                
                val aiMsg = ChatMessage(id = UUID.randomUUID().toString(), sessionId = sessionId, text = replyText, isUser = false, timestamp = System.currentTimeMillis())
                chatRepository.saveMessage(aiMsg)
                
                onResponse(replyText, base64Audio)
            }.onFailure {
                onResponse("Sorry, I encountered an error.", null)
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            val sessionId = _currentSessionId.value
            if (sessionId != null) {
                chatRepository.clearMessages(sessionId)
            }
        }
    }

    fun generateSpeech(text: String, onAudioReady: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val response = geminiRepository.generateSpeechFromText(text)
            response.onSuccess { audioBase64 ->
                onAudioReady(audioBase64)
            }.onFailure {
                onAudioReady("ERROR:${it.message ?: "Failed to generate speech"}")
            }
        }
    }

    class Factory(
        private val toolId: String,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(toolId, GeminiRepository(), ChatRepository(context)) as T
        }
    }
}
