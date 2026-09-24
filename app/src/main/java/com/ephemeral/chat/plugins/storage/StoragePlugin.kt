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
 * 初始化失败不阻断启动；DAO 访问时惰性重建。
 */
class StoragePlugin : IPlugin {
    override val pluginId = "storage"
    override val dependencies = listOf("crypto")

    private val TAG = "StoragePlugin"
    private var databaseManager: DatabaseManager? = null
    private var registry: PluginRegistry? = null

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        this.registry = registry
        try {
            val cryptoPlugin = registry.getPlugin<CryptoPlugin>("crypto")
            if (cryptoPlugin == null) {
                Log.e(TAG, "CryptoPlugin 未注册，无法获取数据库密钥")
                return
            }

            val passphrase = cryptoPlugin.getDatabaseKey()
            databaseManager = DatabaseManager(registry.applicationContext, passphrase)
            Log.i(TAG, "存储插件初始化完成")
        } catch (e: Exception) {
            // 密钥生成异常不应阻断启动，数据库会在首次使用时重建
            Log.e(TAG, "存储插件初始化失败（延迟到首次使用）", e)
        }
    }

    override fun onStart() {}
    override fun onStop() {}

    override fun onDestroy() {
        try {
            databaseManager?.close()
        } catch (e: Exception) {
            Log.e(TAG, "关闭数据库失败", e)
        }
        databaseManager = null
        Log.i(TAG, "存储插件已销毁")
    }

    /**
     * 确保数据库管理器可用。
     * 若 onInit 阶段失败，则在此处重试创建（使用 CryptoPlugin 密钥）。
     */
    private fun ensureDatabaseManager(): DatabaseManager {
        databaseManager?.let { return it }

        val reg = registry
        val context = reg?.applicationContext ?: return throwDbError()
        val cryptoPlugin = reg.getPlugin<CryptoPlugin>("crypto")
        if (cryptoPlugin == null) throwDbError()

        val passphrase = try {
            cryptoPlugin!!.getDatabaseKey()
        } catch (e: Exception) {
            Log.e(TAG, "获取数据库密钥失败", e)
            throw IllegalStateException("数据库密钥获取失败", e)
        }
        val manager = DatabaseManager(context, passphrase)
        databaseManager = manager
        Log.i(TAG, "存储插件（惰性）初始化完成")
        return manager
    }

    private fun throwDbError(): Nothing {
        throw IllegalStateException("存储插件不可用（CryptoPlugin 未注册）")
    }

    fun getGroupDao(): GroupDao = ensureDatabaseManager().getGroupDao()
    fun getMemberDao(): MemberDao = ensureDatabaseManager().getMemberDao()
    fun getMessageDao(): MessageDao = ensureDatabaseManager().getMessageDao()

    suspend fun clearGroupData(groupId: String) {
        ensureDatabaseManager().clearGroupData(groupId)
    }

    suspend fun clearAll() {
        ensureDatabaseManager().clearAll()
    }
}