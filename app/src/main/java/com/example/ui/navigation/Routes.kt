package com.example.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CHAT = "chat/{tool}"
    const val IMAGE = "image"
    
    fun createChatRoute(tool: String) = "chat/$tool"
}
