package com.ephemeral.chat.plugins.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 成员实体。
 */
@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["groupId"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class MemberEntity(
    @PrimaryKey val memberUuid: String,
    val groupId: String,
    val nickname: String,
    val isSelf: Boolean,
    val status: MemberStatus,
    val lastHeartbeat: Long,
    val joinedAt: Long,
)

/**
 * 成员状态。
 */
enum class MemberStatus { ACTIVE, OFFLINE, LEFT }
