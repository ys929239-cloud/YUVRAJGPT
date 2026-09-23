package com.example.data.api

import com.example.BuildConfig
import com.example.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// --- Common Data Classes for Moshi ---
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: SpeechConfig? = null,
    val imageConfig: ImageConfig? = null
)

data class ImageConfig(
    val aspectRatio: String? = null,
    val imageSize: String? = null,
    val outputMimeType: String? = null
)

data class SpeechConfig(
    val voiceConfig: VoiceConfig
)

data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

data class PrebuiltVoiceConfig(
    val voiceName: String
)

data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null
)

data class GeminiError(
    val code: Int,
    val message: String
)

data class Candidate(
    val content: Content? = null
)

// --- Retrofit Setup ---
interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
    
    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateContentPro(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/gemini-2.5-flash-image:generateContent")
    suspend fun generateImage(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
    
    @POST("v1beta/models/gemini-2.5-flash-preview-tts:generateContent")
    suspend fun generateSpeech(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
    
    @POST("v1beta/models/gemini-2.5-flash-native-audio-preview-12-2025:generateContent")
    suspend fun generateNativeAudio(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/lyria-3-clip-preview:generateContent")
    suspend fun generateMusic(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

class GeminiRepository {
    private val apiKey: String
        get() {
            val key = BuildConfig.GEMINI_API_KEY
            return if (key.isBlank() || key == "MY_GEMINI_API_KEY" || key.startsWith("MY_")) {
                ""
            } else {
                key
            }
        }

    private fun parseApiError(code: Int, errorBody: String?): String {
        val body = errorBody ?: ""
        if (code == 400) {
            if (body.contains("API_KEY_INVALID", ignoreCase = true) || body.contains("API key not valid", ignoreCase = true)) {
                return "Invalid Gemini API key (HTTP 400). Please configure your GEMINI_API_KEY in the AI Studio Secrets panel."
            }
        }
        if (code == 429) {
            return "Quota exceeded (HTTP 429). The system is currently at capacity. Please wait a moment and try again."
        }
        if (code == 403) {
            return "Access denied (HTTP 403). Please verify your Gemini API key permissions."
        }
        if (code == 404) {
            return "Requested AI model or endpoint not found (HTTP 404)."
        }
        if (code in 500..599) {
            return "Gemini service temporarily unavailable (HTTP $code). Please try again in a few moments."
        }
        val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(body)
        val msg = match?.groupValues?.getOrNull(1)
        return if (!msg.isNullOrBlank()) {
            msg
        } else {
            "Request failed with HTTP $code"
        }
    }
    
    suspend fun generateChatResponse(prompt: String, systemInstruction: String): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel."))
        }
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )
        
        try {
            val response = RetrofitClient.service.generateContent(key, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (text != null) Result.success(text)
            else Result.failure(Exception(response.error?.message ?: "Empty response received"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val parsed = parseApiError(e.code(), errorBody)
            AppLog.e("GeminiAPI", "HTTP Error ${e.code()}: $parsed")
            Result.failure(Exception(parsed))
        } catch (e: Exception) {
            AppLog.e("GeminiAPI", "Chat exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun generateImage(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel."))
        }
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseModalities = listOf("TEXT", "IMAGE"),
                imageConfig = ImageConfig(aspectRatio = "1:1", imageSize = "1K")
            )
        )
        
        try {
            val response = RetrofitClient.service.generateImage(key, request)
            val base64 = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.inlineData?.data
            if (base64 != null) Result.success(base64)
            else Result.failure(Exception("Failed to generate image"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val parsed = parseApiError(e.code(), errorBody)
            AppLog.e("GeminiAPI", "Image HTTP Error ${e.code()}: $parsed")
            Result.failure(Exception(parsed))
        } catch (e: Exception) {
            AppLog.e("GeminiAPI", "Image exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun generateMusic(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel."))
        }
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO")
            )
        )
        
        try {
            val response = RetrofitClient.service.generateMusic(key, request)
            val parts = response.candidates?.firstOrNull()?.content?.parts
            val audioData = parts?.firstOrNull { it.inlineData != null }?.inlineData?.data
            if (audioData != null) Result.success(audioData)
            else Result.failure(Exception("Failed to generate music: no audio data"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val parsed = parseApiError(e.code(), errorBody)
            AppLog.e("GeminiAPI", "Music HTTP Error ${e.code()}: $parsed")
            Result.failure(Exception(parsed))
        } catch (e: Exception) {
            AppLog.e("GeminiAPI", "Music exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun generateLiveVoiceResponse(prompt: String, voiceName: String = "Aoede"): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        val chatResult = generateChatResponse(prompt, "You are YUVRAJGPT, a natural, smart, and friendly AI assistant. Keep spoken answers concise, engaging, and direct for real-time conversation.")
        if (chatResult.isSuccess) {
            val text = chatResult.getOrNull() ?: ""
            val speechResult = generateSpeechFromText(text, voiceName)
            val audioData = speechResult.getOrNull()
            Result.success(Pair(text, audioData))
        } else {
            Result.failure(chatResult.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun generateSpeechFromText(prompt: String, voiceName: String = "Aoede"): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel."))
        }
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = "Say the following: $prompt")))),
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO"),
                speechConfig = SpeechConfig(
                    voiceConfig = VoiceConfig(
                        prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = voiceName)
                    )
                )
            )
        )
        
        try {
            val response = RetrofitClient.service.generateSpeech(key, request)
            val parts = response.candidates?.firstOrNull()?.content?.parts
            val audioData = parts?.firstOrNull { it.inlineData != null }?.inlineData?.data
            if (audioData != null) Result.success(audioData)
            else {
                AppLog.e("GeminiAPI", "Failed to generate speech. Response: $response")
                Result.failure(Exception("Failed to generate speech: no audio data"))
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val parsed = parseApiError(e.code(), errorBody)
            AppLog.e("GeminiAPI", "Speech HTTP Error ${e.code()}: $parsed")
            Result.failure(Exception(parsed))
        } catch (e: Exception) {
            AppLog.e("GeminiAPI", "Speech exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
