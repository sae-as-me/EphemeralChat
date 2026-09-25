package com.ephemeral.chat.plugins.chat

import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ephemeral.chat.EphemeralChatApplication
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
import com.ephemeral.chat.service.EphemeralForegroundService
import com.ephemeral.chat.service.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import org.json.JSONObject
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

    /** 本机 UUID 短码（4 位），用于协议传输与本地成员存储的主键，保证自consistent */
    private val myUuidShort: String = myUuid.take(4)

    /** 当前昵称——优先使用 SharedStateManager 中已持久化的昵称，无则生成随机昵称 */
    private var nickname: String = SharedStateManager.nickname.value
        .ifEmpty { NicknameGenerator.generate() }
        .also { SharedStateManager.setNickname(it) }

    /** 群数据加载 Job（切换群聊时取消旧的，避免重复收集） */
    private var memberLoadJob: Job? = null
    private var messageLoadJob: Job? = null

    /** JOIN_ACK 等待超时保护 Job */
    private var joinAckTimeoutJob: Job? = null

    /** UI 状态 */
    data class ChatUiState(
        val screen: Screen = Screen.Home,
        val inviteCode: String = "",
        val groupId: String = "",
        val nickname: String = "",
        val myUuidShort: String = "",
        val messages: List<MessageEntity> = emptyList(),
        val members: List<MemberEntity> = emptyList(),
        val isHub: Boolean = false,
        val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
        val errorMessage: String? = null,
        /** 是否已进入过聊天界面 */
        val chatEntered: Boolean = false,
        /** 文件传输进度（0~100，-1 表示无传输） */
        val fileTransferProgress: Int = -1,
        /** 文件传输状态文本 */
        val fileTransferStatus: String = "",
        /** 全屏预览图片的本地路径（非空时显示预览） */
        val previewImagePath: String? = null,
    )

    enum class Screen { Home, Create, Join, Chat, Members }
    enum class ConnectionStatus { Disconnected, Scanning, Connecting, Connected, Reconnecting }

    private val _uiState = MutableStateFlow(ChatUiState(nickname = nickname, myUuidShort = myUuidShort))
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

        // 预检：设备不支持 BLE 广播时无法创建群聊，提前提示（避免创建后对方搜不到）
        if (!blePlugin.canAdvertise()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "当前设备不支持发起群聊（BLE 广播不可用），请由对方设备创建群聊后加入",
            )
            Log.w(TAG, "设备不支持 BLE 广播，无法创建群聊")
            return
        }

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
                try {
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
                        memberUuid = myUuidShort,
                        groupId = groupId,
                        nickname = nickname,
                        isSelf = true,
                        status = MemberStatus.ACTIVE,
                        lastHeartbeat = System.currentTimeMillis(),
                        joinedAt = System.currentTimeMillis(),
                    )
                    storagePlugin.getMemberDao().upsert(selfMember)
                } catch (e: Exception) {
                    Log.e(TAG, "存储群组信息失败", e)
                    // DB 失败不应阻断群聊（BLE 已启动，聊天仍可用）
                }

                // 启动心跳
                val lifecyclePlugin = registry.getPlugin<LifecyclePlugin>("lifecycle")
                lifecyclePlugin?.startHeartbeat(
                    isHub = true,
                    sendHeartbeat = {
                        val hbMsg = ChatMessage(
                            t = ProtocolMessageType.HB.code,
                            u = myUuidShort,
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
                startForegroundService()
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
                                memberUuid = myUuidShort,
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

                        // 发送 join 消息（等待 GATT 连接 + 通知订阅就绪后再发，不再盲等 1 秒）
                        viewModelScope.launch {
                            try {
                                var ready = false
                                repeat(20) { // 最多等待 10 秒
                                    if (blePlugin.isClientReady()) {
                                        ready = true
                                        return@repeat
                                    }
                                    delay(500)
                                }
                                if (!ready) {
                                    Log.w(TAG, "GATT 连接就绪超时")
                                    _uiState.value = _uiState.value.copy(
                                        errorMessage = "连接超时，请确认对方设备蓝牙已开启后重试",
                                        connectionStatus = ConnectionStatus.Disconnected,
                                    )
                                    return@launch
                                }

                                val joinMsg = ChatMessage(
                                    t = ProtocolMessageType.JOIN.code,
                                    u = myUuidShort,
                                    n = nickname,
                                    g = String(groupIdShort, Charsets.UTF_8),
                                    ts = System.currentTimeMillis(),
                                    mid = UUID.randomUUID().toString(),
                                )
                                val sent = blePlugin.writeToHub(joinMsg.toJson().toByteArray())
                                if (sent) {
                                    Log.i(TAG, "JOIN 消息已发送")
                                    startJoinAckTimeout()
                                } else {
                                    Log.e(TAG, "JOIN 写入失败")
                                    _uiState.value = _uiState.value.copy(
                                        errorMessage = "加入消息发送失败，请重试",
                                        connectionStatus = ConnectionStatus.Disconnected,
                                    )
                                }
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
            u = myUuidShort,
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
                senderUuid = myUuidShort,
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

    // ---- 文件/图片发送 ----

    /**
     * 发送图片（从文件路径，已压缩或待压缩）。
     * 流程：压缩 → base64 → 保存本地 → 存消息记录 → BLE 发送
     */
    fun sendImage(imagePath: String, originalName: String = "image.jpg") {
        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return
        val groupId = _uiState.value.groupId
        val context = EphemeralChatApplication.get()
        val msgId = UUID.randomUUID().toString()
        val fileId = UUID.randomUUID().toString().take(8)
        val timestamp = System.currentTimeMillis()
        val sourceFile = java.io.File(imagePath)

        _uiState.value = _uiState.value.copy(fileTransferProgress = 0, fileTransferStatus = "正在压缩图片...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 压缩图片
                val (base64Data, compressedSize) = FileTransferHelper.compressImageFileToBase64(imagePath)
                    ?: run {
                        _uiState.value = _uiState.value.copy(fileTransferProgress = -1, errorMessage = "图片压缩失败")
                        sourceFile.delete()
                        return@launch
                    }

                // 保存到本地临时目录
                val localPath = FileTransferHelper.saveBase64ToFile(context, groupId, fileId, base64Data, originalName)

                // 构造元信息 JSON（存入消息 content）
                val meta = FileTransferHelper.FileMetaData(
                    fileName = originalName,
                    mimeType = "image/jpeg",
                    fileSize = compressedSize.toLong(),
                    isImage = true,
                    localPath = localPath,
                )

                // 存本地消息记录
                val msgEntity = MessageEntity(
                    msgId = msgId,
                    groupId = groupId,
                    senderUuid = myUuidShort,
                    senderName = nickname,
                    content = meta.toJson(),
                    type = MessageType.IMAGE,
                    timestamp = timestamp,
                    isDelivered = false,
                )
                storagePlugin.getMessageDao().upsert(msgEntity)

                // 构造 BLE 消息（元信息+base64数据一起发送）
                val payload = JSONObject().apply {
                    put("meta", meta.toJson())
                    put("data", base64Data)
                }.toString()

                val fileMsg = ChatMessage(
                    t = ProtocolMessageType.FILE.code,
                    u = myUuidShort,
                    n = nickname,
                    c = payload,
                    g = groupId.take(4),
                    ts = timestamp,
                    mid = msgId,
                )

                _uiState.value = _uiState.value.copy(fileTransferProgress = 50, fileTransferStatus = "正在发送图片...")

                val data = fileMsg.toJson().toByteArray()
                if (_uiState.value.isHub) {
                    blePlugin.broadcastToClients(data)
                } else {
                    blePlugin.writeToHub(data)
                }

                _uiState.value = _uiState.value.copy(fileTransferProgress = -1, fileTransferStatus = "")
                Log.i(TAG, "图片已发送: $originalName, $compressedSize bytes")
                // 修复：清理选择器产生的临时文件
                sourceFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "发送图片失败", e)
                _uiState.value = _uiState.value.copy(fileTransferProgress = -1, errorMessage = "发送图片失败: ${e.message}")
                sourceFile.delete()
            }
        }
    }

    /**
     * 发送文件（非图片）。
     * 限制：≤ 10MB。
     */
    fun sendFile(filePath: String, fileName: String, fileSize: Long, mimeType: String) {
        if (!FileTransferHelper.isFileSizeValid(fileSize)) {
            _uiState.value = _uiState.value.copy(errorMessage = "文件超过 10MB 上限")
            return
        }

        // 内存安全检查
        val runtime = Runtime.getRuntime()
        val freeMem = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val estimatedBase64Size = (fileSize * 1.34).toLong()
        if (estimatedBase64Size > freeMem / 3) {
            _uiState.value = _uiState.value.copy(errorMessage = "文件过大，设备内存不足，请尝试发送更小的文件")
            Log.w(TAG, "内存不足: free=$freeMem, base64Est=$estimatedBase64Size")
            return
        }

        val blePlugin = registry.getPlugin<BlePlugin>("ble") ?: return
        val storagePlugin = registry.getPlugin<StoragePlugin>("storage") ?: return
        val groupId = _uiState.value.groupId
        val context = EphemeralChatApplication.get()
        val msgId = UUID.randomUUID().toString()
        val fileId = UUID.randomUUID().toString().take(8)
        val timestamp = System.currentTimeMillis()
        val sourceFile = java.io.File(filePath)

        _uiState.value = _uiState.value.copy(fileTransferProgress = 0, fileTransferStatus = "正在读取文件...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (base64Data, actualSize) = FileTransferHelper.readFileToBase64(filePath)
                    ?: run {
                        _uiState.value = _uiState.value.copy(fileTransferProgress = -1, errorMessage = "文件读取失败")
                        return@launch
                    }

                val localPath = FileTransferHelper.saveBase64ToFile(context, groupId, fileId, base64Data, fileName)

                val meta = FileTransferHelper.FileMetaData(
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = actualSize.toLong(),
                    isImage = false,
                    localPath = localPath,
                )

                val msgEntity = MessageEntity(
                    msgId = msgId,
                    groupId = groupId,
                    senderUuid = myUuidShort,
                    senderName = nickname,
                    content = meta.toJson(),
                    type = MessageType.FILE,
                    timestamp = timestamp,
                    isDelivered = false,
                )
                storagePlugin.getMessageDao().upsert(msgEntity)

                val payload = JSONObject().apply {
                    put("meta", meta.toJson())
                    put("data", base64Data)
                }.toString()

                val fileMsg = ChatMessage(
                    t = ProtocolMessageType.FILE.code,
                    u = myUuidShort,
                    n = nickname,
                    c = payload,
                    g = groupId.take(4),
                    ts = timestamp,
                    mid = msgId,
                )

                _uiState.value = _uiState.value.copy(fileTransferProgress = 50, fileTransferStatus = "正在发送文件...")

                val data = fileMsg.toJson().toByteArray()
                if (_uiState.value.isHub) {
                    blePlugin.broadcastToClients(data)
                } else {
                    blePlugin.writeToHub(data)
                }

                _uiState.value = _uiState.value.copy(fileTransferProgress = -1, fileTransferStatus = "")
                Log.i(TAG, "文件已发送: $fileName, $actualSize bytes")
                // 修复：清理选择器产生的临时文件
                sourceFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "发送文件失败", e)
                _uiState.value = _uiState.value.copy(fileTransferProgress = -1, errorMessage = "发送文件失败: ${e.message}")
                sourceFile.delete()
            }
        }
    }

    /**
     * 另存图片到系统相册。
     */
    fun saveImageToGallery(localPath: String, fileName: String) {
        val context = EphemeralChatApplication.get()
        viewModelScope.launch(Dispatchers.IO) {
            val uri = FileTransferHelper.saveImageToGallery(context, localPath, fileName)
            _uiState.value = _uiState.value.copy(
                errorMessage = if (uri != null) "已保存到相册" else "保存失败",
            )
        }
    }

    /**
     * 另存文件到下载目录。
     */
    fun saveFileToDownloads(localPath: String, fileName: String, mimeType: String) {
        val context = EphemeralChatApplication.get()
        viewModelScope.launch(Dispatchers.IO) {
            val uri = FileTransferHelper.saveFileToDownloads(context, localPath, fileName, mimeType)
            _uiState.value = _uiState.value.copy(
                errorMessage = if (uri != null) "已保存到下载目录" else "保存失败",
            )
        }
    }

    /**
     * 打开图片全屏预览。
     */
    fun openImagePreview(localPath: String) {
        _uiState.value = _uiState.value.copy(previewImagePath = localPath)
    }

    /**
     * 关闭图片预览。
     */
    fun closeImagePreview() {
        _uiState.value = _uiState.value.copy(previewImagePath = null)
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
            u = myUuidShort,
            n = newName,
            g = groupId.take(4),
            ts = System.currentTimeMillis(),
            mid = UUID.randomUUID().toString(),
        )

        // 更新本地成员
        runDb {
            val member = storagePlugin.getMemberDao().getById(myUuidShort, groupId)
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
                    u = myUuidShort,
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
            _uiState.value = ChatUiState(nickname = nickname, myUuidShort = myUuidShort)
            // 修复：取消 Room Flow 订阅，防止资源泄漏
            memberLoadJob?.cancel()
            messageLoadJob?.cancel()
            stopForegroundService()
            FileTransferHelper.clearAllFiles(EphemeralChatApplication.get())
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
                    u = myUuidShort,
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
            _uiState.value = ChatUiState(nickname = nickname, myUuidShort = myUuidShort)
            // 修复：取消 Room Flow 订阅
            memberLoadJob?.cancel()
            messageLoadJob?.cancel()
            stopForegroundService()
            FileTransferHelper.clearAllFiles(EphemeralChatApplication.get())
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

            // 修复：群聊已退出时丢弃消息，防止存入孤儿数据
            if (groupId.isEmpty()) {
                Log.w(TAG, "收到消息但 groupId 为空，已丢弃: type=${msg.t}")
                return
            }

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

                        // 广播全量成员同步：让新加入者和已有成员都能看到完整列表
                        val allMembers = storagePlugin.getMemberDao().getByGroupId(groupId)
                        for (m in allMembers) {
                            val syncMsg = ChatMessage(
                                t = ProtocolMessageType.MEMBER_SYNC.code,
                                u = m.memberUuid,
                                n = m.nickname,
                                g = groupId.take(4),
                                ts = System.currentTimeMillis(),
                                mid = UUID.randomUUID().toString(),
                            )
                            blePlugin.broadcastToClients(syncMsg.toJson().toByteArray())
                            // 修复：成员同步消息间加小延迟，避免 BLE 通知栈拥塞丢消息
                            delay(50)
                        }
                        Log.i(TAG, "已广播 ${allMembers.size} 条 MEMBER_SYNC")
                    }

                    // 回复 join_ack + 广播系统消息（失败不影响主流程）
                    try {
                        val ackMsg = ChatMessage(
                            t = ProtocolMessageType.JOIN_ACK.code,
                            u = myUuidShort,
                            n = nickname,
                            g = groupId.take(4),
                            ts = System.currentTimeMillis(),
                            mid = UUID.randomUUID().toString(),
                        )
                        blePlugin.broadcastToClients(ackMsg.toJson().toByteArray())

                        val sysMsg = ChatMessage(
                            t = ProtocolMessageType.SYS.code,
                            u = myUuidShort,
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
                    // Client 处理加入确认：跳转群聊 + 本地欢迎系统消息
                    joinAckTimeoutJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        screen = Screen.Chat,
                        connectionStatus = ConnectionStatus.Connected,
                        chatEntered = true,
                    )
                    loadGroupData(_uiState.value.groupId)
                    startForegroundService()

                    // 本地插入“已加入群聊”欢迎消息
                    runDb {
                        storagePlugin.getMessageDao().upsert(
                            MessageEntity(
                                msgId = UUID.randomUUID().toString(),
                                groupId = _uiState.value.groupId,
                                senderUuid = myUuidShort,
                                senderName = "系统",
                                content = "$nickname 已加入群聊",
                                type = MessageType.SYSTEM,
                                timestamp = System.currentTimeMillis(),
                                isDelivered = true,
                            )
                        )
                    }
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

                    // 后台弹窗通知
                    if (msg.u != myUuidShort) {
                        maybeNotifyBackgroundNotification(msg.n ?: "新消息", content)
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

                    // 后台弹窗提醒（系统消息如成员加入/离开）
                    if (msg.u != myUuidShort) {
                        maybeNotifyBackgroundNotification("系统消息", msg.c ?: "")
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
                    _uiState.value = ChatUiState(nickname = nickname, myUuidShort = myUuidShort)
                    // 修复：取消 Room Flow 订阅
                    memberLoadJob?.cancel()
                    messageLoadJob?.cancel()
                    stopForegroundService()
                }

                ProtocolMessageType.MEMBER_SYNC -> {
                    // 收到 Hub 广播的成员同步：更新本地成员表
                    val memberId = msg.u
                    val memberName = msg.n ?: "未知"
                    val isMe = memberId == myUuidShort
                    runDb {
                        val existing = storagePlugin.getMemberDao().getById(memberId, groupId)
                        if (existing != null) {
                            // 已有记录：只更新昵称（保留 isSelf 等字段）
                            storagePlugin.getMemberDao().upsert(
                                existing.copy(nickname = memberName, status = MemberStatus.ACTIVE)
                            )
                        } else {
                            // 新成员：插入
                            storagePlugin.getMemberDao().upsert(
                                MemberEntity(
                                    memberUuid = memberId,
                                    groupId = groupId,
                                    nickname = memberName,
                                    isSelf = isMe,
                                    status = MemberStatus.ACTIVE,
                                    lastHeartbeat = System.currentTimeMillis(),
                                    joinedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                    Log.d(TAG, "MEMBER_SYNC: $memberId ($memberName), isMe=$isMe")
                }

                ProtocolMessageType.FILE -> {
                    // 收到文件/图片消息：在 IO 线程解析+保存（base64 解码和文件写入是重操作）
                    val rawMsg = msg
                    viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val payload = JSONObject(rawMsg.c ?: "")
                        val metaJson = payload.optString("meta", "")
                        val base64Data = payload.optString("data", "")

                        if (metaJson.isBlank() || base64Data.isBlank()) {
                            Log.w(TAG, "FILE 消息缺少 meta 或 data")
                        } else {

                        val meta = FileTransferHelper.FileMetaData.fromJson(metaJson)
                        val context = EphemeralChatApplication.get()
                        val fileId = rawMsg.mid ?: UUID.randomUUID().toString().take(8)

                        _uiState.value = _uiState.value.copy(fileTransferProgress = 50, fileTransferStatus = "正在接收${if (meta.isImage) "图片" else "文件"}...")

                        // 保存到本地
                        val localPath = FileTransferHelper.saveBase64ToFile(
                            context, groupId, fileId, base64Data, meta.fileName
                        )

                        val updatedMeta = meta.copy(localPath = localPath)

                        runDb {
                            val msgEntity = MessageEntity(
                                msgId = rawMsg.mid ?: UUID.randomUUID().toString(),
                                groupId = groupId,
                                senderUuid = rawMsg.u,
                                senderName = rawMsg.n ?: "未知",
                                content = updatedMeta.toJson(),
                                type = if (meta.isImage) MessageType.IMAGE else MessageType.FILE,
                                timestamp = rawMsg.ts,
                                isDelivered = true,
                            )
                            storagePlugin.getMessageDao().upsert(msgEntity)
                        }

                        _uiState.value = _uiState.value.copy(fileTransferProgress = -1, fileTransferStatus = "")
                        Log.i(TAG, "收到${if (meta.isImage) "图片" else "文件"}: ${meta.fileName}, ${meta.fileSize} bytes")

                        // 后台通知
                        if (rawMsg.u != myUuidShort) {
                            val title = if (meta.isImage) "${rawMsg.n ?: "好友"} 发送了图片" else "${rawMsg.n ?: "好友"} 发送了文件"
                            maybeNotifyBackgroundNotification(title, meta.fileName)
                        }
                        } // end else
                    } catch (e: Exception) {
                        Log.e(TAG, "接收文件失败", e)
                        _uiState.value = _uiState.value.copy(fileTransferProgress = -1, errorMessage = "接收文件失败")
                    }
                    } // end IO launch
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
     * 等待 JOIN_ACK 的超时保护：25 秒未收到则提示，避免无限卡在"正在连接"。
     */
    private fun startJoinAckTimeout() {
        joinAckTimeoutJob?.cancel()
        joinAckTimeoutJob = viewModelScope.launch {
            delay(25_000)
            if (_uiState.value.screen == Screen.Join) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "加入超时，请确认对方已创建群聊且蓝牙在线，再试一次",
                    connectionStatus = ConnectionStatus.Disconnected,
                )
                Log.w(TAG, "JOIN_ACK 等待超时")
            }
        }
    }

    // ---- 后台运行与消息通知 ----

    /**
     * 启动前台服务：保证切到其他应用后 BLE 继续运行、群聊消息持续接收。
     */
    private fun startForegroundService() {
        try {
            val context = EphemeralChatApplication.get()
            ContextCompat.startForegroundService(
                context,
                Intent(context, EphemeralForegroundService::class.java),
            )
            Log.i(TAG, "前台服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
        }
    }

    /**
     * 停止前台服务（退出/解散群聊时调用）。
     */
    private fun stopForegroundService() {
        try {
            val context = EphemeralChatApplication.get()
            context.stopService(Intent(context, EphemeralForegroundService::class.java))
            Log.i(TAG, "前台服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止前台服务失败", e)
        }
    }

    /**
     * 后台收到新消息时按开关弹窗通知（仅在开关开启、应用在后台、有通知权限时生效）。
     */
    private fun maybeNotifyBackgroundNotification(title: String, content: String) {
        try {
            if (!SharedStateManager.showNotifications.value) return
            if (EphemeralChatApplication.isAppInForeground()) return
            val context = EphemeralChatApplication.get()
            if (NotificationHelper.hasPermission(context)) {
                NotificationHelper.showMessageNotification(context, title, content)
            }
} catch (e: Exception) {
            Log.e(TAG, "后台通知发送失败", e)
        }
    }

    /**
     * 取消创建：停止 BLE 活动并回到干净的首页（用于创建等待页的"取消"）。
     */
    fun cancelGroupCreation() {
        joinAckTimeoutJob?.cancel()
        try {
            registry.getPlugin<LifecyclePlugin>("lifecycle")?.stopHeartbeat()
            registry.getPlugin<BlePlugin>("ble")?.stopAdvertising()
            registry.getPlugin<BlePlugin>("ble")?.stopScanning()
            registry.getPlugin<BlePlugin>("ble")?.stopGattServer()
            registry.getPlugin<BlePlugin>("ble")?.disconnectClient()
        } catch (e: Exception) {
            Log.e(TAG, "取消创建时清理失败", e)
        }
        _uiState.value = ChatUiState(nickname = nickname, myUuidShort = myUuidShort)
        // 修复：取消 Room Flow 订阅
        memberLoadJob?.cancel()
        messageLoadJob?.cancel()
        stopForegroundService()
        FileTransferHelper.clearAllFiles(EphemeralChatApplication.get())
        Log.i(TAG, "已取消创建群聊")
    }
}
