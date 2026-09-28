package com.ephemeral.chat.plugins.emoji.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.ephemeral.chat.R

/**
 * 骰子面组件——渲染 Bootstrap Icons 风格骰子矢量图（ic_dice_1 ~ ic_dice_6）。
 * 每种点数有独特的 pip 排列图案，支持 tint 着色。
 */
@Composable
fun DiceFace(
    point: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF212121),
) {
    Icon(
        painter = painterResource(diceRes(point)),
        contentDescription = "骰子 $point 点",
        modifier = modifier.size(size),
        tint = tint,
    )
}

/**
 * 获取骰子点数对应的矢量图资源 ID。
 */
fun diceRes(point: Int): Int = when (point) {
    1 -> R.drawable.ic_dice_1
    2 -> R.drawable.ic_dice_2
    3 -> R.drawable.ic_dice_3
    4 -> R.drawable.ic_dice_4
    5 -> R.drawable.ic_dice_5
    6 -> R.drawable.ic_dice_6
    else -> R.drawable.ic_dice_1
}
