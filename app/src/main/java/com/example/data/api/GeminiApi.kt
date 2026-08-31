package com.example.data.api

import com.example.BuildConfig
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
    private val apiKey = BuildConfig.GEMINI_API_KEY
    
    suspend fun generateChatResponse(prompt: String, systemInstruction: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext Result.failure(Exception("API Key missing"))
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )
        
        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (text != null) Result.success(text)
            else Result.failure(Exception(response.error?.message ?: "Unknown error"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Result.failure(Exception("HTTP Error: ${e.code()} - $errorBody"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun generateImage(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext Result.failure(Exception("API Key missing"))
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseModalities = listOf("TEXT", "IMAGE"),
                imageConfig = ImageConfig(aspectRatio = "1:1", imageSize = "1K")
            )
        )
        
        try {
            val response = RetrofitClient.service.generateImage(apiKey, request)
            // The image generates as base64 in the inlineData part
            val base64 = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.inlineData?.data
            if (base64 != null) Result.success(base64)
            else Result.failure(Exception("Failed to generate image"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Result.failure(Exception("HTTP Error: ${e.code()} - $errorBody"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun generateMusic(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext Result.failure(Exception("API Key missing"))
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO")
            )
        )
        
        try {
            val response = RetrofitClient.service.generateMusic(apiKey, request)
            val parts = response.candidates?.firstOrNull()?.content?.parts
            val audioData = parts?.firstOrNull { it.inlineData != null }?.inlineData?.data
            if (audioData != null) Result.success(audioData)
            else Result.failure(Exception("Failed to generate music: no audio data"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            if (e.code() == 429) {
                Result.failure(Exception("Quota Exceeded (HTTP 429). The system is currently at maximum capacity for music generation. Please wait a moment and try again."))
            } else {
                Result.failure(Exception("HTTP Error: ${e.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateLiveVoiceResponse(prompt: String): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext Result.failure(Exception("API Key missing"))
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseModalities = listOf("TEXT", "AUDIO"),
                speechConfig = SpeechConfig(
                    voiceConfig = VoiceConfig(
                        prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = "Aoede")
                    )
                )
            )
        )
        
        try {
            val response = RetrofitClient.service.generateNativeAudio(apiKey, request)
            val parts = response.candidates?.firstOrNull()?.content?.parts
            val text = parts?.firstOrNull { it.text != null }?.text ?: ""
            val audioData = parts?.firstOrNull { it.inlineData != null }?.inlineData?.data
            Result.success(Pair(text, audioData))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Result.failure(Exception("HTTP Error: ${e.code()} - $errorBody"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateSpeechFromText(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext Result.failure(Exception("API Key missing"))
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = "Read the following text exactly as written: $prompt")))),
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO"),
                speechConfig = SpeechConfig(
                    voiceConfig = VoiceConfig(
                        prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = "Aoede")
                    )
                )
            )
        )
        
        try {
            val response = RetrofitClient.service.generateSpeech(apiKey, request)
            val parts = response.candidates?.firstOrNull()?.content?.parts
            val audioData = parts?.firstOrNull { it.inlineData != null }?.inlineData?.data
            if (audioData != null) Result.success(audioData)
            else {
                android.util.Log.e("GeminiAPI", "Failed to generate speech. Response: $response")
                Result.failure(Exception("Failed to generate speech: no audio data"))
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            android.util.Log.e("GeminiAPI", "HTTP Error: ${e.code()} - $errorBody")
            Result.failure(Exception("HTTP Error: ${e.code()} - $errorBody"))
        } catch (e: Exception) {
            android.util.Log.e("GeminiAPI", "Exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
