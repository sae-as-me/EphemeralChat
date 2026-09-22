package com.ephemeral.chat.plugins.chat

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import com.ephemeral.chat.navigation.NavDestination
import com.ephemeral.chat.plugins.chat.ui.ChatScreen
import com.ephemeral.chat.plugins.chat.ui.CreateGroupScreen
import com.ephemeral.chat.plugins.chat.ui.JoinGroupScreen
import com.ephemeral.chat.plugins.chat.ui.MemberListScreen

/**
 * 聊天业务插件。
 * 注册群聊导航项，绑定 ChatViewModel + ChatScreen。
 * 依赖：ble, storage, crypto, lifecycle
 */
class ChatPlugin : IPlugin {
    override val pluginId = "chat"
    override val dependencies = listOf("ble", "storage", "crypto", "lifecycle")

    private val TAG = "ChatPlugin"

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        registry.registerNavDestination(NavDestination.Chat) {
            ChatNavContent()
        }
        Log.i(TAG, "聊天插件初始化完成")
    }

    override fun onStart() {}
    override fun onStop() {}
    override fun onDestroy() {}
}

/**
 * 聊天导航内容——根据 ChatUiState.screen 切换界面。
 */
@Composable
fun ChatNavContent() {
    val viewModel: ChatViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    when (state.screen) {
        ChatViewModel.Screen.Home -> CreateGroupScreen(viewModel)
        ChatViewModel.Screen.Create -> CreateGroupScreen(viewModel)
        ChatViewModel.Screen.Join -> JoinGroupScreen(viewModel)
        ChatViewModel.Screen.Chat -> ChatScreen(viewModel)
        ChatViewModel.Screen.Members -> MemberListScreen(viewModel)
    }
}
