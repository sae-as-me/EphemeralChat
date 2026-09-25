package com.ephemeral.chat.plugins.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ChatViewModel

/**
 * 加入群聊界面。
 * 4 位数字输入框 + 加入按钮 + 扫描状态 + 返回。
 */
@Composable
fun JoinGroupScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateLifecycle()
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "输入邀请码",
            fontSize = adaptiveSp(18f),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        OutlinedTextField(
            value = code,
            onValueChange = { newValue ->
                if (newValue.length <= 4 && newValue.all { c -> c.isDigit() }) code = newValue
            },
            modifier = Modifier.width(adaptiveDp(200f)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("0000", fontSize = adaptiveSp(24f)) },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        Button(
            onClick = { viewModel.joinGroup(code) },
            enabled = code.length == 4,
        ) {
            Text("加入群聊", fontSize = adaptiveSp(16f))
        }

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        when (state.connectionStatus) {
            ChatViewModel.ConnectionStatus.Scanning -> {
                Text(
                    text = "正在扫描附近群聊...",
                    fontSize = adaptiveSp(14f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChatViewModel.ConnectionStatus.Connecting -> {
                Text(
                    text = "正在连接...",
                    fontSize = adaptiveSp(14f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChatViewModel.ConnectionStatus.Connected -> {
                Text(
                    text = "已连接",
                    fontSize = adaptiveSp(14f),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            else -> {}
        }

        state.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
            Text(
                text = error,
                fontSize = adaptiveSp(14f),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        // 返回首页
        TextButton(onClick = { viewModel.backToHome() }) {
            Text("返回", fontSize = adaptiveSp(14f))
        }
    }
}
