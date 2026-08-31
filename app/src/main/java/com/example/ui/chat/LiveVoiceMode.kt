package com.example.ui.chat

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@Composable
fun LiveVoiceMode(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var state by remember { mutableStateOf(VoiceState.IDLE) }
    var transcript by remember { mutableStateOf("Tap the circle to speak") }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                transcript = recognizedText
                state = VoiceState.THINKING
                viewModel.sendLiveMessage(recognizedText) { responseText, base64Audio ->
                    state = VoiceState.SPEAKING
                    transcript = responseText
                    
                    if (base64Audio != null) {
                        try {
                            val decoded = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
                            val tempFile = File.createTempFile("live_audio", ".mp3", context.cacheDir)
                            FileOutputStream(tempFile).use { it.write(decoded) }
                            
                            mediaPlayer?.release()
                            val player = MediaPlayer().apply {
                                setDataSource(tempFile.absolutePath)
                                prepare()
                                start()
                            }
                            mediaPlayer = player
                            player.setOnCompletionListener {
                                state = VoiceState.IDLE
                                it.release()
                                mediaPlayer = null
                            }
                        } catch (e: Exception) {
                            state = VoiceState.IDLE
                        }
                    } else {
                        state = VoiceState.IDLE
                    }
                }
            } else {
                state = VoiceState.IDLE
                transcript = "Tap the circle to speak"
            }
        } else {
            state = VoiceState.IDLE
            transcript = "Tap the circle to speak"
        }
    }

    val startListening = {
        mediaPlayer?.release()
        mediaPlayer = null
        state = VoiceState.LISTENING
        transcript = "Listening..."
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            state = VoiceState.IDLE
            transcript = "Speech recognition not available."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // Animation
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VoiceState.SPEAKING || state == VoiceState.THINKING) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        IconButton(
            onClick = {
                mediaPlayer?.release()
                mediaPlayer = null
                onClose()
            },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close Live Voice")
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            VoiceState.IDLE -> MaterialTheme.colorScheme.primaryContainer
                            VoiceState.LISTENING -> MaterialTheme.colorScheme.primary
                            VoiceState.THINKING -> MaterialTheme.colorScheme.secondary
                            VoiceState.SPEAKING -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                    .clickable(enabled = state == VoiceState.IDLE || state == VoiceState.SPEAKING) {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        startListening()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (state == VoiceState.IDLE || state == VoiceState.SPEAKING) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Tap to speak",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Text(
                text = when (state) {
                    VoiceState.IDLE -> "Ready"
                    VoiceState.LISTENING -> "Listening..."
                    VoiceState.THINKING -> "Thinking..."
                    VoiceState.SPEAKING -> "Speaking..."
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = transcript,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

enum class VoiceState {
    IDLE, LISTENING, THINKING, SPEAKING
}
