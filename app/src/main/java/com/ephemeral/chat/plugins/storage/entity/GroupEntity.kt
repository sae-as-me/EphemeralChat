package com.ephemeral.chat.plugins.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 群组实体。
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val code: String,
    val hubUuid: String,
    val isHub: Boolean,
    val createdAt: Long,
    val memberCount: Int,
    val status: GroupStatus,
)

/**
 * 群组状态。
 */
enum class GroupStatus { ACTIVE, DISSOLVING, DISSOLVED }
