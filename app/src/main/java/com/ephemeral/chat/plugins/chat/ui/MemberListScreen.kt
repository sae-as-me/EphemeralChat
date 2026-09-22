package com.ephemeral.chat.plugins.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ChatViewModel
import com.ephemeral.chat.plugins.storage.entity.MemberEntity
import com.ephemeral.chat.plugins.storage.entity.MemberStatus

/**
 * 成员列表界面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberListScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("群成员 (${state.members.size})", fontSize = adaptiveSp(16f)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToChat() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(adaptiveDp(8f)),
            verticalArrangement = Arrangement.spacedBy(adaptiveDp(4f)),
        ) {
            items(state.members) { member ->
                MemberRow(member, isHub = state.isHub && member.isSelf)
            }
        }
    }
}

/**
 * 成员行组件。
 */
@Composable
fun MemberRow(member: MemberEntity, isHub: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(adaptiveDp(8f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像占位（圆形+首字）
        Box(
            modifier = Modifier
                .size(adaptiveDp(40f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.nickname.take(1),
                fontSize = adaptiveSp(16f),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.size(adaptiveDp(12f)))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (member.isSelf) "${member.nickname}（我）" else member.nickname,
                fontSize = adaptiveSp(14f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (member.status) {
                    MemberStatus.ACTIVE -> "在线"
                    MemberStatus.OFFLINE -> "离线"
                    MemberStatus.LEFT -> "已离开"
                },
                fontSize = adaptiveSp(12f),
                color = if (member.status == MemberStatus.ACTIVE)
                    Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Hub 标识
        if (isHub) {
            Text("Hub", fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.secondary)
        }

        // 状态图标（色盲友好：实心圆=在线，虚线=离线）
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(adaptiveDp(24f)),
            tint = if (member.status == MemberStatus.ACTIVE)
                Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
