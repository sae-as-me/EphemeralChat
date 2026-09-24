package com.ephemeral.chat.plugins.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ChatViewModel
import com.ephemeral.chat.plugins.storage.entity.MessageEntity
import com.ephemeral.chat.plugins.storage.entity.MessageType

/**
 * 聊天主界面。
 * 右上角为"更多"菜单：
 * - 返回但不退出：回到首页，群聊保留在列表
 * - 退出当前群聊：二次确认后退出
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "邀请码: ${state.inviteCode}",
                            fontSize = adaptiveSp(16f),
                        )
                        Spacer(modifier = Modifier.width(adaptiveDp(8f)))
                        Text(
                            text = "(${state.members.size}人)",
                            fontSize = adaptiveSp(14f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // 成员列表
                    IconButton(onClick = { viewModel.showMembers() }) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = "成员列表",
                            modifier = Modifier.size(adaptiveDp(24f)),
                        )
                    }
                    // 更多菜单（竖排省略号）
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "更多",
                                modifier = Modifier.size(adaptiveDp(24f)),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("返回但不退出", fontSize = adaptiveSp(14f)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.backToHomeKeepGroup()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(adaptiveDp(20f)),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("退出当前群聊", fontSize = adaptiveSp(14f)) },
                                onClick = {
                                    menuExpanded = false
                                    showExitDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(adaptiveDp(20f)),
                                    )
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(adaptiveDp(8f)),
                verticalArrangement = Arrangement.spacedBy(adaptiveDp(4f)),
            ) {
                items(state.messages) { msg ->
                    MessageBubble(msg, msg.senderUuid == state.myUuidShort)
                }
            }

            // 底部输入栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(adaptiveDp(8f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var inputText by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息", fontSize = adaptiveSp(14f)) },
                )
                Spacer(modifier = Modifier.width(adaptiveDp(8f)))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        modifier = Modifier.size(adaptiveDp(24f)),
                    )
                }
            }
        }
    }

    // 退出群聊二次确认对话框
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出当前群聊", fontSize = adaptiveSp(16f)) },
            text = {
                Text(
                    "确定要退出当前群聊吗？退出后将清除本地聊天记录，如需再次加入需重新输入邀请码。",
                    fontSize = adaptiveSp(14f),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.leaveGroup()
                }) {
                    Text("退出", fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("取消", fontSize = adaptiveSp(14f))
                }
            },
        )
    }
}

/**
 * 消息气泡组件。
 */
@Composable
fun MessageBubble(message: MessageEntity, isMine: Boolean) {
    val bgColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    if (message.type == MessageType.SYSTEM) {
        Text(
            text = message.content,
            fontSize = adaptiveSp(12f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(adaptiveDp(4f)),
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(adaptiveDp(12f)))
                    .background(bgColor)
                    .padding(adaptiveDp(12f))
                    .width(adaptiveDp(240f)),
            ) {
                if (!isMine) {
                    Text(
                        text = message.senderName,
                        fontSize = adaptiveSp(12f),
                        color = textColor.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(adaptiveDp(2f)))
                }
                Text(
                    text = message.content,
                    fontSize = adaptiveSp(14f),
                    color = textColor,
                )
            }
        }
    }
}