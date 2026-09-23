package com.example.ui.chat

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class VoiceState {
    IDLE, LISTENING, THINKING, SPEAKING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceMode(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var state by remember { mutableStateOf(VoiceState.IDLE) }
    var userTranscript by remember { mutableStateOf("") }
    var aiTranscript by remember { mutableStateOf("Tap the orb to start talking with YUVRAJGPT Live") }
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    
    var showVoicePicker by remember { mutableStateOf(false) }
    var liveFeedbackState by remember { mutableStateOf<Boolean?>(null) } // true: up, false: down
    var feedbackFeedbackNotice by remember { mutableStateOf<String?>(null) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                userTranscript = recognizedText
                state = VoiceState.THINKING
                liveFeedbackState = null
                feedbackFeedbackNotice = null
                aiTranscript = "Thinking..."

                viewModel.sendLiveMessage(recognizedText) { responseText, base64Audio ->
                    state = VoiceState.SPEAKING
                    aiTranscript = responseText
                    
                    if (base64Audio != null) {
                        try {
                            val decoded = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
                            val tempFile = File.createTempFile("live_gemini", ".mp3", context.cacheDir)
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
            }
        } else {
            state = VoiceState.IDLE
        }
    }

    val startListening: () -> Unit = {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        state = VoiceState.LISTENING
        userTranscript = ""
        aiTranscript = "Listening to you..."
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            state = VoiceState.IDLE
            aiTranscript = "Speech recognition is not supported on this device."
        }
    }

    val stopSpeaking: () -> Unit = {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        state = VoiceState.IDLE
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // Dynamic pulsating animation for Gemini Live Aura
    val infiniteTransition = rememberInfiniteTransition(label = "live_aura")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (state == VoiceState.SPEAKING || state == VoiceState.LISTENING) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = if (state == VoiceState.SPEAKING || state == VoiceState.LISTENING) 1.45f else 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )

    val orbGradient = when (state) {
        VoiceState.IDLE -> Brush.radialGradient(
            colors = listOf(Color(0xFF38ef7d), Color(0xFF11998e))
        )
        VoiceState.LISTENING -> Brush.radialGradient(
            colors = listOf(Color(0xFF00c6ff), Color(0xFF0072ff))
        )
        VoiceState.THINKING -> Brush.radialGradient(
            colors = listOf(Color(0xFFf37335), Color(0xFFfdc830))
        )
        VoiceState.SPEAKING -> Brush.radialGradient(
            colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0), Color(0xFF00c6ff))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E15))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("live_talking_screen")
    ) {
        // --- TOP BAR: Title, Live Indicator, Voice Selector, Close ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF00E676),
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "YUVRAJGPT Live",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Voice Selector Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showVoicePicker = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = "Voice",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = selectedVoice,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        stopSpeaking()
                        onClose()
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Live Voice", tint = Color.White)
                }
            }
        }

        // --- CENTER: Animated Gemini Live Glowing Orb & Live Transcripts ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Status Text
            Text(
                text = when (state) {
                    VoiceState.IDLE -> "Tap orb to speak"
                    VoiceState.LISTENING -> "Listening..."
                    VoiceState.THINKING -> "Thinking..."
                    VoiceState.SPEAKING -> "YUVRAJGPT is talking..."
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = when (state) {
                    VoiceState.LISTENING -> Color(0xFF00c6ff)
                    VoiceState.THINKING -> Color(0xFFfdc830)
                    VoiceState.SPEAKING -> Color(0xFFBB86FC)
                    VoiceState.IDLE -> Color.White.copy(alpha = 0.7f)
                }
            )

            Spacer(Modifier.height(36.dp))

            // Multi-Layered Pulsing Glowing Orb
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Ripple Ring
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .scale(pulseScale2)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                VoiceState.SPEAKING -> Color(0xFF8E2DE2).copy(alpha = 0.18f)
                                VoiceState.LISTENING -> Color(0xFF0072ff).copy(alpha = 0.18f)
                                else -> Color.White.copy(alpha = 0.05f)
                            }
                        )
                )
                // Inner Glow Ring
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(pulseScale1)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                VoiceState.SPEAKING -> Color(0xFF4A00E0).copy(alpha = 0.35f)
                                VoiceState.LISTENING -> Color(0xFF00c6ff).copy(alpha = 0.3f)
                                else -> Color.White.copy(alpha = 0.08f)
                            }
                        )
                )
                // Center Core Orb
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(orbGradient)
                        .clickable {
                            if (state == VoiceState.SPEAKING) {
                                stopSpeaking()
                            } else {
                                startListening()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (state) {
                            VoiceState.SPEAKING -> Icons.Default.GraphicEq
                            VoiceState.THINKING -> Icons.Default.AutoAwesome
                            VoiceState.LISTENING -> Icons.Default.Mic
                            VoiceState.IDLE -> Icons.Default.Mic
                        },
                        contentDescription = "Live Voice Orb",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // User Speech Transcript
            if (userTranscript.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "\"$userTranscript\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // AI Response Transcript
            Text(
                text = aiTranscript,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(20.dp))

            // --- LIVE FEEDBACK OPTION LIKE GEMINI ---
            AnimatedVisibility(visible = state == VoiceState.SPEAKING || (state == VoiceState.IDLE && userTranscript.isNotBlank())) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Live Feedback:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.width(8.dp))

                        // Thumbs Up
                        IconButton(
                            onClick = {
                                liveFeedbackState = true
                                feedbackFeedbackNotice = "Thank you for positive feedback!"
                                viewModel.submitFeedback(
                                    messageId = "live_${System.currentTimeMillis()}",
                                    isPositive = true,
                                    reason = "Live voice response was great"
                                )
                                Toast.makeText(context, "Thanks for the feedback!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (liveFeedbackState == true) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "Good response",
                                tint = if (liveFeedbackState == true) Color(0xFF00E676) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Thumbs Down
                        IconButton(
                            onClick = {
                                liveFeedbackState = false
                                feedbackFeedbackNotice = "Feedback noted. We'll improve!"
                                viewModel.submitFeedback(
                                    messageId = "live_${System.currentTimeMillis()}",
                                    isPositive = false,
                                    reason = "Live voice response had issues"
                                )
                                Toast.makeText(context, "Feedback recorded. We'll improve!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (liveFeedbackState == false) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                contentDescription = "Poor response",
                                tint = if (liveFeedbackState == false) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (feedbackFeedbackNotice != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = feedbackFeedbackNotice!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (liveFeedbackState == true) Color(0xFF00E676) else Color(0xFFFFAB40)
                        )
                    }
                }
            }
        }

        // --- BOTTOM CONTROLS: Interrupt, Mic, and End Live Mode ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interrupt / Pause Button (if speaking)
            FilledTonalIconButton(
                onClick = { stopSpeaking() },
                enabled = state == VoiceState.SPEAKING,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.05f),
                    disabledContentColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.size(54.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Interrupt Gemini")
            }

            // Central Talk Button
            FloatingActionButton(
                onClick = {
                    if (state == VoiceState.SPEAKING) {
                        stopSpeaking()
                    }
                    startListening()
                },
                containerColor = Color(0xFF00c6ff),
                contentColor = Color.Black,
                modifier = Modifier.size(68.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Talk Now", modifier = Modifier.size(32.dp))
            }

            // End Live Mode Button
            FilledTonalIconButton(
                onClick = {
                    stopSpeaking()
                    onClose()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFFFF5252).copy(alpha = 0.2f),
                    contentColor = Color(0xFFFF5252)
                ),
                modifier = Modifier.size(54.dp)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "End Call")
            }
        }
    }

    // Voice Selector Sheet
    if (showVoicePicker) {
        val voices = listOf(
            "Aoede" to "Warm & Melodic (Default)",
            "Puck" to "Energetic & Playful",
            "Charon" to "Calm & Deep",
            "Kore" to "Gentle & Clear",
            "Fenrir" to "Strong & Direct"
        )
        AlertDialog(
            onDismissRequest = { showVoicePicker = false },
            title = { Text("Choose Gemini Live Voice") },
            text = {
                Column {
                    voices.forEach { (voiceKey, voiceDesc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setVoice(voiceKey)
                                    showVoicePicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedVoice == voiceKey,
                                onClick = {
                                    viewModel.setVoice(voiceKey)
                                    showVoicePicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(voiceKey, fontWeight = FontWeight.Bold)
                                Text(voiceDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoicePicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}
