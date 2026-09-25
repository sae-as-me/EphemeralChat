package com.ephemeral.chat.plugins.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体。
 */
@Entity(
    tableName = "messages",
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
data class MessageEntity(
    @PrimaryKey val msgId: String,
    val groupId: String,
    val senderUuid: String,
    val senderName: String,
    val content: String,
    val type: MessageType,
    val timestamp: Long,
    val isDelivered: Boolean,
)

/**
 * 消息类型。
 */
enum class MessageType { TEXT, SYSTEM, IMAGE, FILE }
