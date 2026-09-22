package com.ephemeral.chat.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ephemeral.chat.core.ui.adaptive.AdaptiveScaleProvider

/**
 * EphemeralChat 主题入口。
 * 深色为默认，支持浅色切换。
 * 集成自适应缩放：先 AdaptiveScaleProvider → 再 MaterialTheme。
 *
 * @param darkTheme 是否使用深色主题，默认跟随系统（应用内默认深色）
 * @param content 主题包裹的内容
 */
@Composable
fun EphemeralChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    AdaptiveScaleProvider {
        val colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = DarkPrimary,
                onPrimary = DarkOnPrimary,
                secondary = DarkSecondary,
                background = DarkBackground,
                onBackground = DarkOnBackground,
                surface = DarkSurface,
                onSurface = DarkOnSurface,
                surfaceVariant = DarkSurfaceVariant,
                outline = DarkOutline,
            )
        } else {
            lightColorScheme(
                primary = LightPrimary,
                onPrimary = LightOnPrimary,
                secondary = LightSecondary,
                background = LightBackground,
                onBackground = LightOnBackground,
                surface = LightSurface,
                onSurface = LightOnSurface,
                surfaceVariant = LightSurfaceVariant,
                outline = LightOutline,
            )
        }

        val typography = adaptiveTypography()

        // 设置状态栏颜色与系统栏
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
