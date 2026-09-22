package com.ephemeral.chat.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ephemeral.chat.EphemeralChatApplication
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.core.ui.adaptive.adaptiveIconSize

/**
 * 底部导航主框架。
 * 从 PluginRegistry 动态获取导航项列表，渲染 NavigationBar + NavHost。
 * 新增 Tab 只需插件注册 NavDestination，无需修改此文件。
 */
@Composable
fun BottomNavHost() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as EphemeralChatApplication
    val registry = app.pluginRegistry

    val navItems = remember { registry.getNavDestinations() }
    val navController: NavHostController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (navItems.isNotEmpty()) {
                NavigationBar {
                    navItems.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = dest.label,
                                    modifier = Modifier.adaptiveIconSize(24f),
                                )
                            },
                            label = {
                                Text(
                                    text = dest.label,
                                    fontSize = adaptiveSp(12f),
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (navItems.isNotEmpty()) navItems.first().route else "empty",
            modifier = Modifier.padding(innerPadding),
        ) {
            if (navItems.isEmpty()) {
                composable("empty") {
                    Text(
                        text = "EphemeralChat",
                        fontSize = adaptiveSp(16f),
                    )
                }
            } else {
                navItems.forEach { dest ->
                    composable(dest.route) {
                        val content = registry.getNavContent(dest.route)
                        if (content != null) {
                            content()
                        } else {
                            Text(
                                text = dest.label,
                                fontSize = adaptiveSp(16f),
                            )
                        }
                    }
                }
            }
        }
    }
}
