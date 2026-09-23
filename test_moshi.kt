import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)
data class Content(val parts: List<Part>)
data class Part(val text: String? = null)
data class GenerationConfig(
    val responseModalities: List<String>? = null,
    val speechConfig: SpeechConfig? = null
)
data class SpeechConfig(val voiceConfig: VoiceConfig)
data class VoiceConfig(val prebuiltVoiceConfig: PrebuiltVoiceConfig)
data class PrebuiltVoiceConfig(val voiceName: String)

fun main() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(GenerateContentRequest::class.java)
    val req = GenerateContentRequest(
        contents = listOf(Content(parts = listOf(Part(text = "Hello!")))),
        generationConfig = GenerationConfig(
            responseModalities = listOf("AUDIO"),
            speechConfig = SpeechConfig(
                voiceConfig = VoiceConfig(
                    prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = "Aoede")
                )
            )
        )
    )
    println(adapter.toJson(req))
}
