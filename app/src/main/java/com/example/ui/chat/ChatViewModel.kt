package com.example.ui.chat

import android.content.Context
import com.example.util.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiRepository
import com.example.data.db.ChatMessage
import com.example.data.db.ChatRepository
import com.example.data.db.ChatSession
import com.google.firebase.auth.FirebaseAuth
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
    val toolId: String,
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Selected voice for Gemini Live Voice Talking (Aoede, Puck, Charon, Kore, Fenrir)
    private val _selectedVoice = MutableStateFlow("Aoede")
    val selectedVoice: StateFlow<String> = _selectedVoice

    // Message feedback tracking: messageId -> isPositive (true = thumbs up, false = thumbs down)
    private val _feedbackMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val feedbackMap: StateFlow<Map<String, Boolean>> = _feedbackMap

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResultsMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val searchResultsMessages: StateFlow<List<ChatMessage>> = _searchResultsMessages

    private val _searchResultsSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val searchResultsSessions: StateFlow<List<ChatSession>> = _searchResultsSessions

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResultsMessages.value = emptyList()
            _searchResultsSessions.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResultsMessages.value = chatRepository.searchMessages(query)
            _searchResultsSessions.value = chatRepository.searchSessions(query)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResultsMessages.value = emptyList()
        _searchResultsSessions.value = emptyList()
    }

    fun setVoice(voice: String) {
        _selectedVoice.value = voice
    }

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
                val sessionTitle = text.take(24) + if (text.length > 24) "..." else ""
                val session = ChatSession(sessionId, toolId, sessionTitle, System.currentTimeMillis())
                chatRepository.createSession(session)
                _currentSessionId.value = sessionId
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
                    "homework" -> "You are a helpful homework assistant. Provide clear, accurate step-by-step explanations."
                    "youtube" -> "You are an elite YouTube strategist. Generate compelling video concepts, viral titles, hooks, scripts, and thumbnail ideas."
                    else -> "You are YUVRAJGPT, an advanced and friendly AI assistant powered by Yuvraj. Be helpful, direct, concise, and thoughtful."
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

    fun regenerateLastResponse(lastAiMessage: ChatMessage) {
        viewModelScope.launch {
            val currentList = messages.value
            val aiIndex = currentList.indexOfFirst { it.id == lastAiMessage.id }
            val prevUserMessage = if (aiIndex > 0) currentList[aiIndex - 1] else null
            val promptText = prevUserMessage?.text ?: return@launch
            
            _isLoading.value = true
            _error.value = null
            
            val systemInstruction = when (toolId) {
                "homework" -> "You are a helpful homework assistant. Provide clear, accurate step-by-step explanations."
                "youtube" -> "You are an elite YouTube strategist. Generate compelling video concepts, viral titles, hooks, scripts, and thumbnail ideas."
                else -> "You are YUVRAJGPT, an advanced and friendly AI assistant built with cutting-edge Google Gemini intelligence. Be helpful, direct, and thoughtful."
            }
            val response = geminiRepository.generateChatResponse(promptText, systemInstruction)
            response.onSuccess { replyText ->
                val newAiMsg = ChatMessage(id = UUID.randomUUID().toString(), sessionId = lastAiMessage.sessionId, text = replyText, isUser = false, timestamp = System.currentTimeMillis())
                chatRepository.saveMessage(newAiMsg)
            }.onFailure { e ->
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }

    fun submitFeedback(
        messageId: String,
        isPositive: Boolean,
        reason: String? = null,
        comment: String? = null
    ) {
        _feedbackMap.value = _feedbackMap.value + (messageId to isPositive)
        viewModelScope.launch {
            try {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                chatRepository.firestoreRepository.saveFeedback(
                    userId = currentUid,
                    messageId = messageId,
                    isPositive = isPositive,
                    reason = reason,
                    comment = comment
                )
            } catch (e: Exception) {
                AppLog.w("ChatViewModel", "Feedback save notice: ${e.message}")
            }
        }
    }

    fun sendLiveMessage(text: String, onResponse: (String, String?) -> Unit) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            var sessionId = _currentSessionId.value
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString()
                val sessionTitle = "Live Voice Chat"
                val session = ChatSession(sessionId, toolId, sessionTitle, System.currentTimeMillis())
                chatRepository.createSession(session)
                _currentSessionId.value = sessionId
            }

            val userMsg = ChatMessage(id = UUID.randomUUID().toString(), sessionId = sessionId, text = text, isUser = true, timestamp = System.currentTimeMillis())
            chatRepository.saveMessage(userMsg)

            val response = geminiRepository.generateLiveVoiceResponse(text, _selectedVoice.value)
            
            response.onSuccess { pair ->
                val replyText = pair.first
                val base64Audio = pair.second
                
                val aiMsg = ChatMessage(id = UUID.randomUUID().toString(), sessionId = sessionId, text = replyText, isUser = false, timestamp = System.currentTimeMillis())
                chatRepository.saveMessage(aiMsg)
                
                onResponse(replyText, base64Audio)
            }.onFailure {
                onResponse("Sorry, I encountered an error processing your request. Please try again.", null)
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
            val response = geminiRepository.generateSpeechFromText(text, _selectedVoice.value)
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
