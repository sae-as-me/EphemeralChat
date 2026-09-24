package com.ephemeral.chat.plugins.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ChatViewModel

/**
 * 首页——创建/加入群聊入口。
 * - 无活跃群聊时显示创建/加入按钮
 * - 有活跃群聊（返回但不退出）时显示群聊列表卡片
 * - inviteCode 非空且为创建者时显示邀请码等待页
 */
@Composable
fun CreateGroupScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.inviteCode.isEmpty() && state.groupId.isEmpty()) {
            // ---- 首页：无活跃群聊，创建/加入入口 ----
            HomeEntry(viewModel, state.nickname)
        } else if (state.inviteCode.isNotEmpty() && state.groupId.isNotEmpty()) {
            // ---- 创建者等待页：显示邀请码 ----
            CreateWaitScreen(viewModel = viewModel, inviteCode = state.inviteCode)
        } else {
            // ---- 首页：有活跃群聊（返回但不退出），显示群聊列表 ----
            ActiveGroupList(viewModel = viewModel)
        }
    }
}

/**
 * 首页入口：创建/加入。
 */
@Composable
private fun HomeEntry(viewModel: ChatViewModel, nickname: String) {
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
    Spacer(modifier = Modifier.height(adaptiveDp(8f)))
    Text(
        text = "当前昵称: $nickname",
        fontSize = adaptiveSp(12f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(adaptiveDp(32f)))
    Button(onClick = { viewModel.createGroup() }) {
        Text("创建群聊", fontSize = adaptiveSp(16f))
    }
    Spacer(modifier = Modifier.height(adaptiveDp(16f)))
    Button(onClick = { viewModel.navigateToJoin() }) {
        Text("加入群聊", fontSize = adaptiveSp(16f))
    }
}

/**
 * 创建者等待页：显示邀请码。
 */
@Composable
private fun CreateWaitScreen(viewModel: ChatViewModel, inviteCode: String) {
    Text(
        text = "群聊邀请码",
        fontSize = adaptiveSp(14f),
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(adaptiveDp(16f)))
    Text(
        text = inviteCode,
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
    // 创建者可直接进入聊天界面
    Button(onClick = { viewModel.enterChat() }) {
        Text("进入群聊", fontSize = adaptiveSp(16f))
    }
    Spacer(modifier = Modifier.height(adaptiveDp(16f)))
    // 返回但不退出
    Button(onClick = { viewModel.backToHomeKeepGroup() }) {
        Text("返回", fontSize = adaptiveSp(14f))
    }
}

/**
 * 活跃群聊列表（返回但不退出后的首页）。
 */
@Composable
private fun ActiveGroupList(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateLifecycle()

    Text(
        text = "我的群聊",
        fontSize = adaptiveSp(18f),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(adaptiveDp(16f)))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(adaptiveDp(16f)))
            .clickable { viewModel.reenterGroup() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(adaptiveDp(16f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = adaptiveDp(12f)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "群聊 ${state.inviteCode}",
                    fontSize = adaptiveSp(16f),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(adaptiveDp(4f)))
                Text(
                    text = if (state.isHub) "我是群主（Hub）" else "群成员",
                    fontSize = adaptiveSp(12f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "进入",
                fontSize = adaptiveSp(14f),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    Spacer(modifier = Modifier.height(adaptiveDp(32f)))

    // 其他操作：创建新群聊 / 加入新群聊 / 彻底退出当前群聊
    Button(onClick = { viewModel.createGroup() }) {
        Text("创建新群聊", fontSize = adaptiveSp(16f))
    }
    Spacer(modifier = Modifier.height(adaptiveDp(12f)))
    Button(onClick = { viewModel.navigateToJoin() }) {
        Text("加入新群聊", fontSize = adaptiveSp(16f))
    }
    Spacer(modifier = Modifier.height(adaptiveDp(12f)))
    Button(onClick = { viewModel.leaveGroup() }) {
        Text("退出当前群聊", fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.secondary)
    }
}