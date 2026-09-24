package com.ephemeral.chat.plugins.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ephemeral.chat.core.eventbus.AppEvent
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.registry.PluginRegistry
import com.ephemeral.chat.plugins.ble.BlePlugin
import com.ephemeral.chat.plugins.crypto.CryptoPlugin
import com.ephemeral.chat.plugins.lifecycle.LifecyclePlugin
import com.ephemeral.chat.plugins.location.GeohashUtils
import com.ephemeral.chat.plugins.location.LocationPlugin
import com.ephemeral.chat.plugins.storage.StoragePlugin
import com.ephemeral.chat.plugins.storage.entity.GroupEntity
import com.ephemeral.chat.plugins.storage.entity.GroupStatus
import com.ephemeral.chat.plugins.storage.entity.MemberEntity
import com.ephemeral.chat.plugins.storage.entity.MemberStatus
import com.ephemeral.chat.plugins.storage.entity.MessageEntity
import com.ephemeral.chat.plugins.storage.entity.MessageType
import com.ephemeral.chat.protocol.ChatMessage
import com.ephemeral.chat.protocol.MessageType as ProtocolMessageType
import com.ephemeral.chat.SharedStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.security.PublicKey
import java.util.UUID
import javax.crypto.SecretKey
import javax.inject.Inject

