package com.ephemeral.chat.plugins.storage

import android.util.Log
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import com.ephemeral.chat.plugins.crypto.CryptoPlugin
import com.ephemeral.chat.plugins.storage.dao.GroupDao
import com.ephemeral.chat.plugins.storage.dao.MemberDao
import com.ephemeral.chat.plugins.storage.dao.MessageDao

/**
 * 存储插件——提供加密数据库访问能力。
 * 依赖：CryptoPlugin（提供数据库密钥）
 */
class StoragePlugin : IPlugin {
    override val pluginId = "storage"
    override val dependencies = listOf("crypto")

    private val TAG = "StoragePlugin"
    private lateinit var databaseManager: DatabaseManager

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        val cryptoPlugin = registry.getPlugin<CryptoPlugin>("crypto")
        if (cryptoPlugin == null) {
            Log.e(TAG, "CryptoPlugin 未注册，无法获取数据库密钥")
            return
        }

        val passphrase = cryptoPlugin.getDatabaseKey()
        databaseManager = DatabaseManager(registry.applicationContext, passphrase)
        Log.i(TAG, "存储插件初始化完成")
    }

    override fun onStart() {}
    override fun onStop() {}

    override fun onDestroy() {
        databaseManager.close()
        Log.i(TAG, "存储插件已销毁")
    }

    fun getGroupDao(): GroupDao = databaseManager.getGroupDao()
    fun getMemberDao(): MemberDao = databaseManager.getMemberDao()
    fun getMessageDao(): MessageDao = databaseManager.getMessageDao()

    suspend fun clearGroupData(groupId: String) {
        databaseManager.clearGroupData(groupId)
    }

    suspend fun clearAll() {
        databaseManager.clearAll()
    }
}
