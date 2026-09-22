package com.ephemeral.chat.plugins.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ChatViewModel

/**
 * 首页——创建/加入群聊入口。
 */
@Composable
fun CreateGroupScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.inviteCode.isEmpty()) {
            // 创建群聊入口
            Text(
                text = "EphemeralChat",
                fontSize = adaptiveSp(28f),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(16f)))
            Text(
                text = "临时群聊，在场即在场，离场即消散",
                fontSize = adaptiveSp(14f),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(32f)))
            Button(onClick = { viewModel.createGroup() }) {
                Text("创建群聊", fontSize = adaptiveSp(16f))
            }
            Spacer(modifier = Modifier.height(adaptiveDp(16f)))
            Button(onClick = { viewModel.uiState.value.let { /* navigate to join */ } }) {
                Text("加入群聊", fontSize = adaptiveSp(16f))
            }
        } else {
            // 显示邀请码
            Text(
                text = "群聊邀请码",
                fontSize = adaptiveSp(14f),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(16f)))
            Text(
                text = state.inviteCode,
                fontSize = adaptiveSp(48f),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(16f)))
            Text(
                text = "等待加入...",
                fontSize = adaptiveSp(14f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(32f)))
            if (state.connectionStatus == ChatViewModel.ConnectionStatus.Connected) {
                Button(onClick = { viewModel.backToHome() }) {
                    Text("进入群聊", fontSize = adaptiveSp(16f))
                }
            }
        }
    }
}
