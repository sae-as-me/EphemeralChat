package com.ephemeral.chat.plugins.chat

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import com.ephemeral.chat.navigation.NavDestination
import com.ephemeral.chat.plugins.chat.ui.ChatScreen
import com.ephemeral.chat.plugins.chat.ui.CreateGroupScreen
import com.ephemeral.chat.plugins.chat.ui.JoinGroupScreen
import com.ephemeral.chat.plugins.chat.ui.MemberListScreen
import com.ephemeral.chat.plugins.game.numberbomb.NumberBombPlugin
import com.ephemeral.chat.plugins.game.numberbomb.ui.NumberBombScreen
import com.ephemeral.chat.plugins.game.whospy.SpyPlugin
import com.ephemeral.chat.plugins.game.whospy.ui.SpyScreen
import com.ephemeral.chat.plugins.game.ui.GameListScreen
import androidx.compose.material3.Text

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
    // 群聊内双击返回退出计时
    var lastBackTime by remember { mutableStateOf(0L) }
    var exitGroupHint by remember { mutableStateOf(false) }

    // 修复：首页/创建/加入页不拦截返回键（交由 MainActivity 双击退出 App 处理）；
    // 子页面返回聊天页；聊天页双击返回退出群聊
    val inSubPage = state.screen in listOf(
        ChatViewModel.Screen.Members,
        ChatViewModel.Screen.GameList,
        ChatViewModel.Screen.NumberBomb,
        ChatViewModel.Screen.WhoIsSpy,
    )
    val inChat = state.screen == ChatViewModel.Screen.Chat

    BackHandler(enabled = inSubPage) {
        viewModel.backToChat()
    }

    // 聊天页：双击返回退出群聊
    BackHandler(enabled = inChat && state.groupId.isNotEmpty()) {
        val now = System.currentTimeMillis()
        if (now - lastBackTime < 2000) {
            viewModel.leaveGroup()
            lastBackTime = 0L
        } else {
            lastBackTime = now
            exitGroupHint = true
        }
    }

    if (exitGroupHint) {
        LaunchedEffect(lastBackTime) {
            kotlinx.coroutines.delay(2000)
            exitGroupHint = false
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                Text(
                    text = "再按一次返回键退出当前群聊",
                    fontSize = adaptiveSp(14f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }

    when (state.screen) {
        ChatViewModel.Screen.Home -> CreateGroupScreen(viewModel)
        ChatViewModel.Screen.Create -> CreateGroupScreen(viewModel)
        ChatViewModel.Screen.Join -> JoinGroupScreen(viewModel)
        ChatViewModel.Screen.Chat -> ChatScreen(viewModel)
        ChatViewModel.Screen.Members -> MemberListScreen(viewModel)
        ChatViewModel.Screen.GameList -> GameListScreen(
            onBack = { viewModel.backToChat() },
            onSelectNumberBomb = { viewModel.showNumberBomb() },
            onSelectWhoIsSpy = { viewModel.showWhoIsSpy() },
        )
        ChatViewModel.Screen.NumberBomb -> {
            // 获取 NumberBombPlugin 实例并绑定上下文
            val app = com.ephemeral.chat.EphemeralChatApplication.get()
            val plugin = app.pluginRegistry.getPlugin<NumberBombPlugin>("numberBomb")
            if (plugin != null) {
                // 修复：绑定实时成员查询而非进入页面时的快照（中途加入的成员也能参与）
                plugin.bindContext(
                    isHub = state.isHub,
                    myUuidShort = state.myUuidShort,
                    sendBroadcast = { act, data -> viewModel.sendGameBroadcast("numberBomb", act, data) },
                    sendToHub = { act, data -> viewModel.sendGameToHub("numberBomb", act, data) },
                    membersProvider = { viewModel.uiState.value.members.map { it.memberUuid to it.nickname } },
                )
                plugin.sendInvite = { gameId, gameName -> viewModel.sendGameInvite(gameId, gameName) }
                plugin.sendSystemMessage = { content -> viewModel.sendSystemMessage(content) }
                NumberBombScreen(
                    plugin = plugin,
                    onBack = { viewModel.backToChat() },
                )
            } else {
                Text("游戏插件未加载", fontSize = adaptiveSp(16f))
            }
        }
        ChatViewModel.Screen.WhoIsSpy -> {
            val app = com.ephemeral.chat.EphemeralChatApplication.get()
            val plugin = app.pluginRegistry.getPlugin<SpyPlugin>("whoIsSpy")
            if (plugin != null) {
                plugin.bindContext(
                    isHub = state.isHub,
                    myUuidShort = state.myUuidShort,
                    sendBroadcast = { act, data -> viewModel.sendGameBroadcast("whoIsSpy", act, data) },
                    sendToHub = { act, data -> viewModel.sendGameToHub("whoIsSpy", act, data) },
                    sendPrivate = { act, targetUuid, data -> viewModel.sendGamePrivate("whoIsSpy", act, targetUuid, data) },
                    membersProvider = { viewModel.uiState.value.members.map { it.memberUuid to it.nickname } },
                )
                SpyScreen(
                    plugin = plugin,
                    onBack = { viewModel.backToChat() },
                )
            } else {
                Text("游戏插件未加载", fontSize = adaptiveSp(16f))
            }
        }
    }
}
