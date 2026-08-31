package com.example.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.R
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.theme.ThemeViewModel

data class AiTool(val id: String, val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val TOOLS = listOf(
    AiTool("chat", "AI Chat", Icons.Default.Chat),
    AiTool("homework", "Homework Helper", Icons.Default.School),
    AiTool("youtube", "YouTube AI", Icons.Default.VideoLibrary),
    AiTool("live_voice", "Live Voice Chat", Icons.Default.Headphones),
    AiTool("music", "Music Generator", Icons.Default.MusicNote)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(themeViewModel: ThemeViewModel) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(context))
    val user by authViewModel.user.collectAsState()
    val isDarkMode by themeViewModel.isDarkMode.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var currentToolId by remember { mutableStateOf("chat") }
    
    val chatViewModel: ChatViewModel = viewModel(
        key = currentToolId,
        factory = ChatViewModel.Factory(currentToolId, context)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.yuvrajgpt_logo_1788103619536),
                        contentDescription = "Logo",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("YUVRAJGPT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(16.dp))

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
                    sessions.take(5).forEach { session ->
                        val currentSessionId by chatViewModel.currentSessionId.collectAsState()
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp)) },
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

                Divider(modifier = Modifier.padding(vertical = 8.dp))

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

                // Account Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (user == null) {
                                authViewModel.signIn()
                            } else {
                                authViewModel.signOut()
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = user?.displayName?.firstOrNull()?.toString() ?: "U",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = user?.displayName ?: "Sign In",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (user != null) {
                                Text(
                                    text = "Sign Out",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        val toolName = TOOLS.find { it.id == currentToolId }?.name ?: "YUVRAJGPT"
        
        ChatScreen(
            toolName = toolName,
            viewModel = chatViewModel,
            onOpenDrawer = {
                scope.launch { drawerState.open() }
            }
        )
    }
}
