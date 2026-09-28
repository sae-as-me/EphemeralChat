package com.ephemeral.chat.plugins.game.numberbomb.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ephemeral.chat.plugins.game.numberbomb.NumberBombPlugin

/**
 * 数字炸弹 UI 入口——根据游戏阶段显示不同页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberBombScreen(
    plugin: NumberBombPlugin,
    onBack: () -> Unit,
) {
    val state by plugin.uiState.collectAsStateLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数字炸弹", fontSize = adaptiveSp(16f)) },
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
                NumberBombPlugin.GamePhase.IDLE -> StartPage(plugin, state.errorMessage)
                NumberBombPlugin.GamePhase.WAITING_START -> WaitingPage()
                NumberBombPlugin.GamePhase.PLAYING -> PlayingPage(plugin, state)
                NumberBombPlugin.GamePhase.ENDED -> ResultPage(plugin, state)
            }
        }
    }
}

// ---- 发起页 ----

@Composable
private fun StartPage(plugin: NumberBombPlugin, errorMessage: String?) {
    var selectedPreset by remember { mutableIntStateOf(1) } // 默认选中 1-100（修复：原默认 0 无选中）
    var customMin by remember { mutableStateOf("") }
    var customMax by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "选择数字范围",
            fontSize = adaptiveSp(18f),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        // 快捷选择
        Row(
            horizontalArrangement = Arrangement.spacedBy(adaptiveDp(16f)),
        ) {
            OutlinedButton(
                onClick = {
                    selectedPreset = 1
                    customMin = ""
                    customMax = ""
                },
                colors = if (selectedPreset == 1) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else ButtonDefaults.outlinedButtonColors(),
            ) {
                Text("0 - 100", fontSize = adaptiveSp(16f))
            }
            OutlinedButton(
                onClick = {
                    selectedPreset = 2
                    customMin = ""
                    customMax = ""
                },
                colors = if (selectedPreset == 2) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else ButtonDefaults.outlinedButtonColors(),
            ) {
                Text("0 - 1000", fontSize = adaptiveSp(16f))
            }
        }

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))
        Text(
            text = "或自定义范围",
            fontSize = adaptiveSp(14f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(adaptiveDp(8f)))

        // 自定义范围输入
        Row(
            horizontalArrangement = Arrangement.spacedBy(adaptiveDp(8f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = customMin,
                onValueChange = {
                    customMin = it.filter { c -> c.isDigit() }
                    if (customMin.isNotEmpty()) selectedPreset = 0
                },
                modifier = Modifier.width(adaptiveDp(100f)),
                label = { Text("最小", fontSize = adaptiveSp(12f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Text("——", fontSize = adaptiveSp(16f))
            OutlinedTextField(
                value = customMax,
                onValueChange = {
                    customMax = it.filter { c -> c.isDigit() }
                    if (customMax.isNotEmpty()) selectedPreset = 0
                },
                modifier = Modifier.width(adaptiveDp(100f)),
                label = { Text("最大", fontSize = adaptiveSp(12f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(adaptiveDp(16f)))
            Text(
                text = it,
                fontSize = adaptiveSp(12f),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(adaptiveDp(32f)))

        // 开始按钮
        Button(
            onClick = {
                val (min, max) = when (selectedPreset) {
                    1 -> 0 to 100
                    2 -> 0 to 1000
                    else -> {
                        val mn = customMin.toIntOrNull() ?: 0
                        val mx = customMax.toIntOrNull() ?: 100
                        mn to mx
                    }
                }
                plugin.startGame(min, max)
            },
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Text("开始游戏", fontSize = adaptiveSp(16f))
        }
    }
}

// ---- 等待开始页 ----

@Composable
private fun WaitingPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("等待游戏开始...", fontSize = adaptiveSp(16f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- 游戏进行页 ----

@Composable
private fun PlayingPage(plugin: NumberBombPlugin, state: NumberBombPlugin.BombUiState) {
    var guessInput by remember { mutableStateOf("") }
    val isMyTurn = state.turnUuid == state.myUuid

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 当前范围
        Text(
            text = "当前范围",
            fontSize = adaptiveSp(14f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(adaptiveDp(8f)))
        Text(
            text = "[ ${state.rangeMin} — ${state.rangeMax} ]",
            fontSize = adaptiveSp(32f),
            color = MaterialTheme.colorScheme.primary,
        )

        // 上次猜测结果
        state.lastGuess?.let { lastGuess ->
            Spacer(modifier = Modifier.height(adaptiveDp(16f)))
            val guesserName = if (state.lastGuessPlayer == state.myUuid) "我" else plugin.run {
                state.order.firstOrNull { it.first == state.lastGuessPlayer }?.second ?: "玩家"
            }
            Text(
                text = "$guesserName 猜了 $lastGuess → ${if (state.lastResult == "explode") "💥爆炸！" else "安全，范围已收窄"}",
                fontSize = adaptiveSp(14f),
                color = if (state.lastResult == "explode") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        // 当前轮到谁
        Text(
            text = if (isMyTurn) "轮到你了！" else "等待 ${state.order.firstOrNull { it.first == state.turnUuid }?.second ?: "玩家"} 猜测...",
            fontSize = adaptiveSp(16f),
            color = if (isMyTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        // 玩家顺序
        Text(
            text = "玩家顺序: ${state.order.joinToString(" → ") { it.second }}",
            fontSize = adaptiveSp(12f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        // 输入框（仅当前玩家可输入）
        if (isMyTurn) {
            OutlinedTextField(
                value = guessInput,
                onValueChange = { guessInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入 ${state.rangeMin + 1} - ${state.rangeMax - 1} 的数字（不含边界）", fontSize = adaptiveSp(14f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(12f)))
            Button(
                onClick = {
                    val num = guessInput.toIntOrNull()
                    if (num != null) {
                        plugin.submitGuess(num)
                        guessInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = guessInput.isNotEmpty(),
            ) {
                Text("确认猜测", fontSize = adaptiveSp(16f))
            }
        }

        state.errorMessage?.let {
            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
            Text(it, fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.error)
        }
    }
}

// ---- 结果页 ----

@Composable
private fun ResultPage(plugin: NumberBombPlugin, state: NumberBombPlugin.BombUiState) {
    val winnerName = state.order.firstOrNull { it.first == state.winner }?.second ?: "玩家"
    val loserName = state.order.firstOrNull { it.first == state.loser }?.second ?: "玩家"

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "💥 游戏结束",
            fontSize = adaptiveSp(28f),
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(adaptiveDp(24f)))
        Text(
            text = "$loserName 引爆了炸弹！",
            fontSize = adaptiveSp(18f),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(adaptiveDp(8f)))
        Text(
            text = "胜者: $winnerName",
            fontSize = adaptiveSp(16f),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(adaptiveDp(32f)))
        Button(
            onClick = { plugin.resetGame() },
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Text("再来一局", fontSize = adaptiveSp(16f))
        }
    }
}
