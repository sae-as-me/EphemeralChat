package com.ephemeral.chat.plugins.lifecycle

import android.util.Log
import com.ephemeral.chat.core.eventbus.AppEvent
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 生命周期管理插件。
 * 职责：心跳管理、Hub 重选、自动解散。
 * 依赖：ble, storage
 */
class LifecyclePlugin : IPlugin {
    override val pluginId = "lifecycle"
    override val dependencies = listOf("ble", "storage")

    private val TAG = "LifecyclePlugin"
    private lateinit var eventBus: EventBus
    private lateinit var registry: PluginRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var heartbeatManager: HeartbeatManager? = null
    private var hubElection: HubElection? = null

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        this.registry = registry
        this.eventBus = eventBus
        heartbeatManager = HeartbeatManager(eventBus, scope)
        hubElection = HubElection(registry, eventBus, scope)
        Log.i(TAG, "生命周期插件初始化完成")
    }

    override fun onStart() {}

    override fun onStop() {
        heartbeatManager?.stop()
        hubElection?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        Log.i(TAG, "生命周期插件已销毁")
    }

    /**
     * 启动心跳管理。
     */
    fun startHeartbeat(
        isHub: Boolean,
        sendHeartbeat: () -> Unit,
        onMemberOffline: (String) -> Unit,
        onMemberLeft: (String) -> Unit,
    ) {
        heartbeatManager?.start(isHub, sendHeartbeat, onMemberOffline, onMemberLeft)
    }

    /**
     * 停止心跳管理。
     */
    fun stopHeartbeat() {
        heartbeatManager?.stop()
    }

    /**
     * 记录成员心跳。
     */
    fun onHeartbeatReceived(uuid: String) {
        heartbeatManager?.onHeartbeatReceived(uuid)
    }

    /**
     * 添加成员到心跳追踪。
     */
    fun addMember(uuid: String) {
        heartbeatManager?.addMember(uuid)
    }

    /**
     * 移除成员。
     */
    fun removeMember(uuid: String) {
        heartbeatManager?.removeMember(uuid)
    }

    /**
     * 启动 Hub 选举。
     */
    fun startElection(
        myUuid: String,
        groupId: String,
        groupIdShort: ByteArray,
        onElected: () -> Unit,
        onFoundHub: (String) -> Unit,
    ) {
        hubElection?.startElection(myUuid, groupId, groupIdShort, onElected, onFoundHub)
    }

    /**
     * 取消 Hub 选举。
     */
    fun cancelElection() {
        hubElection?.cancel()
    }
}
