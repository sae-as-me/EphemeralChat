---
AIGC:
  ContentProducer: '001191110102MAD55U9H0F10002'
  ContentPropagator: '001191110102MAD55U9H0F10002'
  Label: '1'
  ProduceID: '09fbda69-1d4b-40e4-baa4-f65aed01d848'
  PropagateID: '09fbda69-1d4b-40e4-baa4-f65aed01d848'
  ReservedCode1: '9d870103-991c-43ad-87c6-6d4d4266974b'
  ReservedCode2: '9d870103-991c-43ad-87c6-6d4d4266974b'
---

# Changelog

本项目所有重要变更记录于此文件。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [0.3.2] - 2026-09-26

### 修复（5轮全量代码审计）

**第1轮：BLE 传输层与线程安全**
- GattClient 断连后 busy/writeQueue 不重置 → 重连后无法发送消息
- GattClient 通知描述符为 null 时 notificationReady 永不设置 → 卡死
- GattClient write() 清空正在发送的队列导致分片丢失
- ChatScreen 临时文件在异步读取前被删除 → 文件找不到
- ChatViewModel FILE 分支在 BLE 回调线程做 base64 解码+文件写入 → 阻塞 BLE
- MessageFragmenter nextMsgId 非线程安全 → 并发分片时 msgId 冲突
- handleReceivedMessage groupId 为空时仍存入数据 → 孤儿数据

**第2轮：竞态与生命周期**
- GattClient write() 与 onCharacteristicWrite 竞态 → 旧回调删除新队列分片（改为待发队列模式）
- GattClient sendNextQueued 未检查 writeCharacteristic 返回值 → 队列卡死
- ChatViewModel leaveGroup/dissolveGroup 未取消 Room Flow Job → 资源泄漏
- EphemeralChatApplication foregroundActivities 可能变为负数 → 前台判定错误

**第3轮：资源管理与性能**
- createGroup 中 DB 调用无 try-catch → DB 异常导致协程崩溃
- compressBitmapToBase64 中 scaledBitmap 未 recycle → 内存泄漏
- MessageBubble 图片解码在主线程 → 大图 ANR 风险（改为异步加载）
- MEMBER_SYNC 循环广播无间隔 → BLE 通知栈拥塞丢消息

**第4轮：竞态根治与边界**
- GattClient write() 与 onCharacteristicWrite 竞态根治：改为 pendingMessages 队列模式，busy 时不打断当前发送
- FileTransferHelper saveBase64ToFile 扩展名提取修复（处理无扩展名和空扩展名）

**第5轮：一致性验证**
- 确认前4轮修复无冲突、无引入新问题
- 全量编译通过

## [0.3.1] - 2026-09-26

### 修复（源码审计）

- **GattServer 广播分片丢失**：多设备广播时 `notifChar.value` 在设备间被覆盖，后一台设备只收到最后一个分片。改为每次 `notifyCharacteristicChanged` 前重新设值
- **GattClient 描述符写入失败无回退**：`onDescriptorWrite` 返回非 GATT_SUCCESS 时 `notificationReady` 永不为 true → `onConnected` 不回调 → 卡死。改为失败时也标记就绪（回退为只写模式）
- **MessageFragmenter 分片数溢出**：seq 字段仅 7 bit（最大 127），超过 128 片时回绕导致重组错位。增加上限校验，超出抛出明确异常
- **大文件发送 OOM**：10MB 文件 base64 编码后约 13MB，加上 JSON 包装峰值约 15MB，低端设备堆不足。增加可用内存检查，不足时提前拒绝并提示
- **临时缓存文件泄漏**：图片/文件选择器复制到 cacheDir 的临时文件用完未删除。在回调中增加 `tempFile.delete()`
- **另存为提示被覆盖**：保存成功/失败的 `errorMessage` 在文件传输完成时被 `copy(fileTransferStatus = "")` 覆盖。合并为单次 copy
- **图片预览加载失败无提示**：`ImagePreviewScreen` 中 bitmap 为 null 时空白，增加"图片加载失败"文本提示

## [0.3.0] - 2026-09-26

### 新增

- **图片发送**：支持从相册选择和拍照发送，自动压缩（长边 1280px、质量 85%、≤200KB），聊天中以缩略图气泡展示，点击全屏预览（支持双指缩放）
- **文件发送**：支持选择任意类型文件发送（上限 10MB），聊天中以文件名+大小气泡展示
- **另存为**：收到的图片可保存到系统相册（Pictures/EphemeralChat），文件可保存到下载目录（Download/EphemeralChat）
- **临时存储自动清除**：图片和文件临时存于应用私有目录，退出/解散群聊时自动删除
- **传输性能优化**：BLE 分片器动态适配 MTU，协商成功后用 244 字节/片替代 20 字节/片，传输速度提升约 12 倍

