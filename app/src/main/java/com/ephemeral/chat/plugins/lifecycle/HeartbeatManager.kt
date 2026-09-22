package com.ephemeral.chat.plugins.lifecycle

import com.ephemeral.chat.core.eventbus.AppEvent
import com.ephemeral.chat.core.eventbus.EventBus
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 心跳管理器。
 * 每 15 秒发送心跳，45 秒未收到判定离线，90 秒移除。
 */
class HeartbeatManager(
    private val eventBus: EventBus,
    private val coroutineScope: CoroutineScope,
) {
    private val TAG = "HeartbeatManager"

    private val heartbeatInterval = 15_000L
    private val offlineThreshold = 45_000L
    private val removeThreshold = 90_000L

    /** 成员心跳记录：Map<uuid, Long(上次心跳时间)> */
    private val memberHeartbeats = ConcurrentHashMap<String, Long>()

    private var heartbeatJob: Job? = null
    private var checkJob: Job? = null

    /**
     * 启动心跳管理。
     *
     * @param isHub 是否为 Hub 角色
     * @param sendHeartbeat 发送心跳的回调（Hub 广播，Client 写入 Hub）
     * @param onMemberOffline 成员离线回调
     * @param onMemberLeft 成员离开回调
     */
    fun start(
        isHub: Boolean,
        sendHeartbeat: () -> Unit,
        onMemberOffline: (String) -> Unit,
        onMemberLeft: (String) -> Unit,
    ) {
        stop()

        // 定时发送心跳
        heartbeatJob = coroutineScope.launch {
            while (true) {
                delay(heartbeatInterval)
                sendHeartbeat()
                Log.d(TAG, "心跳已发送")
            }
        }

        // 定时检查成员心跳超时
        checkJob = coroutineScope.launch {
            while (true) {
                delay(heartbeatInterval)
                checkTimeouts(onMemberOffline, onMemberLeft)
            }
        }

        Log.i(TAG, "心跳管理器已启动, isHub=$isHub")
    }

    /**
     * 收到心跳，更新成员心跳时间。
     *
     * @param uuid 成员 UUID
     */
    fun onHeartbeatReceived(uuid: String) {
        memberHeartbeats[uuid] = System.currentTimeMillis()
    }

    /**
     * 添加成员到心跳追踪。
     */
    fun addMember(uuid: String) {
        memberHeartbeats[uuid] = System.currentTimeMillis()
    }

    /**
     * 移除成员。
     */
    fun removeMember(uuid: String) {
        memberHeartbeats.remove(uuid)
    }

    /**
     * 检查心跳超时。
     * 45s 超时 → 发布 MemberOffline
     * 90s 超时 → 发布 MemberLeft
     */
    private fun checkTimeouts(
        onMemberOffline: (String) -> Unit,
        onMemberLeft: (String) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        val toOffline = mutableListOf<String>()

        for ((uuid, lastHeartbeat) in memberHeartbeats) {
            val elapsed = now - lastHeartbeat
            when {
                elapsed > removeThreshold -> {
                    toRemove.add(uuid)
                    onMemberLeft(uuid)
                    Log.i(TAG, "成员 $uuid 超时 $removeThreshold ms，标记离开")
                }
                elapsed > offlineThreshold -> {
                    toOffline.add(uuid)
                    onMemberOffline(uuid)
                    Log.d(TAG, "成员 $uuid 超时 $offlineThreshold ms，标记离线")
                }
            }
        }

        for (uuid in toRemove) {
            memberHeartbeats.remove(uuid)
        }
    }

    /**
     * 停止心跳管理，清空状态。
     */
    fun stop() {
        heartbeatJob?.cancel()
        checkJob?.cancel()
        heartbeatJob = null
        checkJob = null
        memberHeartbeats.clear()
        Log.i(TAG, "心跳管理器已停止")
    }
}
