package com.ephemeral.chat.core.ui.adaptive

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 自适应 Modifier 扩展函数集合。
 * 提供 Composable 中便捷的尺寸/间距设置方式。
 */

/**
 * 自适应图标尺寸 Modifier。
 */
@androidx.compose.runtime.Composable
fun Modifier.adaptiveIconSize(base: Float): Modifier =
    this.size(adaptiveDp(base))

/**
 * 自适应内边距 Modifier。
 */
@androidx.compose.runtime.Composable
fun Modifier.adaptivePadding(base: Float): Modifier =
    this.padding(adaptiveDp(base))
