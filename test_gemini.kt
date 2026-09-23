import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

fun main() {
    val apiKey = System.getenv("GEMINI_API_KEY")
    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$apiKey")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true
    
    val payload = """
        {
            "contents": [{"parts":[{"text":"Read the following text exactly as written: Hello!"}]}],
            "generationConfig": {
                "responseModalities": ["AUDIO"],
                "speechConfig": {
                    "voiceConfig": {
                        "prebuiltVoiceConfig": {
                            "voiceName": "Aoede"
                        }
                    }
                }
            }
        }
    """.trimIndent()
    
    OutputStreamWriter(connection.outputStream).use { it.write(payload) }
    
    try {
        println(connection.inputStream.bufferedReader().readText())
    } catch (e: Exception) {
        println("Error: ${connection.responseCode}")
        println(connection.errorStream.bufferedReader().readText())
    }
}
