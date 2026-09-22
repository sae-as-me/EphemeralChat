package com.ephemeral.chat.plugins.storage

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.ephemeral.chat.plugins.storage.dao.GroupDao
import com.ephemeral.chat.plugins.storage.dao.MemberDao
import com.ephemeral.chat.plugins.storage.dao.MessageDao
import net.sqlcipher.database.SupportFactory

/**
 * 数据库管理器——封装 Room + SQLCipher。
 * 提供加密数据库的创建和访问。
 */
class DatabaseManager(
    private val context: Context,
    private val passphrase: ByteArray,
) {
    private val TAG = "DatabaseManager"
    private var database: EphemeralDatabase? = null

    /**
     * 创建加密数据库实例。
     * 使用 SQLCipher 的 SupportFactory 注入加密。
     */
    @Synchronized
    fun createDatabase(): EphemeralDatabase {
        if (database != null) return database!!

        val factory = SupportFactory(passphrase)
        database = Room.databaseBuilder(
            context,
            EphemeralDatabase::class.java,
            "ephemeral_chat.db",
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()

        Log.i(TAG, "加密数据库已创建")
        return database!!
    }

    /**
     * 获取群组 DAO。
     */
    fun getGroupDao(): GroupDao = createDatabase().groupDao()

    /**
     * 获取成员 DAO。
     */
    fun getMemberDao(): MemberDao = createDatabase().memberDao()

    /**
     * 获取消息 DAO。
     */
    fun getMessageDao(): MessageDao = createDatabase().messageDao()

    /**
     * 清除指定群组的所有数据。
     */
    suspend fun clearGroupData(groupId: String) {
        val db = createDatabase()
        db.messageDao().deleteByGroupId(groupId)
        db.memberDao().deleteByGroupId(groupId)
        db.groupDao().deleteByGroupId(groupId)
        Log.i(TAG, "已清除群组 $groupId 的所有数据")
    }

    /**
     * 清除所有数据（解散时调用）。
     */
    suspend fun clearAll() {
        val db = createDatabase()
        db.messageDao().deleteAll()
        db.memberDao().deleteAll()
        db.groupDao().deleteAll()
        Log.i(TAG, "已清除所有数据库数据")
    }

    /**
     * 清零并释放密钥。
     */
    fun close() {
        database?.close()
        database = null
        // 清零密钥
        passphrase.fill(0)
        Log.i(TAG, "数据库已关闭，密钥已清零")
    }
}
