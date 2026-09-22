package com.ephemeral.chat.plugins.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ephemeral.chat.plugins.storage.dao.GroupDao
import com.ephemeral.chat.plugins.storage.dao.MemberDao
import com.ephemeral.chat.plugins.storage.dao.MessageDao
import com.ephemeral.chat.plugins.storage.entity.GroupEntity
import com.ephemeral.chat.plugins.storage.entity.MemberEntity
import com.ephemeral.chat.plugins.storage.entity.MessageEntity

/**
 * EphemeralChat 加密数据库。
 * 使用 Room + SQLCipher，全库加密。
 * 密钥由 CryptoPlugin 通过 Android Keystore 生成。
 */
@Database(
    entities = [GroupEntity::class, MemberEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EphemeralDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun messageDao(): MessageDao
}
