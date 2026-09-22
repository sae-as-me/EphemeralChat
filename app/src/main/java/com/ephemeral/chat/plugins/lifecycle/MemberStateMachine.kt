package com.ephemeral.chat.plugins.lifecycle

import com.ephemeral.chat.plugins.storage.entity.MemberStatus

/**
 * 成员状态机——管理单个成员的状态转换。
 * Disconnected → Joining → Active → Offline → Left
 */
class MemberStateMachine {

    /**
     * 根据当前状态和事件计算新状态。
     *
     * @param current 当前状态
     * @param event 触发事件
     * @return 新状态，null 表示无效转换
     */
    fun transition(current: MemberStatus, event: MemberEvent): MemberStatus? {
        return when {
            // Joining → Active
            current == MemberStatus.OFFLINE && event == MemberEvent.HeartbeatReceived -> MemberStatus.ACTIVE
            // Active → Offline
            current == MemberStatus.ACTIVE && event == MemberEvent.HeartbeatTimeout45s -> MemberStatus.OFFLINE
            // Offline → Active
            current == MemberStatus.OFFLINE && event == MemberEvent.HeartbeatReceived -> MemberStatus.ACTIVE
            // Offline → Left
            current == MemberStatus.OFFLINE && event == MemberEvent.Timeout90s -> MemberStatus.LEFT
            // Active → Left
            current == MemberStatus.ACTIVE && event == MemberEvent.ManualLeave -> MemberStatus.LEFT
            current == MemberStatus.ACTIVE && event == MemberEvent.LocationDisabled -> MemberStatus.LEFT
            // Any → Left
            event == MemberEvent.LocationDisabled -> MemberStatus.LEFT
            else -> null
        }
    }
}

/**
 * 成员事件类型。
 */
enum class MemberEvent {
    GattConnected,
    JoinAckReceived,
    HeartbeatReceived,
    HeartbeatTimeout45s,
    Timeout90s,
    ManualLeave,
    LocationDisabled,
}
