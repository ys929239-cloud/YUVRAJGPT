package com.example.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.auth.AuthDialog
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch

data class AiTool(val id: String, val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val TOOLS = listOf(
    AiTool("chat", "AI Chat", Icons.AutoMirrored.Filled.Chat),
    AiTool("homework", "Homework Helper", Icons.Default.School),
    AiTool("youtube", "YouTube AI", Icons.Default.VideoLibrary),
    AiTool("music", "Music Generator", Icons.Default.MusicNote)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(themeViewModel: ThemeViewModel) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(context))
    val user by authViewModel.user.collectAsState()
    val isDarkMode by themeViewModel.isDarkMode.collectAsState()
    val syncStatus by authViewModel.syncStatus.collectAsState()
    val isSyncing by authViewModel.isSyncing.collectAsState()

    var showAuthDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var currentToolId by remember { mutableStateOf("chat") }
    
    val chatViewModel: ChatViewModel = viewModel(
        key = currentToolId,
        factory = ChatViewModel.Factory(currentToolId, context)
    )

    if (showAuthDialog) {
        AuthDialog(
            authViewModel = authViewModel,
            onDismissRequest = { showAuthDialog = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.yuvrajgpt_logo_1788103619536),
                        contentDescription = "Logo",
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("YUVRAJGPT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Powered by Yuvraj", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("New Chat") },
                    selected = false,
                    onClick = {
                        chatViewModel.startNewSession()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                val sessions by chatViewModel.sessions.collectAsState()
                if (sessions.isNotEmpty()) {
                    Text(
                        "Recent Sessions", 
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    sessions.take(4).forEach { session ->
                        val currentSessionId by chatViewModel.currentSessionId.collectAsState()
                        NavigationDrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(session.title, maxLines = 1) },
                            selected = session.id == currentSessionId,
                            onClick = {
                                chatViewModel.loadSession(session.id)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                Text(
                    "Tools", 
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TOOLS.forEach { tool ->
                    NavigationDrawerItem(
                        icon = { Icon(tool.icon, contentDescription = null) },
                        label = { Text(tool.name) },
                        selected = currentToolId == tool.id,
                        onClick = {
                            currentToolId = tool.id
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Database & Cloud Status Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { showAuthDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (user != null) Icons.Default.CloudDone else Icons.Default.Storage,
                            contentDescription = "Database",
                            tint = if (user != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (user != null) "Cloud Firestore" else "Local Database",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (user != null) syncStatus else "Tap to sign in & sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (user != null && isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                NavigationDrawerItem(
                    icon = { 
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null
                        ) 
                    },
                    label = { Text(if (isDarkMode) "Light Mode" else "Dark Mode") },
                    selected = false,
                    onClick = { themeViewModel.toggleTheme() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // Account Section with Google Sign-In affordance
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAuthDialog = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (user?.photoUrl != null) {
                            AsyncImage(
                                model = user!!.photoUrl,
                                contentDescription = "Profile Photo",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = if (user != null) MaterialTheme.colorScheme.primaryContainer else Color.White,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (user != null) {
                                        Text(
                                            text = (user?.displayName?.firstOrNull() ?: 'U').toString(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        Text(
                                            "G",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4285F4),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user?.displayName ?: "Google Sign-In",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (user != null) (user?.email ?: "Account Connected") else "Tap to backup chats",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    ) {
        val toolName = TOOLS.find { it.id == currentToolId }?.name ?: "YUVRAJGPT"
        
        ChatScreen(
            toolName = toolName,
            viewModel = chatViewModel,
            user = user,
            onOpenDrawer = {
                scope.launch { drawerState.open() }
            },
            onOpenAuth = {
                showAuthDialog = true
            }
        )
    }
}
