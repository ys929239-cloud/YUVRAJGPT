package com.example.ui.chat

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.ChatMessage
import com.google.firebase.auth.FirebaseUser
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    toolName: String,
    viewModel: ChatViewModel,
    user: FirebaseUser? = null,
    onOpenDrawer: () -> Unit,
    onOpenAuth: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val feedbackMap by viewModel.feedbackMap.collectAsState()

    val searchResultsMessages by viewModel.searchResultsMessages.collectAsState()
    val searchResultsSessions by viewModel.searchResultsSessions.collectAsState()
    
    var textState by remember { mutableStateOf("") }
    var feedbackDialogMessageId by remember { mutableStateOf<String?>(null) }
    
    // In-chat search filter
    var isInChatSearchActive by remember { mutableStateOf(false) }
    var inChatSearchQuery by remember { mutableStateOf("") }
    
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
                viewModel.onSearchQueryChanged(textState)
            }
        }
    }

    if (feedbackDialogMessageId != null) {
        FeedbackDialog(
            messageId = feedbackDialogMessageId!!,
            onDismissRequest = { feedbackDialogMessageId = null },
            onSubmitFeedback = { reason, comment ->
                viewModel.submitFeedback(
                    messageId = feedbackDialogMessageId!!,
                    isPositive = false,
                    reason = reason,
                    comment = comment
                )
                Toast.makeText(context, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
                feedbackDialogMessageId = null
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "YUVRAJGPT",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.testTag("app_title_header")
                            )
                            if (toolName != "AI Chat") {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = toolName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Powered by Yuvraj",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("menu_drawer_button")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    // In-chat search icon toggle (if inside chat)
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                isInChatSearchActive = !isInChatSearchActive
                                if (!isInChatSearchActive) inChatSearchQuery = ""
                            },
                            modifier = Modifier.testTag("toggle_in_chat_search")
                        ) {
                            Icon(
                                imageVector = if (isInChatSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search in chat",
                                tint = if (isInChatSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Account & Database Profile Button
                    IconButton(
                        onClick = onOpenAuth,
                        modifier = Modifier.testTag("profile_button")
                    ) {
                        if (user?.photoUrl != null) {
                            AsyncImage(
                                model = user.photoUrl,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (user != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (user.displayName?.firstOrNull() ?: 'U').toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "Y",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
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
            // Optional In-Chat Search Bar (collapsible)
            AnimatedVisibility(
                visible = isInChatSearchActive && messages.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = inChatSearchQuery,
                            onValueChange = { inChatSearchQuery = it },
                            placeholder = { Text("Search this chat...", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                        if (inChatSearchQuery.isNotBlank()) {
                            IconButton(onClick = { inChatSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Simple, Clean Empty State (Home Screen)
            if (messages.isEmpty() && !isLoading && error == null) {
                val scrollState = rememberScrollState()
                val userName = user?.displayName?.split(" ")?.firstOrNull() ?: "there"

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Clean Simple Emblem
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(64.dp)
                                .shadow(4.dp, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Hello, $userName",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Powered by Yuvraj",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "What can I help you with today?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(32.dp))

                        // ==========================================
                        // SIMPLE & CLEAN SEARCH BAR IN THE MIDDLE
                        // ==========================================
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.5.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            ),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("middle_search_bar_container")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(Modifier.width(8.dp))

                                OutlinedTextField(
                                    value = textState,
                                    onValueChange = {
                                        textState = it
                                        viewModel.onSearchQueryChanged(it)
                                    },
                                    placeholder = {
                                        Text(
                                            "Ask YUVRAJGPT or search...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("middle_search_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )

                                // Voice input
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to YUVRAJGPT...")
                                        }
                                        try {
                                            speechRecognizerLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Voice input not available", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Voice Input",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Send / Search Action Button
                                Surface(
                                    shape = CircleShape,
                                    color = if (textState.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = textState.isNotBlank()) {
                                            val query = textState
                                            textState = ""
                                            viewModel.clearSearch()
                                            viewModel.sendMessage(query)
                                        }
                                        .testTag("middle_search_submit_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Search or Send",
                                            tint = if (textState.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Search Matches Dropdown (Only appears when user types to search past chats)
                        if (textState.isNotBlank() && (searchResultsMessages.isNotEmpty() || searchResultsSessions.isNotEmpty())) {
                            Spacer(Modifier.height(10.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_results_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Found in past chats:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(6.dp))

                                    searchResultsSessions.take(3).forEach { session ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    viewModel.loadSession(session.id)
                                                    viewModel.clearSearch()
                                                    textState = ""
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = session.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    searchResultsMessages.take(3).forEach { msg ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    viewModel.loadSession(msg.sessionId)
                                                    viewModel.clearSearch()
                                                    textState = ""
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Conversation View
                val displayedMessages = remember(messages, inChatSearchQuery) {
                    if (inChatSearchQuery.isBlank()) {
                        messages
                    } else {
                        messages.filter { it.text.contains(inChatSearchQuery, ignoreCase = true) }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (inChatSearchQuery.isNotBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Showing ${displayedMessages.size} matching message(s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    items(displayedMessages) { message ->
                        ChatMessageItem(
                            message = message,
                            feedbackState = feedbackMap[message.id],
                            onThumbsUp = {
                                viewModel.submitFeedback(message.id, true)
                                Toast.makeText(context, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
                            },
                            onThumbsDown = {
                                feedbackDialogMessageId = message.id
                            },
                            onRegenerate = {
                                viewModel.regenerateLastResponse(message)
                            },
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
                                Row(
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Thinking...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    if (error != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = error!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Simple Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = {
                        textState = it
                        if (messages.isEmpty()) {
                            viewModel.onSearchQueryChanged(it)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_textfield"),
                    placeholder = { Text("Ask YUVRAJGPT...") },
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                                Toast.makeText(context, "Voice input not available", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.width(10.dp))
                
                // Send Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (textState.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable(enabled = textState.isNotBlank()) {
                            val query = textState
                            textState = ""
                            viewModel.clearSearch()
                            viewModel.sendMessage(query)
                        }
                        .testTag("chat_send_button"),
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
fun ChatMessageItem(
    message: ChatMessage,
    feedbackState: Boolean? = null,
    onThumbsUp: () -> Unit = {},
    onThumbsDown: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onPlayHumanVoice: (String, (String?) -> Unit) -> Unit = { _, _ -> }
) {
    val isUser = message.isUser
    val isAudio = message.text.startsWith("[AUDIO_BASE64] ")
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Clean AI Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isAudio) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val base64 = message.text.removePrefix("[AUDIO_BASE64] ")
                            try {
                                val decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                val tempFile = File.createTempFile("audio", ".mp3", context.cacheDir)
                                FileOutputStream(tempFile).use { it.write(decoded) }
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
                            text = "Generated Music Audio",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = message.text,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Clean Feedback & Action Toolbar (Only for AI responses)
            if (!isUser && !isAudio) {
                var isPlayingTts by remember { mutableStateOf(false) }
                var isLoadingTts by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, start = 2.dp)
                        .height(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbs Up Feedback
                    IconButton(
                        onClick = onThumbsUp,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (feedbackState == true) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Good response",
                            tint = if (feedbackState == true) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Thumbs Down Feedback
                    IconButton(
                        onClick = onThumbsDown,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (feedbackState == false) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Bad response",
                            tint = if (feedbackState == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Copy Response Text
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("YUVRAJGPT Response", message.text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Read Aloud / TTS
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
                                            val tempFile = File.createTempFile("tts", ".wav", context.cacheDir)
                                            FileOutputStream(tempFile).use { it.write(finalData) }
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
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        if (isLoadingTts) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        } else {
                            Icon(
                                if (isPlayingTts) Icons.Default.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read Aloud",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Regenerate Response
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Regenerate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
