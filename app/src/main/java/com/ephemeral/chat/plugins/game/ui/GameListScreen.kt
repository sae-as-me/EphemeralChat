package com.ephemeral.chat.plugins.game.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp

/**
 * 游戏选择页——列出所有已注册的小游戏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    onBack: () -> Unit,
    onSelectNumberBomb: () -> Unit,
    onSelectWhoIsSpy: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聚会小游戏", fontSize = adaptiveSp(16f)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(adaptiveDp(16f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(adaptiveDp(16f)))

            // 数字炸弹
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectNumberBomb() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(adaptiveDp(16f)),
                ) {
                    Text(
                        text = "数字炸弹",
                        fontSize = adaptiveSp(18f),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(adaptiveDp(4f)))
                    Text(
                        text = "轮流猜数字，猜中炸弹出局，最后一人获胜",
                        fontSize = adaptiveSp(12f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(adaptiveDp(12f)))

            // 谁是卧底
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectWhoIsSpy() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(adaptiveDp(16f)),
                ) {
                    Text(
                        text = "谁是卧底",
                        fontSize = adaptiveSp(18f),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(adaptiveDp(4f)))
                    Text(
                        text = "每人拿到相近词语，描述后投票淘汰卧底",
                        fontSize = adaptiveSp(12f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(adaptiveDp(12f)))
            Text(
                text = "更多游戏开发中...",
                fontSize = adaptiveSp(12f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
