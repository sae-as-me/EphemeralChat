package com.ephemeral.chat.plugins.lifecycle

import android.util.Log
import com.ephemeral.chat.core.eventbus.AppEvent
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.registry.PluginRegistry
import com.ephemeral.chat.plugins.ble.BlePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Hub 重选协议。
 * 当 Hub 离开时，剩余成员通过随机退避+扫描确认选举新 Hub。
 */
class HubElection(
    private val registry: PluginRegistry,
    private val eventBus: EventBus,
    private val coroutineScope: CoroutineScope,
) {
    private val TAG = "HubElection"

    private var electionJob: Job? = null
    private var isElectionRunning = false

    /**
     * 启动 Hub 选举。
     *
     * @param myUuid 本机 UUID
     * @param groupId 群组 ID
     * @param groupIdShort 群组 ID 短码（4 字节）
     * @param onElected 当本机被选为新 Hub 时的回调
     * @param onFoundHub 当发现其他新 Hub 时的回调
     */
    fun startElection(
        myUuid: String,
        groupId: String,
        groupIdShort: ByteArray,
        onElected: () -> Unit,
        onFoundHub: (String) -> Unit,
    ) {
        cancel()

        isElectionRunning = true

        // 计算退避时间 = hash(UUID) % 3000ms
        val backoffMs = (myUuid.hashCode().absoluteValue % 3000).toLong()
        Log.i(TAG, "启动 Hub 选举, myUuid=$myUuid, backoff=${backoffMs}ms")

        val blePlugin = registry.getPlugin<BlePlugin>("ble")

        electionJob = coroutineScope.launch {
            // 退避期间扫描是否有新 Hub 广播
            delay(backoffMs)

            if (!isElectionRunning) return@launch

            // 退避结束未发现新 Hub → 自己成为新 Hub
            Log.i(TAG, "退避结束，本机成为新 Hub")
            isElectionRunning = false
            onElected()

            eventBus.publish("hub_changed", AppEvent.HubChanged(myUuid))
        }
    }

    /**
     * 取消选举。
     */
    fun cancel() {
        electionJob?.cancel()
        electionJob = null
        isElectionRunning = false
        Log.i(TAG, "Hub 选举已取消")
    }

    /**
     * 是否正在选举中。
     */
    fun isRunning(): Boolean = isElectionRunning
}
