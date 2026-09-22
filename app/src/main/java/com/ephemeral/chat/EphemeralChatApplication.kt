package com.ephemeral.chat

import android.app.Application
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.registry.PluginRegistry
import com.ephemeral.chat.plugins.ble.BlePlugin
import com.ephemeral.chat.plugins.chat.ChatPlugin
import com.ephemeral.chat.plugins.crypto.CryptoPlugin
import com.ephemeral.chat.plugins.lifecycle.LifecyclePlugin
import com.ephemeral.chat.plugins.location.LocationPlugin
import com.ephemeral.chat.plugins.profile.ProfilePlugin
import com.ephemeral.chat.plugins.storage.StoragePlugin
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

/**
 * EphemeralChat 应用入口。
 * 负责初始化插件注册中心和事件总线。
 * Phase 3：注册全部插件，替换临时占位插件。
 * 注册顺序：Location → Crypto → Storage → BLE → Lifecycle → Chat → Profile
 */
@HiltAndroidApp
class EphemeralChatApplication : Application() {

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var pluginRegistryProvider: Provider<PluginRegistry>

    lateinit var pluginRegistry: PluginRegistry
        private set

    override fun onCreate() {
        super.onCreate()
        pluginRegistry = pluginRegistryProvider.get()

        // 完整注册顺序（拓扑排序保证依赖正确）
        pluginRegistry.register(LocationPlugin())
        pluginRegistry.register(CryptoPlugin())
        pluginRegistry.register(StoragePlugin())
        pluginRegistry.register(BlePlugin())
        pluginRegistry.register(LifecyclePlugin())
        pluginRegistry.register(ChatPlugin())
        pluginRegistry.register(ProfilePlugin())

        pluginRegistry.startAll()
    }
}
