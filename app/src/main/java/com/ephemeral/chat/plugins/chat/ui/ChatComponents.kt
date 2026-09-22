package com.ephemeral.chat.plugins.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ChatViewModel
import com.ephemeral.chat.plugins.storage.entity.MemberEntity
import com.ephemeral.chat.plugins.storage.entity.MessageEntity
import com.ephemeral.chat.plugins.storage.entity.MessageType

/**
 * 聊天相关可复用组件。
 */

/**
 * 连接状态条。
 */
@Composable
fun ConnectionStatusBar(status: ChatViewModel.ConnectionStatus) {
    val (text, color) = when (status) {
        ChatViewModel.ConnectionStatus.Scanning -> "扫描中..." to MaterialTheme.colorScheme.secondary
        ChatViewModel.ConnectionStatus.Connecting -> "连接中..." to MaterialTheme.colorScheme.secondary
        ChatViewModel.ConnectionStatus.Reconnecting -> "重新连接中..." to MaterialTheme.colorScheme.secondary
        ChatViewModel.ConnectionStatus.Connected -> "" to Color.Transparent
        ChatViewModel.ConnectionStatus.Disconnected -> "" to Color.Transparent
    }
    if (text.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.1f))
                .padding(adaptiveDp(8f)),
        ) {
            Text(
                text = text,
                fontSize = adaptiveSp(12f),
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 邀请码展示组件。
 */
@Composable
fun InviteCodeDisplay(code: String) {
    Text(
        text = code,
        fontSize = adaptiveSp(48f),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(adaptiveDp(16f)),
    )
}
