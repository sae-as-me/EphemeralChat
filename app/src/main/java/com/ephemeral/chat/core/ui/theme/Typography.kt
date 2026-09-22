package com.ephemeral.chat.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.ephemeral.chat.core.ui.adaptive.LocalAdaptiveScale
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp

/**
 * Material 3 Typography 定义。
 * 所有字号通过 adaptiveSp() 设置，自动按屏幕尺寸缩放。
 */

/**
 * 创建自适应 Typography。
 * 在 Composable 上下文中调用，读取 LocalAdaptiveScale。
 */
@Composable
fun adaptiveTypography(): Typography {
    val scale = LocalAdaptiveScale.current
    return Typography(
        headlineLarge = TextStyle(fontSize = (32 * scale).sp),
        headlineMedium = TextStyle(fontSize = (28 * scale).sp),
        titleLarge = TextStyle(fontSize = (22 * scale).sp),
        titleMedium = TextStyle(fontSize = (16 * scale).sp),
        bodyLarge = TextStyle(fontSize = (16 * scale).sp),
        bodyMedium = TextStyle(fontSize = (14 * scale).sp),
        bodySmall = TextStyle(fontSize = (12 * scale).sp),
        labelLarge = TextStyle(fontSize = (14 * scale).sp),
        labelMedium = TextStyle(fontSize = (12 * scale).sp),
        labelSmall = TextStyle(fontSize = (11 * scale).sp),
    )
}
