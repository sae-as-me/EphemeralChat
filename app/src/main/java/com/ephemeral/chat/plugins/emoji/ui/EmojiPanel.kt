package com.ephemeral.chat.plugins.emoji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.emoji.EmojiPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 表情面板——Tab 分类（表情/游戏）。
 * 表情直接以文本消息发送，游戏表情带简短动画后发送。
 */
@Composable
fun EmojiPanel(
    emojiPlugin: EmojiPlugin,
    onSendEmoji: (String) -> Unit,
    onSendGame: (String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("表情", "游戏")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(adaptiveDp(240f)),
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = adaptiveSp(14f)) },
                )
            }
        }

        when (selectedTab) {
            0 -> EmojiGrid(emojiPlugin, onSendEmoji)
            1 -> GamePanel(emojiPlugin, onSendGame)
        }
    }
}

/**
 * 表情网格——8 列，点击直接发送。
 */
@Composable
private fun EmojiGrid(
    emojiPlugin: EmojiPlugin,
    onSendEmoji: (String) -> Unit,
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = emojiPlugin.categories

    Column(modifier = Modifier.fillMaxWidth()) {
        // 分类标签行
        Row(
            modifier = Modifier.fillMaxWidth().padding(adaptiveDp(4f)),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            categories.forEachIndexed { index, cat ->
                val isSelected = selectedCategory == index
                Text(
                    text = cat.name,
                    fontSize = adaptiveSp(12f),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(adaptiveDp(4f)).clickable { selectedCategory = index },
                )
            }
        }

        // 表情网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth().padding(adaptiveDp(4f)),
            horizontalArrangement = Arrangement.spacedBy(adaptiveDp(2f)),
            verticalArrangement = Arrangement.spacedBy(adaptiveDp(2f)),
        ) {
            items(categories[selectedCategory].emojis) { emoji ->
                Text(
                    text = emoji,
                    fontSize = adaptiveSp(24f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(adaptiveDp(2f)).clickable { onSendEmoji(emoji) },
                )
            }
        }
    }
}

/**
 * 游戏面板——剪刀石头布、骰子。
 * 点击后播放简短动画，然后发送结果。
 */
@Composable
private fun GamePanel(
    emojiPlugin: EmojiPlugin,
    onSendGame: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var animatingResult by remember { mutableStateOf<String?>(null) }
    var animatingDicePoint by remember { mutableStateOf<Int?>(null) }
    val animScale = remember { Animatable(1f) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(adaptiveDp(16f)),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // 剪刀石头布
                GameButton(
                    icon = Icons.Default.EmojiEmotions,
                    label = "猜拳",
                    onClick = {
                        if (animatingResult != null) return@GameButton
                        scope.launch {
                            for (i in 0..4) {
                                animatingResult = listOf("✊", "✌️", "🖐️").random()
                                animScale.animateTo(1.5f, tween(100))
                                animScale.animateTo(1f, tween(100))
                                delay(80)
                            }
                            val result = emojiPlugin.randomRockPaperScissors()
                            animatingResult = result
                            delay(500)
                            animatingResult = null
                            onSendGame(result)
                        }
                    },
                )

                // 骰子
                GameButton(
                    icon = Icons.Default.Casino,
                    label = "骰子",
                    onClick = {
                        if (animatingDicePoint != null) return@GameButton
                        scope.launch {
                            for (i in 0..5) {
                                val p = (1..6).random()
                                animatingDicePoint = p
                                animScale.animateTo(1.5f, tween(80))
                                animScale.animateTo(1f, tween(80))
                                delay(60)
                            }
                            val (resultEmoji, point) = emojiPlugin.randomDice()
                            animatingDicePoint = point
                            delay(500)
                            animatingDicePoint = null
                            // 发送占位符文本，聊天气泡识别后渲染为骰子图标
                            onSendGame("[dice:$point]")
                        }
                    },
                )
            }
        }

        // 动画覆盖层
        val showDiceAnim = animatingDicePoint != null
        val showTextAnim = animatingResult != null

        AnimatedVisibility(
            visible = showDiceAnim,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            // 修复：Canvas 自绘标准骰子面，6 种点数各有独特 pip 排列图案
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DiceFace(
                    point = animatingDicePoint ?: 1,
                    size = 120.dp,
                    modifier = Modifier.scale(animScale.value),
                )
                animatingDicePoint?.let { p ->
                    Text(
                        text = "$p 点",
                        fontSize = 48.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFFE53935),
                        modifier = Modifier.scale(animScale.value),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showTextAnim,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            // 猜拳动画保持文字 emoji
            Text(
                text = animatingResult ?: "",
                fontSize = 72.sp,
                modifier = Modifier.scale(animScale.value),
            )
        }
    }
}

/**
 * 游戏按钮组件。
 */
@Composable
private fun GameButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(adaptiveDp(8f)).clickable { onClick() },
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(adaptiveDp(48f)),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(adaptiveDp(4f)))
        Text(label, fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.onSurface)
    }
}
