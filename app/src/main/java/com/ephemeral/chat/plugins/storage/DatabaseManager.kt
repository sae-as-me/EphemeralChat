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
 * 重要：数据库文件是临时性的（解散/退出即清），若因密钥变化等原因无法打开，
 * 直接删除旧文件重建，绝不让启动/使用崩溃。
 */
class DatabaseManager(
    private val context: Context,
    private val passphrase: ByteArray,
) {
    private val TAG = "DatabaseManager"
    private var database: EphemeralDatabase? = null

    companion object {
        private const val DB_NAME = "ephemeral_chat.db"
    }

    /**
     * 创建加密数据库实例。
     * 使用 SQLCipher 的 SupportFactory 注入加密。
     * 若已有数据库文件无法用当前密钥打开（例如版本升级导致密钥派生变化），
     * 捕获异常后删除旧文件重建——保证应用不崩溃。
     */
    @Synchronized
    fun createDatabase(): EphemeralDatabase {
        if (database != null) return database!!
        return try {
            buildDatabase()
        } catch (e: Exception) {
            // 打开失败：删除损坏/密钥不匹配的旧库，重建全新库
            Log.e(TAG, "打开数据库失败，删除旧库重建: ${e.message}")
            deleteDatabase()
            buildDatabase()
        }
    }

    /**
     * 实际构建数据库。
     */
    private fun buildDatabase(): EphemeralDatabase {
        val factory = SupportFactory(passphrase)
        val db = Room.databaseBuilder(
            context,
            EphemeralDatabase::class.java,
            DB_NAME,
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
        database = db
        Log.i(TAG, "加密数据库已创建")
        return db
    }

    /**
     * 删除数据库所有相关文件。
     */
    private fun deleteDatabase() {
        context.deleteDatabase(DB_NAME)
        context.deleteDatabase("$DB_NAME-shm")
        context.deleteDatabase("$DB_NAME-wal")
        context.deleteDatabase("$DB_NAME-journal")
        database = null
        Log.w(TAG, "已删除旧数据库文件")
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
