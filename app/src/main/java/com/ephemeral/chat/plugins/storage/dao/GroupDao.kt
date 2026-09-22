package com.ephemeral.chat.plugins.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ephemeral.chat.plugins.storage.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

/**
 * 群组 DAO。
 */
@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity)

    @Delete
    suspend fun delete(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("DELETE FROM groups WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("DELETE FROM groups")
    suspend fun deleteAll()
}
