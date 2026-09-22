package com.ephemeral.chat.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航项定义。
 * 每个 UI 插件注册自己的导航项，BottomNavHost 动态拉取渲染。
 * route 唯一，用作 NavHost 路由键。
 * 后续新增 Tab 只需新增 data object，无需修改 NavDestination。
 */
sealed class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    /** 群聊页 */
    data object Chat : NavDestination("chat", "群聊", Icons.AutoMirrored.Filled.Chat)

    /** 个人信息页 */
    data object Profile : NavDestination("profile", "我的", Icons.Default.Person)
}
