---
AIGC:
  ContentProducer: '001191110102MAD55U9H0F10002'
  ContentPropagator: '001191110102MAD55U9H0F10002'
  Label: '1'
  ProduceID: '67404c95-a4b4-4808-b7d6-c579e52fad64'
  PropagateID: '67404c95-a4b4-4808-b7d6-c579e52fad64'
  ReservedCode1: 'a4511c61-634b-4fa4-9694-8c978fb9da65'
  ReservedCode2: 'a4511c61-634b-4fa4-9694-8c978fb9da65'
---

# Changelog

本项目所有重要变更记录于此文件。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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