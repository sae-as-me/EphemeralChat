package com.ephemeral.chat.plugins.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ephemeral.chat.plugins.storage.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 消息 DAO。
 */
@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun observeByGroupId(groupId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    suspend fun getByGroupId(groupId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
