package com.ephemeral.chat

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

    companion object {
        private lateinit var instance: EphemeralChatApplication

        /** 全局 Application 实例（用于启动前台服务、发通知等） */
        fun get(): EphemeralChatApplication = instance

        /** 前台 Activity 计数：>0 表示应用在前台 */
        @Volatile
        private var foregroundActivities = 0

        /** 应用是否在前台（后台时用于决定是否弹窗通知） */
        fun isAppInForeground(): Boolean = foregroundActivities > 0
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityForegroundTracker()
        // 设置全局未捕获异常处理（防止个别设备兼容性问题导致闪退无反馈）
        installCrashGuard()

        // 初始化全局共享状态（主题/昵称持久化）
        // 用 try-catch 保护：DataStore 异常不应阻断启动
        try {
            SharedStateManager.init(this)
        } catch (e: Exception) {
            android.util.Log.e("EphemeralChatApp", "SharedStateManager 初始化失败（忽略继续）", e)
        }

        try {
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
        } catch (e: Exception) {
            android.util.Log.e("EphemeralChatApp", "插件注册失败（应用继续运行，功能受限）", e)
        }
    }

    /**
     * 安装全局未捕获异常处理器。
     * 将崩溃信息写入应用私有文件 crash_log.txt，便于后续排查。
     */
    private fun installCrashGuard() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = java.io.File(filesDir, "crash_log.txt")
                logFile.appendText(
                    "${System.currentTimeMillis()}\n${thread.name}: ${throwable}\n" +
                    throwable.stackTrace.joinToString("\n") { "    at $it" } + "\n\n"
                )
            } catch (_: Exception) {}
            // 交给系统默认处理器结束进程
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 注册 Activity 生命周期监听，用于判断应用是否在前台。
     * 用于：后台收到群聊消息时决定是否弹窗通知。
     */
    private fun registerActivityForegroundTracker() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                foregroundActivities++
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                foregroundActivities--
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }
}
