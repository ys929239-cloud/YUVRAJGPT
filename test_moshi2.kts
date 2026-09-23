import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)
data class Content(val parts: List<Part>)
data class Part(val text: String? = null)

val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
val adapter = moshi.adapter(GenerateContentRequest::class.java)
val req = GenerateContentRequest(
    contents = listOf(Content(parts = listOf(Part(text = "Hello!")))),
    systemInstruction = Content(parts = listOf(Part(text = "You are a bot")))
)
File("moshi_output.txt").writeText(adapter.toJson(req))