## [0.2.7] - 2026-09-25

### 新增

- **后台运行**：进入群聊（创建/加入成功）后启动前台服务，切到其他应用仍保持 BLE 连接并持续接收群聊消息；退出/解散/取消创建时自动停止
- **后台新消息弹窗通知**：应用在后台且收到新消息时以弹窗（Heads-up）形式提醒，点击可回到应用；"我的"页面新增"弹窗通知"开关（默认关闭，用户自行控制；Android 13+ 开启时会请求通知权限）
- **聊天界面自动滚动**：新消息到达时自动滚动到最新消息底部，无需手动下拉

## [0.2.6] - 2026-09-25

### 修复

- **加入群聊后仍卡在"正在连接"无法自动跳转**：根因是通知订阅只在 MTU 协商回调中执行，部分设备 MTU 协商失败或回调不触发时 Client 永不启用通知订阅 → 收不到 JOIN_ACK → 永远卡住。修复：服务发现后立即启用通知，以描述符写入完成作为就绪信号；JOIN 发送改为轮询等待就绪后再发，加入超时 25 秒自动提示
- **创建群聊前缺少广播能力检查**：不支持 BLE 广播的设备创建后对方搜不到且无提示。现在创建前预检，不支持时直接提示

## [0.2.5] - 2026-09-24

### 修复

- **加入群聊后永远卡在"正在连接"、无法自动进入群聊**：根因是传输层缺陷——消息体超过 BLE 单包上限（20 字节）时被系统自动分片，接收端未重组导致协议消息（加入确认等）丢失。现已将内置分片协议（MessageFragmenter）接入实际传输链路：发送端按 20 字节分片并携带序号头，接收端按设备与序号重组完整消息，任意 MTU 协商结果下均可靠送达
- **成员列表与人数显示不准确**：成员同步消息此前因传输分片丢失而无法送达加入端。传输层修复后，群主广播的全量成员列表能可靠送达，加入端成员列表与人数即时正确
- **创建群聊后对方偶尔搜索不到**：广播启动失败时自动重试 3 次（间隔 2 秒），且失败原因现会显示在聊天页顶部，不再静默丢失
- **加入成功后无提示**：加入确认到达后，聊天界面直接显示"<用户名> 已加入群聊"系统消息

## [0.2.4] - 2026-09-24

### 修复

- **成员列表不完整 / 人数不正确**：Client 端从未收到其他成员信息——Hub 从不向 Client 同步成员。新增 `MEMBER_SYNC` 协议消息，Hub 在收到 JOIN 后查询全量成员并逐条广播同步，Client 收到后更新本地成员表，Room Flow 自动刷新 UI
- **消息气泡左右不分**：`ChatScreen` 的 `isMine` 判断错误地用 `msg.senderUuid == state.nickname`（UUID 与昵称比较），改为 `msg.senderUuid == state.myUuidShort`
- **UUID 不一致**：自己存 `myUuid`（36 字符）、别人存 `msg.u`（4 字符截断），导致 `getById` 永远查不到自己。统一全链路使用 `myUuidShort`（4 位短码）作为 memberUuid / senderUuid

## [0.2.3] - 2026-09-24

### 修复

- **"返回但不退出"后未显示群聊列表、返回按钮无反应**：`CreateGroupScreen` 分支判断从 `inviteCode/groupId` 改为基于 `chatEntered`，修复创建者/加入者两字段均非空导致永远命中邀请码等待页、群聊卡片分支不可达的死循环；等待页"返回"按钮改为「取消创建」，正确停止 BLE 并回到干净首页
- **聊天界面人数始终为 0、消息不显示、发送消息闪退/无反应**：新增 `loadGroupData()` 通过 Room Flow 实时订阅成员与消息到 UI；`JOIN_ACK`、`enterChat`、`reenterGroup` 均触发数据加载；所有数据库与 BLE 操作统一经 `runDb` 与 try-catch 保护，数据库初始化失败等异常不再导致协程崩溃
- **双机互测搜不到附近群聊**：`LocationPlugin.getCurrentLocation` 改为必回调（lastKnown 优先 → 单次更新 10s 超时回退 → 兜底坐标），解决荣耀8X 等无 lastKnown 且室内无 GPS fix 时 `joinGroup` 扫描永不启动、误报"未发现附近群聊"的问题；广播数据升级为 16 字节（位置 hash + 纯邀请码 hash + groupId），扫描端双模式匹配——位置哈希优先、纯邀请码哈希兜底，防止位置缓存陈旧/定位不准导致误匹配

## [0.2.2] - 2026-09-24

### 修复

