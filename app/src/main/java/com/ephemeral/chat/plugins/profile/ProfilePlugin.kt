package com.ephemeral.chat.plugins.profile

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import com.ephemeral.chat.navigation.NavDestination
import com.ephemeral.chat.plugins.profile.ui.ProfileScreen

/**
 * 个人信息插件。
 * 注册个人信息导航项。
 * 依赖：无
 */
class ProfilePlugin : IPlugin {
    override val pluginId = "profile"
    override val dependencies = emptyList<String>()

    private val TAG = "ProfilePlugin"

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        registry.registerNavDestination(NavDestination.Profile) {
            ProfileScreen()
        }
        Log.i(TAG, "个人信息插件初始化完成")
    }

    override fun onStart() {}
    override fun onStop() {}
    override fun onDestroy() {}
}
