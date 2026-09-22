package com.ephemeral.chat.core.eventbus

/**
 * 跨插件事件密封类。
 * 所有跨插件通信通过 EventBus 分发，事件类型在此集中定义。
 */
sealed class AppEvent {

    // ---- BLE 相关 ----

    /** BLE 扫描发现匹配设备，携带设备地址和邀请码哈希 */
    data class DeviceDiscovered(val deviceAddress: String, val codeHash: ByteArray) : AppEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DeviceDiscovered) return false
            return deviceAddress == other.deviceAddress && codeHash.contentEquals(other.codeHash)
        }
        override fun hashCode(): Int = deviceAddress.hashCode() * 31 + codeHash.contentHashCode()
    }

    /** GATT 连接成功，携带设备地址 */
    data class GattConnected(val deviceAddress: String) : AppEvent()

    /** GATT 连接断开，携带设备地址 */
    data class GattDisconnected(val deviceAddress: String) : AppEvent()

    // ---- 消息相关 ----

    /** 收到消息，携带解密后内容、发送者 UUID、昵称和时间戳 */
    data class MessageReceived(val content: String, val senderUuid: String, val senderName: String, val timestamp: Long) : AppEvent()

    /** 消息已发送，携带消息 ID */
    data class MessageSent(val msgId: String) : AppEvent()

    // ---- 成员相关 ----

    /** 成员加入群聊 */
    data class MemberJoined(val uuid: String, val nickname: String) : AppEvent()

    /** 成员离开群聊 */
    data class MemberLeft(val uuid: String) : AppEvent()

    /** 成员离线（心跳超时 45s） */
    data class MemberOffline(val uuid: String) : AppEvent()

    /** 成员修改昵称 */
    data class NicknameChanged(val uuid: String, val newNickname: String) : AppEvent()

    // ---- 定位相关 ----

    /** 定位开关状态变化 */
    data class LocationStateChanged(val enabled: Boolean) : AppEvent()

    /** 获取到 GPS 位置 */
    data class LocationAcquired(val latitude: Double, val longitude: Double) : AppEvent()

    // ---- 群聊生命周期 ----

    /** 群聊创建完成，携带群组 ID 和邀请码 */
    data class GroupCreated(val groupId: String, val code: String) : AppEvent()

    /** 成功加入群聊 */
    data class GroupJoined(val groupId: String, val nickname: String) : AppEvent()

    /** 群聊解散 */
    data class GroupDissolved(val groupId: String) : AppEvent()

    /** Hub 变更（重选后新 Hub 上线） */
    data class HubChanged(val newHubUuid: String) : AppEvent()

    /** 收到心跳 */
    data class HeartbeatReceived(val uuid: String, val timestamp: Long) : AppEvent()

    /** 心跳超时 */
    data class HeartbeatTimeout(val uuid: String) : AppEvent()
}