/**
 * 聊天业务 ViewModel。
 * 职责：创建/加入群聊、消息收发、成员管理、退出与解散。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val registry: PluginRegistry,
    private val eventBus: EventBus,
) : ViewModel() {

    private val TAG = "ChatViewModel"

    /** 本机 UUID */
    private val myUuid: String = UUID.randomUUID().toString()

    /** 当前昵称——优先使用 SharedStateManager 中已持久化的昵称，无则生成随机昵称 */
    private var nickname: String = SharedStateManager.nickname.value
        .ifEmpty { NicknameGenerator.generate() }
        .also { SharedStateManager.setNickname(it) }

    /** 群数据加载 Job（切换群聊时取消旧的，避免重复收集） */
    private var memberLoadJob: Job? = null
    private var messageLoadJob: Job? = null

    /** UI 状态 */
    data class ChatUiState(
        val screen: Screen = Screen.Home,
        val inviteCode: String = "",
        val groupId: String = "",
        val nickname: String = "",
        val messages: List<MessageEntity> = emptyList(),
        val members: List<MemberEntity> = emptyList(),
        val isHub: Boolean = false,
        val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
        val errorMessage: String? = null,
        /** 是否已进入过聊天界面（用于首页区分“创建后等待”与“返回但不退出”） */
        val chatEntered: Boolean = false,
    )

    enum class Screen { Home, Create, Join, Chat, Members }
    enum class ConnectionStatus { Disconnected, Scanning, Connecting, Connected, Reconnecting }

    private val _uiState = MutableStateFlow(ChatUiState(nickname = nickname))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // 订阅 BLE 消息
        viewModelScope.launch {
            eventBus.subscribe("ble_message").collect { event ->
                if (event is AppEvent.MessageReceived) {
                    handleReceivedMessage(event.content)
                }
            }
        }

        // 订阅定位状态
        viewModelScope.launch {
            eventBus.subscribe("location_state").collect { event ->
                if (event is AppEvent.LocationStateChanged && !event.enabled) {
                    Log.i(TAG, "定位关闭，自动退出群聊")
                    leaveGroup()
                }
            }
        }
    }

    // ---- 创建群聊 ----

    /**
     * 创建群聊。
     * 1. 生成邀请码 2. 获取定位 3. 计算哈希 4. 启动广播和 GATT Server
     */
    fun createGroup() {
        val cryptoPlugin = registry.getPlugin<CryptoPlugin>("crypto") ?: return
        val locationPlugin = registry.getPlugin<LocationPlugin>("location") ?: return
        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return

        val code = cryptoPlugin.generateInviteCode()
        val groupId = UUID.randomUUID().toString()
        val groupIdShort = groupId.take(4).toByteArray()
        // 纯邀请码哈希（不依赖位置）：供扫描端兜底匹配，防止位置缓存异常导致搜不到
        val codeOnlyHash = cryptoPlugin.sha256(code).copyOfRange(0, 4)

        _uiState.value = _uiState.value.copy(screen = Screen.Create, inviteCode = code, groupId = groupId, isHub = true)

        locationPlugin.getCurrentLocation { lat, lon ->
            val geohash = GeohashUtils.encode(lat, lon, 5)
            val codeHash = cryptoPlugin.sha256(code + geohash).copyOfRange(0, 4)

            viewModelScope.launch {
                // 用户可能在定位等待期间取消了创建
                if (_uiState.value.groupId != groupId || _uiState.value.screen == Screen.Home) {
                    Log.i(TAG, "创建已取消，跳过后续流程")
                    return@launch
                }

                // 启动 BLE 广播（位置哈希优先 + 纯 code 哈希兑底）
                blePlugin.startAdvertising(codeHash, codeOnlyHash, groupIdShort) { errorMsg ->
                    _uiState.value = _uiState.value.copy(errorMessage = errorMsg)
                    Log.e(TAG, "广播失败: $errorMsg")
                }

                // 启动 GATT Server 接收消息
                blePlugin.startGattServer { data ->
                    handleReceivedMessage(String(data, Charsets.UTF_8))
                }

                // 存储群组信息
                val groupEntity = GroupEntity(
                    groupId = groupId,
                    code = code,
                    hubUuid = myUuid,
                    isHub = true,
                    createdAt = System.currentTimeMillis(),
                    memberCount = 1,
                    status = GroupStatus.ACTIVE,
                )
                storagePlugin.getGroupDao().upsert(groupEntity)

                // 存储自己为成员
                val selfMember = MemberEntity(
                    memberUuid = myUuid,
                    groupId = groupId,
                    nickname = nickname,
                    isSelf = true,
                    status = MemberStatus.ACTIVE,
                    lastHeartbeat = System.currentTimeMillis(),
                    joinedAt = System.currentTimeMillis(),
                )
                storagePlugin.getMemberDao().upsert(selfMember)

                // 启动心跳
                val lifecyclePlugin = registry.getPlugin<LifecyclePlugin>("lifecycle")
                lifecyclePlugin?.startHeartbeat(
                    isHub = true,
                    sendHeartbeat = {
                        val hbMsg = ChatMessage(
                            t = ProtocolMessageType.HB.code,
                            u = myUuid.take(4),
                            g = groupId.take(4),
                            ts = System.currentTimeMillis(),
                        )
                        blePlugin.broadcastToClients(hbMsg.toJson().toByteArray())
                    },
                    onMemberOffline = { uuid ->
                        _uiState.value = _uiState.value.copy(errorMessage = "$uuid 已离线")
                    },
                    onMemberLeft = { uuid ->
                        runDb {
                            storagePlugin.getMemberDao().delete(
                                storagePlugin.getMemberDao().getById(uuid, groupId) ?: return@runDb
                            )
                        }
                    },
                )

                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.Connected,
                    screen = Screen.Chat,
                    chatEntered = true,
                )
                loadGroupData(groupId)
                Log.i(TAG, "群聊创建成功, code=$code, groupId=$groupId")
            }
        }
    }

    // ---- 加入群聊 ----

    /**
     * 加入群聊。
     * 1. 获取定位 2. 计算 9 邻域哈希 3. 扫描匹配设备 4. 连接 Hub
     */
    fun joinGroup(code: String) {
        val cryptoPlugin = registry.getPlugin<CryptoPlugin>("crypto") ?: return
        val locationPlugin = registry.getPlugin<LocationPlugin>("location") ?: return
        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return

        _uiState.value = _uiState.value.copy(screen = Screen.Join, inviteCode = code, connectionStatus = ConnectionStatus.Scanning)

        // 纯邀请码哈希：位置哈希匹配不上时兑底匹配（解决位置缓存陈旧/定位不准导致搜不到）
        val codeOnlyHash = cryptoPlugin.sha256(code).copyOfRange(0, 4)

        locationPlugin.getCurrentLocation { lat, lon ->
            val geohash = GeohashUtils.encode(lat, lon, 5)
            val neighbors = GeohashUtils.neighbors(geohash)
            val codeHashes = neighbors.map { hash ->
                cryptoPlugin.sha256(code + hash).copyOfRange(0, 4)
            }

            blePlugin.startScanning(
                codeHashes,
                codeOnlyHash,
                { deviceAddress, groupIdShort ->
                    _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Connecting)

                    // 加入者生成自己的本地 groupId（与 Hub 的短码关联）
                    val localGroupId = "join_" + UUID.randomUUID().toString().take(8)
                    _uiState.value = _uiState.value.copy(
                        groupId = localGroupId,
                        isHub = false,
                        inviteCode = code,
                    )
                    val storagePlugin = registry.getPlugin<StoragePlugin>("storage")
                    runDb {
                        val sp = storagePlugin ?: return@runDb
                        // 保存群组信息（加入者视角）
                        sp.getGroupDao().upsert(
                            GroupEntity(
                                groupId = localGroupId,
                                code = code,
                                hubUuid = deviceAddress,
                                isHub = false,
                                createdAt = System.currentTimeMillis(),
                                memberCount = 1,
                                status = GroupStatus.ACTIVE,
                            )
                        )
                        // 保存自己为成员
                        sp.getMemberDao().upsert(
                            MemberEntity(
                                memberUuid = myUuid,
                                groupId = localGroupId,
                                nickname = nickname,
                                isSelf = true,
                                status = MemberStatus.ACTIVE,
                                lastHeartbeat = System.currentTimeMillis(),
                                joinedAt = System.currentTimeMillis(),
                            )
                        )
                    }

                    // 距离校验
                    locationPlugin.getCurrentLocation { lat2, lon2 ->
                        val distance = GeohashUtils.distanceMeters(lat, lon, lat2, lon2)
                        if (distance > 100.0) {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "距离过远（${distance.toInt()}米），请靠近后重试",
                                connectionStatus = ConnectionStatus.Disconnected,
                            )
                            return@getCurrentLocation
                        }

                        // 连接 Hub
                        blePlugin.connectAsClient(deviceAddress) { data ->
                            handleReceivedMessage(String(data, Charsets.UTF_8))
                        }

                        // 发送 join 消息
                        viewModelScope.launch {
                            try {
                                delay(1000) // 等待连接建立
                                val joinMsg = ChatMessage(
                                    t = ProtocolMessageType.JOIN.code,
                                    u = myUuid.take(4),
                                    n = nickname,
                                    g = String(groupIdShort, Charsets.UTF_8),
                                    ts = System.currentTimeMillis(),
                                    mid = UUID.randomUUID().toString(),
                                )
                                blePlugin.writeToHub(joinMsg.toJson().toByteArray())
                            } catch (e: Exception) {
                                Log.e(TAG, "发送 join 消息失败", e)
                            }
                        }
                    }
                },
                { errorMsg ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = errorMsg,
                        connectionStatus = ConnectionStatus.Disconnected,
                    )
                    Log.e(TAG, "扫描失败: $errorMsg")
                },
            )
        }
    }

    // ---- 发送消息 ----

    /**
     * 发送文本消息。
     * 乐观更新 UI，然后通过 BLE 发送。
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return
        val cryptoPlugin = registry.getPlugin<CryptoPlugin>("crypto") ?: return

        val groupId = _uiState.value.groupId
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // 加密内容
        val groupKey = cryptoPlugin.getGroupKey(groupId)
        val encryptedContent = if (groupKey != null) {
            cryptoPlugin.base64Encode(cryptoPlugin.encrypt(content.toByteArray(), groupKey))
        } else {
            content // 无密钥时明文（Phase 4 完善加密）
        }

        val chatMsg = ChatMessage(
            t = ProtocolMessageType.MSG.code,
            u = myUuid.take(4),
            n = nickname,
            c = encryptedContent,
            g = groupId.take(4),
            ts = timestamp,
            mid = msgId,
        )

        // 先存本地（乐观更新），UI 通过数据流自动刷新
        runDb {
            val msgEntity = MessageEntity(
                msgId = msgId,
                groupId = groupId,
                senderUuid = myUuid,
                senderName = nickname,
                content = content, // 本地存明文
                type = MessageType.TEXT,
                timestamp = timestamp,
                isDelivered = false,
            )
            storagePlugin.getMessageDao().upsert(msgEntity)
            Log.d(TAG, "消息已存本地: $content")
        }

        // 通过 BLE 发送（失败不闪退，仅记录日志）
        try {
            val data = chatMsg.toJson().toByteArray()
            if (_uiState.value.isHub) {
                blePlugin.broadcastToClients(data)
            } else {
                blePlugin.writeToHub(data)
            }
            Log.d(TAG, "消息已发送: $content")
        } catch (e: Exception) {
            Log.e(TAG, "BLE 发送消息异常", e)
        }
    }

    // ---- 修改用户名 ----

    /**
     * 修改昵称并广播通知。
     */
    fun changeNickname(newName: String) {
        if (newName.isBlank()) return

        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return
        val groupId = _uiState.value.groupId
        val oldName = nickname

        nickname = newName
        SharedStateManager.setNickname(newName)
        _uiState.value = _uiState.value.copy(nickname = newName)

        val nickMsg = ChatMessage(
            t = ProtocolMessageType.NICK.code,
            u = myUuid.take(4),
            n = newName,
            g = groupId.take(4),
            ts = System.currentTimeMillis(),
            mid = UUID.randomUUID().toString(),
        )

        // 更新本地成员
        runDb {
            val member = storagePlugin.getMemberDao().getById(myUuid, groupId)
            if (member != null) {
                storagePlugin.getMemberDao().upsert(member.copy(nickname = newName))
            }
        }

        // 广播
        try {
            val data = nickMsg.toJson().toByteArray()
            if (_uiState.value.isHub) {
                blePlugin.broadcastToClients(data)
            } else {
                blePlugin.writeToHub(data)
            }
            Log.i(TAG, "昵称已修改: $oldName → $newName")
        } catch (e: Exception) {
            Log.e(TAG, "昵称广播失败", e)
        }
    }

    // ---- 退出群聊 ----

    /**
     * 退出群聊。发送 leave 消息，清除本地数据，停止 BLE。
     */
    fun leaveGroup() {
        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return
        val lifecyclePlugin = registry.getPlugin<LifecyclePlugin>("lifecycle")
        val groupId = _uiState.value.groupId

        viewModelScope.launch {
            // 发送 leave 消息
            try {
                val leaveMsg = ChatMessage(
                    t = ProtocolMessageType.LEAVE.code,
                    u = myUuid.take(4),
                    g = groupId.take(4),
                    ts = System.currentTimeMillis(),
                )
                val data = leaveMsg.toJson().toByteArray()
                if (_uiState.value.isHub) {
                    blePlugin.broadcastToClients(data)
                } else {
                    blePlugin.writeToHub(data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送 leave 消息失败", e)
            }

            // 停止心跳
            lifecyclePlugin?.stopHeartbeat()

            // 清除本地数据
            try {
                storagePlugin.clearGroupData(groupId)
            } catch (e: Exception) {
                Log.e(TAG, "清除群数据失败", e)
            }

            // 停止 BLE
            try {
                blePlugin.stopAdvertising()
                blePlugin.stopScanning()
                blePlugin.stopGattServer()
                blePlugin.disconnectClient()
            } catch (e: Exception) {
                Log.e(TAG, "停止 BLE 失败", e)
            }

            // 回到首页
            _uiState.value = ChatUiState(nickname = nickname)
            Log.i(TAG, "已退出群聊")
        }
    }

    // ---- 解散群聊 ----

    /**
     * 解散群聊（仅 Hub 可操作）。
     */
    fun dissolveGroup() {
        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return
        val lifecyclePlugin = registry.getPlugin<LifecyclePlugin>("lifecycle")
        val groupId = _uiState.value.groupId

        viewModelScope.launch {
            // 发送 dissolve 通知
            try {
                val dissolveMsg = ChatMessage(
                    t = ProtocolMessageType.DISSOLVE.code,
                    u = myUuid.take(4),
                    g = groupId.take(4),
                    ts = System.currentTimeMillis(),
                )
                blePlugin.broadcastToClients(dissolveMsg.toJson().toByteArray())
            } catch (e: Exception) {
                Log.e(TAG, "发送 dissolve 失败", e)
            }

            // 停止心跳
            lifecyclePlugin?.stopHeartbeat()

            // 清除所有数据
            try {
                storagePlugin.clearAll()
            } catch (e: Exception) {
                Log.e(TAG, "清空数据库失败", e)
            }

            // 停止 BLE
            try {
                blePlugin.stopAdvertising()
                blePlugin.stopGattServer()
            } catch (e: Exception) {
                Log.e(TAG, "停止 BLE 失败", e)
            }

            // 回到首页
            _uiState.value = ChatUiState(nickname = nickname)
            Log.i(TAG, "群聊已解散")
        }
    }

    // ---- 接收消息处理 ----

    /**
     * 处理收到的消息。
     * 解析 JSON → 按 t 分发到对应处理逻辑。
     */
    private fun handleReceivedMessage(rawData: String) {
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return
        val cryptoPlugin = registry.getPlugin<CryptoPlugin>("crypto") ?: return
        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return
        val lifecyclePlugin = registry.getPlugin<LifecyclePlugin>("lifecycle")

        try {
            val msg = ChatMessage.fromJson(rawData)
            val groupId = _uiState.value.groupId

            when (ProtocolMessageType.fromCode(msg.t)) {
                ProtocolMessageType.JOIN -> {
                    // Hub 处理加入请求
                    val newMember = MemberEntity(
                        memberUuid = msg.u,
                        groupId = groupId,
                        nickname = msg.n ?: NicknameGenerator.generate(),
                        isSelf = false,
                        status = MemberStatus.ACTIVE,
                        lastHeartbeat = System.currentTimeMillis(),
                        joinedAt = System.currentTimeMillis(),
                    )
                    runDb {
                        storagePlugin.getMemberDao().upsert(newMember)
                        lifecyclePlugin?.addMember(msg.u)
                    }

                    // 回复 join_ack + 广播系统消息（失败不影响主流程）
                    try {
                        val ackMsg = ChatMessage(
                            t = ProtocolMessageType.JOIN_ACK.code,
                            u = myUuid.take(4),
                            n = nickname,
                            g = groupId.take(4),
                            ts = System.currentTimeMillis(),
                            mid = UUID.randomUUID().toString(),
                        )
                        blePlugin.broadcastToClients(ackMsg.toJson().toByteArray())

                        val sysMsg = ChatMessage(
                            t = ProtocolMessageType.SYS.code,
                            u = myUuid.take(4),
                            c = "${msg.n ?: "新成员"} 加入了群聊",
                            g = groupId.take(4),
                            ts = System.currentTimeMillis(),
                            mid = UUID.randomUUID().toString(),
                        )
                        blePlugin.broadcastToClients(sysMsg.toJson().toByteArray())
                    } catch (e: Exception) {
                        Log.e(TAG, "JOIN 回复广播失败", e)
                    }
                }

                ProtocolMessageType.JOIN_ACK -> {
                    // Client 处理加入确认
                    _uiState.value = _uiState.value.copy(
                        screen = Screen.Chat,
                        connectionStatus = ConnectionStatus.Connected,
                        chatEntered = true,
                    )
                    loadGroupData(_uiState.value.groupId)
                }

                ProtocolMessageType.MSG -> {
                    // 解密消息内容
                    val groupKey = cryptoPlugin.getGroupKey(groupId)
                    val content = if (msg.c != null && groupKey != null) {
                        try {
                            String(cryptoPlugin.decrypt(cryptoPlugin.base64Decode(msg.c), groupKey))
                        } catch (e: Exception) {
                            Log.w(TAG, "消息解密失败，使用原始内容")
                            msg.c
                        }
                    } else {
                        msg.c ?: ""
                    }

                    runDb {
                        val msgEntity = MessageEntity(
                            msgId = msg.mid ?: UUID.randomUUID().toString(),
                            groupId = groupId,
                            senderUuid = msg.u,
                            senderName = msg.n ?: "未知",
                            content = content,
                            type = MessageType.TEXT,
                            timestamp = msg.ts,
                            isDelivered = true,
                        )
                        storagePlugin.getMessageDao().upsert(msgEntity)
                    }
                }

                ProtocolMessageType.SYS -> {
                    runDb {
                        val msgEntity = MessageEntity(
                            msgId = msg.mid ?: UUID.randomUUID().toString(),
                            groupId = groupId,
                            senderUuid = msg.u,
                            senderName = "系统",
                            content = msg.c ?: "",
                            type = MessageType.SYSTEM,
                            timestamp = msg.ts,
                            isDelivered = true,
                        )
                        storagePlugin.getMessageDao().upsert(msgEntity)
                    }
                }

                ProtocolMessageType.HB -> {
                    lifecyclePlugin?.onHeartbeatReceived(msg.u)
                }

                ProtocolMessageType.LEAVE -> {
                    runDb {
                        val member = storagePlugin.getMemberDao().getById(msg.u, groupId)
                        if (member != null) {
                            storagePlugin.getMemberDao().upsert(member.copy(status = MemberStatus.LEFT))
                        }
                        lifecyclePlugin?.removeMember(msg.u)

                        // 系统消息
                        val sysEntity = MessageEntity(
                            msgId = UUID.randomUUID().toString(),
                            groupId = groupId,
                            senderUuid = msg.u,
                            senderName = "系统",
                            content = "${msg.n ?: "成员"} 离开了群聊",
                            type = MessageType.SYSTEM,
                            timestamp = System.currentTimeMillis(),
                            isDelivered = true,
                        )
                        storagePlugin.getMessageDao().upsert(sysEntity)
                    }
                }

                ProtocolMessageType.NICK -> {
                    runDb {
                        val member = storagePlugin.getMemberDao().getById(msg.u, groupId)
                        if (member != null) {
                            val oldName = member.nickname
                            storagePlugin.getMemberDao().upsert(member.copy(nickname = msg.n ?: member.nickname))

                            // 系统消息
                            val sysEntity = MessageEntity(
                                msgId = UUID.randomUUID().toString(),
                                groupId = groupId,
                                senderUuid = msg.u,
                                senderName = "系统",
                                content = "$oldName 已改名为 ${msg.n}",
                                type = MessageType.SYSTEM,
                                timestamp = System.currentTimeMillis(),
                                isDelivered = true,
                            )
                            storagePlugin.getMessageDao().upsert(sysEntity)
                        }
                    }
                }

                ProtocolMessageType.DISSOLVE -> {
                    runDb {
                        storagePlugin.clearAll()
                        try { blePlugin.disconnectClient() } catch (e: Exception) { Log.e(TAG, "断开连接失败", e) }
                    }
                    _uiState.value = ChatUiState(nickname = nickname)
                }

                else -> {
                    Log.d(TAG, "未处理的消息类型: ${msg.t}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "消息处理失败", e)
        }
    }

    /**
     * 导航到加入群聊页面。
     */
    fun navigateToJoin() {
        _uiState.value = _uiState.value.copy(screen = Screen.Join, errorMessage = null)
    }

    /**
     * 进入聊天界面（创建群聊或加入成功后调用）。
     */
    fun enterChat() {
        _uiState.value = _uiState.value.copy(screen = Screen.Chat, chatEntered = true)
        loadGroupData(_uiState.value.groupId)
    }

    /**
     * 导航到成员列表页。
     */
    fun showMembers() {
        _uiState.value = _uiState.value.copy(screen = Screen.Members)
    }

    /**
     * 返回聊天页。
     */
    fun backToChat() {
        _uiState.value = _uiState.value.copy(screen = Screen.Chat)
    }

    /**
     * 返回首页。
     */
    fun backToHome() {
        _uiState.value = _uiState.value.copy(screen = Screen.Home)
    }

    /**
     * 返回首页但不退出群聊。
     * 保留群组状态（groupId/inviteCode/isHub），首页显示群聊列表。
     */
    fun backToHomeKeepGroup() {
        _uiState.value = _uiState.value.copy(screen = Screen.Home)
    }

    /**
     * 从首页群聊列表重新进入聊天。
     */
    fun reenterGroup() {
        val current = _uiState.value
        if (current.groupId.isNotEmpty()) {
            _uiState.value = current.copy(screen = Screen.Chat, chatEntered = true)
            loadGroupData(current.groupId)
        }
    }

    /**
     * 是否处于活跃群聊中（groupId 非空即认为有群聊）。
     */
    fun hasActiveGroup(): Boolean = _uiState.value.groupId.isNotEmpty()

    /**
     * 清除错误消息。
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ---- 数据加载与安全数据库访问 ----

    /**
     * 加载指定群聊的成员与消息列表到 UI 状态（Room Flow 实时订阅）。
     * 切换群聊时先取消旧订阅，避免重复收集。
     */
    private fun loadGroupData(groupId: String) {
        if (groupId.isEmpty()) return
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return

        memberLoadJob?.cancel()
        messageLoadJob?.cancel()

        memberLoadJob = viewModelScope.launch {
            try {
                storagePlugin.getMemberDao().observeByGroupId(groupId).collect { members ->
                    _uiState.value = _uiState.value.copy(members = members)
                }
            } catch (e: Exception) {
                Log.e(TAG, "成员数据流中断", e)
            }
        }
        messageLoadJob = viewModelScope.launch {
            try {
                storagePlugin.getMessageDao().observeByGroupId(groupId).collect { messages ->
                    _uiState.value = _uiState.value.copy(messages = messages)
                }
            } catch (e: Exception) {
                Log.e(TAG, "消息数据流中断", e)
            }
        }
    }

    /**
     * 安全执行数据库操作：任何异常只记录日志，绝不导致应用崩溃。
     */
    private fun runDb(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "数据库操作失败", e)
            }
        }
    }

    /**
     * 取消创建：停止 BLE 活动并回到干净的首页（用于创建等待页的“取消”）。
     */
    fun cancelGroupCreation() {
        try {
            registry.getPlugin<LifecyclePlugin>("lifecycle")?.stopHeartbeat()
            registry.getPlugin<BlePlugin>("ble")?.stopAdvertising()
            registry.getPlugin<BlePlugin>("ble")?.stopScanning()
            registry.getPlugin<BlePlugin>("ble")?.stopGattServer()
            registry.getPlugin<BlePlugin>("ble")?.disconnectClient()
        } catch (e: Exception) {
            Log.e(TAG, "取消创建时清理失败", e)
        }
        _uiState.value = ChatUiState(nickname = nickname)
        Log.i(TAG, "已取消创建群聊")
    }
}
