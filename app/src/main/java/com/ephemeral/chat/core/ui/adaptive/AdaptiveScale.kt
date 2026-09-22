package com.ephemeral.chat.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

/**
 * UI 自适应缩放系统。
 * 根据屏幕物理对角线尺寸计算缩放因子，通过 CompositionLocal 全局注入。
 * 所有 UI 组件统一使用 adaptiveSp() / adaptiveDp() 声明尺寸。
 */

/**
 * 全局自适应缩放因子。默认 1.0f。
 * 通过 AdaptiveScaleProvider 注入。
 * ≤4.7" → 0.85，标准 → 1.0，≥6.5" → 1.15
 */
val LocalAdaptiveScale = staticCompositionLocalOf { 1.0f }

/**
 * 自适应缩放 Provider。
 * 通过 LocalConfiguration 获取屏幕尺寸，计算对角线英寸，推导缩放因子。
 * 须包裹在最外层（Theme 之前），使 Theme 内部也能读取缩放因子。
 */
@Composable
fun AdaptiveScaleProvider(
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = configuration.screenWidthDp * density.density
    val screenHeightPx = configuration.screenHeightDp * density.density
    // 对角线像素 / (density * 25.4) = 对角线英寸
    val diagonalInches = sqrt(
        screenWidthPx.toDouble().pow(2.0) + screenHeightPx.toDouble().pow(2.0)
    ) / (density.density * 25.4)

    val scale = when {
        diagonalInches <= 4.7 -> 0.85f
        diagonalInches >= 6.5 -> 1.15f
        else -> 1.0f
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAdaptiveScale provides scale,
        content = content,
    )
}

/**
 * 自适应字号。取 LocalAdaptiveScale.current 乘以 base。
 */
@Composable
fun adaptiveSp(base: Float): TextUnit = (base * LocalAdaptiveScale.current).sp

/**
 * 自适应尺寸。取 LocalAdaptiveScale.current 乘以 base。
 */
@Composable
fun adaptiveDp(base: Float): Dp = (base * LocalAdaptiveScale.current).dp

// 由于 Kotlin math 没有 pow(Float)，用 Double 版本
private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)