- **旧版本数据库升级闪退**：v0.2 固定 IV 后密钥变化，新密码打开旧加密库导致 SQLCipher 解密失败；DatabaseManager 打开失败自动删除重建（符合临时数据不持久定位）；KeyManager 增加 Keystore 不可用回退固定 SHA-256 密码（兼容 EMUI/荣耀 ROM）；Application.onCreate 插件注册全 try-catch、SharedStateManager 异步初始化防低端机 ANR；新增全局崩溃处理器写入 `crash_log.txt`

## [0.2.1] - 2026-09-24

### 修复

- **启动白屏（ANR）**：`SharedStateManager.init()` 用 `runBlocking { dataStore.data.collect{} }` 在主线程收集永不结束的 Flow 导致永久阻塞；改为 `first()` 只读一次

## [0.2] - 2026-09-24

### 修复

- **第二次创建/加入群聊闪退**：修复数据库密钥派生不稳定问题——`KeyManager.getDatabaseKey()` 使用 GCM 随机 IV 导致每次启动生成不同的 SQLCipher 密码，二次启动打开加密库失败崩溃。改为固定 IV 派生稳定密码
- **加入群聊搜索不到附近群聊**：BLE 扫描/广播增加蓝牙开关检测、权限检查（BLUETOOTH_SCAN / BLUETOOTH_ADVERTISE）、失败错误回调与 30s 超时提示；扫描改为主线程执行；匹配成功后自动停止扫描
- **昵称被随机覆盖**：ChatViewModel 优先使用 SharedStateManager 已持久化的昵称，不再每次启动覆盖为随机昵称

### 新增

- **应用图标**：AI 生成的专属图标（深色背景 + 聊天气泡 + 定位徽标），配置全部 5 个 mipmap 密度
- **"更多"菜单**：聊天页右上角改为竖排省略号菜单，包含"返回但不退出"与"退出当前群聊"（带二次确认对话框）
- **返回但不退出**：回到首页保留群聊状态，首页显示"我的群聊"列表卡片，点击可重新进入聊天
- **主题持久化**：主题色与昵称通过 DataStore Preferences 持久化，退出重进保持用户设置

## [0.1.1] - 2026-09-24

### 修复

- **创建群聊后无法进入聊天界面**：创建成功后自动跳转聊天界面；"进入群聊"按钮由错误的 `backToHome()` 改为正确的 `enterChat()`，不再回到首页死循环
- **"加入群聊"按钮无反应**：补全空实现的 onClick，新增 `navigateToJoin()` 方法正确跳转到邀请码输入页
- **主题切换无效**：新增 `SharedStateManager` 全局状态单例，贯通 `ProfileViewModel`（写入）与 `MainActivity`（读取），切换开关即时生效
- **"我的"页面缺少用户名管理**：新增当前昵称显示 + 昵称编辑输入框 + 保存按钮，昵称通过 `SharedStateManager` 与 ChatViewModel 跨页面共享

### 新增

- ChatScreen 顶部栏新增"退出群聊"按钮
- JoinGroupScreen 新增"返回首页"按钮
- CreateGroupScreen 新增"返回"按钮（取消创建回到首页）
- 首页显示当前昵称

## [0.1] - 2026-09-22

### 新增

- **首个可运行版本**：基于 BLE P2P 的临时群聊应用完整工程
- **创建群聊**：4 位邀请码 + Geohash 定位联合编码，Hub 启动 BLE 广播 + GATT Server
- **加入群聊**：9 邻域哈希扫描匹配，距离校验（100m）后连接 Hub
- **文本聊天**：Hub 中继广播，消息持久化到本地加密数据库
- **成员管理**：成员列表、在线/离线状态、昵称修改、随机昵称生成（225,000 组合）
- **心跳与生命周期**：15s 心跳 / 45s 离线判定 / 90s 移除，全员离开自动解散
- **定位联动**：关闭定位 → BLE 停止 → 心跳停止 → 自动离群
- **端到端加密**：ECDH(secp256r1) 密钥协商 + AES-256-GCM 消息加密
- **加密存储**：Room + SQLCipher 全库加密，密钥由 Android Keystore 管理
- **Hub 重选**：Hub 离开后 UUID 字典序最小优先 + 随机退避重选
- **消息分片**：超过 MTU 247 的长消息自动分片/重组
- **插件化架构**：7 个插件按拓扑排序注册（Location → Crypto → Storage → BLE → Lifecycle → Chat → Profile）
- **UI 自适应**：屏幕对角线 ≤4.7"×0.85 / 标准×1.0 / ≥6.5"×1.15
- **底部导航**：群聊 + 我的，架构支持动态扩展
- **深色主题默认** + 色盲友好状态标识
- **前台服务**：保持 BLE 活跃，防止后台被杀
- **权限网关**：首次启动引导授权蓝牙 + 定位，拒绝时引导到系统设置