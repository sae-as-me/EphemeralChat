package com.ephemeral.chat.plugins.game.whospy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.ephemeral.chat.plugins.chat.ui.collectAsStateLifecycle
import com.ephemeral.chat.plugins.game.whospy.SpyPlugin

/**
 * 谁是卧底 UI 入口——根据游戏阶段显示不同页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpyScreen(
    plugin: SpyPlugin,
    onBack: () -> Unit,
) {
    val state by plugin.uiState.collectAsStateLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("谁是卧底", fontSize = adaptiveSp(16f)) },
                navigationIcon = {
                    IconButton(onClick = {
                        plugin.resetGame()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (state.phase) {
                SpyPlugin.GamePhase.IDLE -> StartPage(plugin, state)
                SpyPlugin.GamePhase.WAITING_DEAL -> WaitingDealPage()
                SpyPlugin.GamePhase.DESCRIBE -> DescribePage(plugin, state)
                SpyPlugin.GamePhase.VOTING -> VotingPage(plugin, state)
                SpyPlugin.GamePhase.VOTE_RESULT -> VoteResultPage(plugin, state)
                SpyPlugin.GamePhase.GAME_END -> GameEndPage(plugin, state)
            }
        }
    }
}

// ---- 发起页 ----

@Composable
private fun StartPage(plugin: SpyPlugin, state: SpyPlugin.SpyUiState) {
    var enableWhiteBlank by remember { mutableStateOf(false) }
    val memberCount = state.activePlayers.size.let { if (it == 0 && state.myUuid.isNotEmpty()) 1 else it }

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("谁是卧底", fontSize = adaptiveSp(24f), color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        Text(
            text = "游戏规则：\n" +
                "• 大多数人拿到相同词语（平民），少数人拿到相近词语（卧底）\n" +
                "• 每轮依次描述自己的词语，然后投票淘汰一人\n" +
                "• 你不知道自己的身份，游戏结束后公布\n" +
                "• 卧底全部出局→平民胜；卧底存活到人数持平→卧底胜",
            fontSize = adaptiveSp(12f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = adaptiveSp(18f),
        )

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        // 白板开关（8+人时提示）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("白板角色", fontSize = adaptiveSp(14f))
                Text(
                    "8+人时可开启，白板看不到任何词语",
                    fontSize = adaptiveSp(11f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enableWhiteBlank,
                onCheckedChange = { enableWhiteBlank = it },
            )
        }

        state.errorMessage?.let {
            Spacer(modifier = Modifier.height(adaptiveDp(12f)))
            Text(it, fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(adaptiveDp(32f)))

        Button(
            onClick = { plugin.startGame(enableWhiteBlank) },
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Text("开始游戏", fontSize = adaptiveSp(16f))
        }
    }
}

// ---- 等待发牌页 ----

@Composable
private fun WaitingDealPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("等待分配词语...", fontSize = adaptiveSp(16f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- 描述页 ----

@Composable
private fun DescribePage(plugin: SpyPlugin, state: SpyPlugin.SpyUiState) {
    var descInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 你的词语
        Text("你的词语", fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(adaptiveDp(8f)))
        Text(
            text = if (state.myWord.isEmpty()) "???（你是白板）" else state.myWord,
            fontSize = adaptiveSp(32f),
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        // 轮次和发言方向
        Text(
            text = "第 ${state.round} 轮 | 起始发言者: ${state.activePlayers.firstOrNull { it.first == state.firstSpeaker }?.second ?: "随机"} | ${if (state.direction == "cw") "顺时针" else "逆时针"}",
            fontSize = adaptiveSp(12f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // 描述列表
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(adaptiveDp(8f)),
        ) {
            items(state.descriptions) { (playerUuid, text) ->
                val playerName = state.activePlayers.firstOrNull { it.first == playerUuid }?.second ?: "玩家"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(adaptiveDp(12f))) {
                        Text(playerName, fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(adaptiveDp(4f)))
                        Text(text, fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // 描述输入框（所有人随时可输入）
        if (state.activePlayers.any { it.first == state.myUuid }) {
            OutlinedTextField(
                value = descInput,
                onValueChange = { descInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入你的描述", fontSize = adaptiveSp(14f)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
            Button(
                onClick = {
                    if (descInput.isNotBlank()) {
                        plugin.submitDescription(descInput)
                        descInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = descInput.isNotBlank(),
            ) {
                Text("提交描述", fontSize = adaptiveSp(14f))
            }
        }

        state.errorMessage?.let {
            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
            Text(it, fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.error)
        }
    }
}

// ---- 投票页 ----

@Composable
private fun VotingPage(plugin: SpyPlugin, state: SpyPlugin.SpyUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("投票阶段", fontSize = adaptiveSp(18f), color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(adaptiveDp(8f)))
        Text(
            "已投票 ${state.votedCount}/${state.totalVoters}",
            fontSize = adaptiveSp(12f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // 投票列表
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(adaptiveDp(8f)),
        ) {
            items(state.activePlayers) { (uuid, name) ->
                val isMe = uuid == state.myUuid
                val isMyVote = state.myVote == uuid
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { plugin.submitVote(uuid) },
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isMyVote -> MaterialTheme.colorScheme.primaryContainer
                            isMe -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(adaptiveDp(12f)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name + if (isMe) "（我）" else "",
                            fontSize = adaptiveSp(14f),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isMyVote) {
                            Text("✓", fontSize = adaptiveSp(16f), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(adaptiveDp(12f)))

        // 弃权按钮
        OutlinedButton(
            onClick = { plugin.submitVote("") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("弃权", fontSize = adaptiveSp(14f))
        }
    }
}

// ---- 投票结果页（淘汰通知） ----

@Composable
private fun VoteResultPage(plugin: SpyPlugin, state: SpyPlugin.SpyUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.lastEliminated.isNotEmpty()) {
            Text(
                "${state.lastEliminatedName} 淘汰",
                fontSize = adaptiveSp(24f),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
            Text("游戏继续", fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("平票", fontSize = adaptiveSp(24f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
            Text("本轮不淘汰，游戏继续", fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- 游戏结束页 ----

@Composable
private fun GameEndPage(plugin: SpyPlugin, state: SpyPlugin.SpyUiState) {
    val winnerText = when (state.winner) {
        "civilian" -> "平民胜利！"
        "spy" -> "卧底胜利！"
        "white_blank" -> "白板胜利！"
        else -> "游戏结束"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("游戏结束", fontSize = adaptiveSp(20f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(adaptiveDp(8f)))
        Text(winnerText, fontSize = adaptiveSp(28f), color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        // 公布所有人身份
        Text("身份揭晓", fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(adaptiveDp(12f)))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(adaptiveDp(8f)),
        ) {
            items(state.reveals) { (playerInfo, word, role) ->
                // playerInfo 格式: "uuid|name"
                val parts = playerInfo.split("|", limit = 2)
                val uuid = parts.getOrElse(0) { "" }
                val playerName = parts.getOrElse(1) { "玩家" }
                val roleName = when (role) {
                    "civilian" -> "平民"
                    "spy" -> "卧底"
                    "white_blank" -> "白板"
                    else -> "未知"
                }
                val roleColor = when (role) {
                    "spy" -> MaterialTheme.colorScheme.error
                    "white_blank" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(adaptiveDp(12f)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(playerName, fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.onSurface)
                            Text("词语: $word", fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(roleName, fontSize = adaptiveSp(14f), color = roleColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // 白板胜利按钮（仅 Hub 且存在白板）
        if (state.isHub && state.winner == "civilian" && state.hasWhiteBlank) {
            Button(
                onClick = { plugin.announceWhiteBlankWin() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("宣布白板胜利", fontSize = adaptiveSp(14f))
            }
            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
        }

        Button(
            onClick = { plugin.resetGame() },
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Text("再来一局", fontSize = adaptiveSp(16f))
        }
    }
}
