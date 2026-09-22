package com.ephemeral.chat.plugins.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ephemeral.chat.plugins.storage.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * 成员 DAO。
 */
@Dao
interface MemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: MemberEntity)

    @Delete
    suspend fun delete(member: MemberEntity)

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY joinedAt ASC")
    fun observeByGroupId(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    suspend fun getByGroupId(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE memberUuid = :uuid AND groupId = :groupId")
    suspend fun getById(uuid: String, groupId: String): MemberEntity?

    @Query("DELETE FROM members WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("DELETE FROM members")
    suspend fun deleteAll()
}
