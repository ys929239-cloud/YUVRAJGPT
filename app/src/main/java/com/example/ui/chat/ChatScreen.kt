package com.example.ui.chat

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.example.R
import com.example.data.db.ChatMessage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    toolName: String,
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    var showLiveVoiceMode by remember { mutableStateOf(false) }
    var textState by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                textState = if (textState.isEmpty()) recognizedText else "$textState $recognizedText"
            }
        }
    }

    if (showLiveVoiceMode) {
        LiveVoiceMode(
            viewModel = viewModel,
            onClose = { showLiveVoiceMode = false }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = toolName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { showLiveVoiceMode = true }) {
                        Icon(Icons.Default.Headphones, contentDescription = "Live Voice Mode")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty() && !isLoading && error == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.yuvrajgpt_logo_1788103619536),
                            contentDescription = "Logo",
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "How can I help you today?",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        ChatMessageItem(
                            message = message,
                            onPlayHumanVoice = { text, callback ->
                                viewModel.generateSpeech(text, { callback(it) }, { callback(null) })
                            }
                        )
                    }
                    
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    if (error != null) {
                        item {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                    maxLines = 5,
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                // Ignore or show toast if no speech recognizer exists
                            }
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (textState.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable(enabled = textState.isNotBlank()) {
                            viewModel.sendMessage(textState)
                            textState = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (textState.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage, onPlayHumanVoice: (String, (String?) -> Unit) -> Unit = { _, _ -> }) {
    val isUser = message.isUser
    val isAudio = message.text.startsWith("[AUDIO_BASE64] ")
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // AI Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.yuvrajgpt_logo_1788103619536),
                    contentDescription = "AI",
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = if (isUser) 16.dp else 0.dp, vertical = if (isUser) 12.dp else 4.dp)
        ) {
            if (isAudio) {
                Row(
                    modifier = Modifier
                        .background(if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val base64 = message.text.removePrefix("[AUDIO_BASE64] ")
                        try {
                            val decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                            val tempFile = java.io.File.createTempFile("audio", ".mp3", context.cacheDir)
                            java.io.FileOutputStream(tempFile).use { it.write(decoded) }
                            val mediaPlayer = android.media.MediaPlayer().apply {
                                setDataSource(tempFile.absolutePath)
                                prepare()
                                start()
                            }
                            isPlaying = true
                            mediaPlayer.setOnCompletionListener {
                                isPlaying = false
                                it.release()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play Audio", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Audio Track",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                var isPlayingTts by remember { mutableStateOf(false) }
                var isLoadingTts by remember { mutableStateOf(false) }
                
                Column {
                    Text(
                        text = message.text,
                        color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!isUser) {
                        if (isLoadingTts) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(24.dp)
                                    .padding(4.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (!isPlayingTts) {
                                        isLoadingTts = true
                                        onPlayHumanVoice(message.text) { result ->
                                            isLoadingTts = false
                                            if (result != null && !result.startsWith("ERROR:")) {
                                                try {
                                                    val decoded = android.util.Base64.decode(result, android.util.Base64.DEFAULT)
                                                    
                                                    val finalData = if (decoded.size > 4 && decoded[0].toInt().toChar() == 'R' && decoded[1].toInt().toChar() == 'I') {
                                                        decoded
                                                    } else {
                                                        val pcmDataLength = decoded.size
                                                        val channels = 1
                                                        val sampleRate = 24000
                                                        val bitsPerSample = 16
                                                        val byteRate = sampleRate * channels * bitsPerSample / 8

                                                        val header = ByteArray(44)
                                                        header[0] = 'R'.code.toByte()
                                                        header[1] = 'I'.code.toByte()
                                                        header[2] = 'F'.code.toByte()
                                                        header[3] = 'F'.code.toByte()
                                                        
                                                        val totalDataLen = pcmDataLength + 36
                                                        header[4] = (totalDataLen and 0xff).toByte()
                                                        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
                                                        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
                                                        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
                                                        
                                                        header[8] = 'W'.code.toByte()
                                                        header[9] = 'A'.code.toByte()
                                                        header[10] = 'V'.code.toByte()
                                                        header[11] = 'E'.code.toByte()
                                                        
                                                        header[12] = 'f'.code.toByte()
                                                        header[13] = 'm'.code.toByte()
                                                        header[14] = 't'.code.toByte()
                                                        header[15] = ' '.code.toByte()
                                                        
                                                        header[16] = 16
                                                        header[17] = 0
                                                        header[18] = 0
                                                        header[19] = 0
                                                        header[20] = 1
                                                        header[21] = 0
                                                        header[22] = channels.toByte()
                                                        header[23] = 0
                                                        header[24] = (sampleRate and 0xff).toByte()
                                                        header[25] = ((sampleRate shr 8) and 0xff).toByte()
                                                        header[26] = ((sampleRate shr 16) and 0xff).toByte()
                                                        header[27] = ((sampleRate shr 24) and 0xff).toByte()
                                                        header[28] = (byteRate and 0xff).toByte()
                                                        header[29] = ((byteRate shr 8) and 0xff).toByte()
                                                        header[30] = ((byteRate shr 16) and 0xff).toByte()
                                                        header[31] = ((byteRate shr 24) and 0xff).toByte()
                                                        header[32] = (channels * bitsPerSample / 8).toByte()
                                                        header[33] = 0
                                                        header[34] = bitsPerSample.toByte()
                                                        header[35] = 0
                                                        
                                                        header[36] = 'd'.code.toByte()
                                                        header[37] = 'a'.code.toByte()
                                                        header[38] = 't'.code.toByte()
                                                        header[39] = 'a'.code.toByte()
                                                        
                                                        header[40] = (pcmDataLength and 0xff).toByte()
                                                        header[41] = ((pcmDataLength shr 8) and 0xff).toByte()
                                                        header[42] = ((pcmDataLength shr 16) and 0xff).toByte()
                                                        header[43] = ((pcmDataLength shr 24) and 0xff).toByte()
                                                        
                                                        header + decoded
                                                    }
                                                    
                                                    val tempFile = java.io.File.createTempFile("tts", ".wav", context.cacheDir)
                                                    java.io.FileOutputStream(tempFile).use { it.write(finalData) }
                                                    val mediaPlayer = android.media.MediaPlayer().apply {
                                                        setDataSource(tempFile.absolutePath)
                                                        prepare()
                                                        start()
                                                    }
                                                    isPlayingTts = true
                                                    mediaPlayer.setOnCompletionListener {
                                                        isPlayingTts = false
                                                        it.release()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                android.widget.Toast.makeText(context, result?.removePrefix("ERROR:") ?: "Failed to generate speech", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = if (isPlayingTts) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), 
                                    contentDescription = "Read Aloud", 
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
