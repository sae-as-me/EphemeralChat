---
AIGC:
  ContentProducer: '001191110102MAD55U9H0F10002'
  ContentPropagator: '001191110102MAD55U9H0F10002'
  Label: '1'
  ProduceID: 'd062de4c-2130-4492-a4be-dcd45879aede'
  PropagateID: 'd062de4c-2130-4492-a4be-dcd45879aede'
  ReservedCode1: 'c67385f0-4f46-45b9-8328-73f15cf60316'
  ReservedCode2: 'c67385f0-4f46-45b9-8328-73f15cf60316'
---

# EphemeralChat 临时群聊软件设计方案

## 一、项目概述

### 核心需求

| 需求 | 描述 |
|------|------|
| 创建群聊 | 基于手机定位 + 自动生成 4 位邀请码 |
| 加入群聊 | 输入 4 位码 + 定位匹配，加入后可自定义用户名（默认随机） |
| 零服务器 | 完全 P2P，不依赖任何云服务、信令服务器或第三方 API |
| 自动解散 | 所有人手动退出或关闭定位后，群聊自动消亡，本地数据清除 |

### 设计哲学

这个产品的本质是 **"在场即在场，离场即消散"**——群聊的存在不依赖任何持久化基础设施，只依赖当前物理空间中"愿意共享位置的人"。与 Firewatch 式的 walkie-talkie、任天堂 Switch 的本地面联是同一设计谱系：**物理在场 = 群聊在场**。

---

## 二、技术选型决策

### 2.1 为什么选 BLE（蓝牙低功耗）作为核心通道

| 维度 | BLE GATT | WiFi Direct | 同 LAN TCP | Nearby Connections |
|------|----------|------------|-----------|-------------------|
| 服务器依赖 | 无 | 无 | 无 | 需 Google Play |
| 功耗 | 极低 | 高 | 中 | 中 |
| Android 兼容性 | 全设备 | 部分 OEM 有 bug | 需同一 WiFi | 国内设备缺 GMS |
| 连接数上限 | ~7 | ~10-15 | 无上限 | ~10 |
| 带宽 | ~20KB/s | ~2MB/s | ~10MB/s | ~自适应 |
| 发现阶段 | 自带广播 | 自带发现 | 需 NSD | 自带 |
| 与定位耦合 | Android 6+ 扫描需定位权限 | 无 | 无 | 无 |

**核心决策逻辑**：

1. **"不依赖服务器"排除了所有需要信令交换的方案**（WebRTC、Firebase、云 IM SDK），只剩近场通信
2. **Android 6+ 的 BLE 扫描强制要求定位权限**——这与"利用手机定位"需求完美耦合：用户必须开定位才能扫描 BLE，关闭定位则 BLE 停止，天然实现"关定位 = 离群"
3. **BLE 功耗极低**（广播模式 ~1mA），适合需要持续在线的临时群聊场景
4. **BLE 的 ~7 连接数限制**对临时群聊是合理的——线下聚会、旅行团、会议小组等场景很少超过 7 人同时在线

**结论：v1 采用纯 BLE GATT 星型拓扑，v2 升级 WiFi Direct 支持更大群组。**

### 2.2 为什么不选其他方案

- **WiFi Direct**：功耗高（~300mA），部分国产 ROM 实现有 bug，连接建立慢（3-10s），且 BLE 发现阶段速度更快。留作 v2 大群组升级通道。
- **Nearby Connections API**：封装了 BLE + WiFi Direct，API 简洁，但依赖 Google Play Services，国内设备大量不可用。与"纯本地、最小依赖"原则冲突。
- **同 LAN TCP**：要求所有人连同一 WiFi，户外场景不可用，且需要额外的发现机制（NSD）。
- **BLE Mesh**：理论上去中心化，但 Android 没有官方 BLE Mesh SDK，需引入第三方库（如 Nordic Mesh SDK），复杂度高，延迟大，对临时群聊过度设计。

---

## 三、系统架构

### 3.1 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                    UI 层 (Jetpack Compose)                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │             UI 自适应层 (AdaptiveUI)                    │  │
│  │  字体/图标尺寸自适应 · 屏幕密度感知 · 动态缩放            │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │             底部导航 (BottomNavHost)                    │  │
│  │      群聊页          │        我的页          │  [可扩展]  │  │
│  └──────────────────────────────────────────────────────┘  │
│   创建群聊 │ 加入群聊 │ 聊天界面 │ 成员列表                    │
├──────────────────────────────────────────────────────────┤
│                   业务逻辑层 (ViewModel)                       │
│   群聊管理 │ 消息处理 │ 用户名管理 │ 生命周期                    │
├──────────────────────────────────────────────────────────┤
│                  插件系统层 (PluginRegistry)                    │
│   插件接口定义 │ 插件注册/注销 │ 插件生命周期 │ 插件间通信          │
├──────┬──────┬──────┬──────┬──────────────────────────────┤
│ BLE  │ 定位  │ 存储  │ 加密  │ 生命周期                       │
│ 发现  │ 服务  │ 模块  │ 模块  │ 管理器                         │
│ 通信  │ 模块  │      │      │                               │
├──────┴──────┴──────┴──────┴──────────────────────────────┤
│                    Android 平台层                              │
│  BluetoothManager │ LocationManager │ Room │ ForegroundService│
│  Android Keystore │ WifiManager(不用) │ DisplayMetrics        │
└──────────────────────────────────────────────────────────┘
```

### 3.2 BLE GATT 星型拓扑

```
         ┌─────────┐
         │ Client C │
         └────┬────┘
              │
    ┌─────────┼─────────┐
    │         │         │
┌───┴───┐ ┌──┴──┐ ┌───┴───┐
│Client A│ │ Hub │ │Client B│
└────────┘ │(GO) │ └───────┘
           └──┬──┘
              │
         ┌────┴────┐
         │ Client D │
         └─────────┘
```

- **Hub（协调者）**：群聊创建者，运行 GATT Server + BLE 广播。负责消息中继、成员管理、心跳监测。
- **Client（成员）**：通过 GATT Client 连接到 Hub。发送消息到 Hub，Hub 转发给所有 Client。
- **不是传统服务器**：Hub 只是临时消息中继，不持久化任何数据，断开即失联。所有 Client 本地保存完整消息历史。

### 3.3 角色定义

| 角色 | 职责 | 谁来担任 |
|------|------|---------|
| Hub | GATT Server + BLE 广播 + 消息中继 + 心跳监测 | 创建者（首任），可重选 |
| Client | GATT Client + 消息收发 + 本地存储 | 所有加入者 |
| 任何人 | 本地存储完整消息、监测心跳、检测解散条件 | 全员 |

---

## 四、核心模块设计

### 4.1 BLE 发现模块

#### 4.1.1 邀请码 + Geohash 联合编码

4 位邀请码只有 10,000 种组合，在同一物理区域可能碰撞。解决方案是**将邀请码与位置区域绑定**：

```
广播内容 = SHA-256(邀请码 + Geohash(精度5)) 截取前 4 字节
```

- **Geohash 精度 5** = 约 5km × 5km 网格
- 加入者输入 4 位码后，用自身定位计算 Geohash，生成同样的 hash 去扫描 BLE
- 物理位置不同的同码群聊不会互相干扰
- BLE 广播中不含明文邀请码，防止被动嗅探

**边界情况处理**：如果创建者和加入者恰好分处 Geohash 网格边界两侧，加入者计算自身 + 8 邻域共 9 个 hash 一起扫描，确保不漏。

#### 4.1.2 广播数据包设计

```
BLE Advertising Payload (≤ 31 bytes):
├── Service UUID (2 bytes): 自定义固定 UUID
├── Service Data (8 bytes):
│   ├── code_hash (4 bytes): SHA-256(code+geohash) 截取
│   └── group_id_short (4 bytes): 群组 UUID 前 4 字节
└── Flags (3 bytes): 标准 BLE 广播标志
Total: 13 bytes（远低于 31 字节上限）
```

广播间隔：**250ms**（`ADVERTISE_MODE_LOW_LATENCY`），平衡发现速度与功耗。群聊建立后可切换为低功耗模式（1s 间隔）。

#### 4.1.3 扫描匹配流程

```
用户输入 4 位码
    ↓
获取当前 GPS → 计算 Geohash(精度5) → 计算 9 邻域 hash
    ↓
启动 BLE 扫描，过滤 Service UUID
    ↓
比对扫描到的 code_hash 与 9 个候选 hash
    ↓
匹配成功 → 读取 group_id_short → 校验 GPS 距离
    ↓
距离 < 阈值(默认 100m) → 发起 GATT 连接
距离 ≥ 阈值 → 提示"距离群聊创建者太远"
```

### 4.2 BLE 通信模块

#### 4.2.1 GATT 特征值设计

Hub 运行 GATT Server，提供以下特征值：

| 特征值 | UUID | 属性 | 用途 |
|--------|------|------|------|
| `command` | 自定义 | Write | Client → Hub：发送指令（加入/消息/心跳/离开/改名/同步请求） |
| `notification` | 自定义 | Notify | Hub → 所有 Client：广播事件（新消息/系统通知/成员变更/解散） |

简化为两个特征值：一个写、一个通知。所有业务逻辑通过消息类型字段区分，避免特征值过多导致 BLE 连接不稳定。

#### 4.2.2 MTU 协商

连接建立后立即请求 `MTU = 247`（Android 支持的最大实用值），使大部分文本消息单次传输完成，减少分片开销。

#### 4.2.3 消息分片协议

超过 MTU 的消息（如长文本）需要手动分片：

```
分片头 (3 bytes):
├── msg_id (2 bytes): 消息分片组 ID
└── seq_flag (1 byte): [1 bit: 是否最后一片] [7 bits: 序号 0-127]
```

接收方按 `msg_id` 缓存分片，收到 `is_last=true` 后重组。超时 10s 未完成则丢弃。

### 4.3 定位服务模块

#### 4.3.1 定位策略

| 阶段 | 定位用途 | 精度要求 | 频率 |
|------|---------|---------|------|
| 创建群聊 | 生成 Geohash + 计算邀请码 hash | 精度 5 Geohash（~5km） | 单次 |
| 加入群聊 | 计算 Geohash + 距离校验 | GPS 精确（~10m） | 单次 |
| 群聊存续 | 心跳存活条件（定位是否开启） | 不需要坐标 | 每 15s 检测状态 |

**关键设计**：群聊存续期间不需要持续上报 GPS 坐标，只需要检测**定位开关是否处于开启状态**。这大幅降低功耗。

#### 4.3.2 定位状态监控

```kotlin
// 通过 LocationManager 检测定位开关状态
val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

// 定位关闭时 → 停止 BLE 广播/扫描 → 停止心跳 → 触发离群
```

定位关闭 = BLE 停止 = 心跳停止 = 被判定为"离群"。**定位是群聊存续的"心跳电源"**。

### 4.4 生命周期管理模块

#### 4.4.1 心跳机制

```
每 15 秒：
  ├── Hub 向所有 Client 发送 heartbeat 通知
  └── 每个 Client 向 Hub 发送 heartbeat 指令

超时规则：
  ├── 45 秒未收到某成员心跳 → 标记为"离线"
  ├── 90 秒未收到 → 标记为"已离开"，从成员列表移除
  └── 全员已离开（含 Hub）→ 触发解散
```

**心跳不经过 GPS 坐标传输**，只携带 `{"t":"hb","u":"uuid","ts":1695000000}`，极小数据包。

#### 4.4.2 成员状态机

```
                 ┌──────────┐
                 │ Disconnected │
                 └──────┬───┘
              GATT 连接成功
                 ↓
            ┌──────────┐
            │  Joining  │──发送 join 指令──→ Hub 验证
            └──────┬───┘
              Hub 确认
                 ↓
            ┌──────────┐
            │  Active   │←─→ 收发消息、心跳正常
            └──────┬───┘
            心跳超时 45s
                 ↓
            ┌──────────┐
            │  Offline  │──90s 内恢复 → 回到 Active
            └──────┬───┘
            超时 90s / 手动退出 / 定位关闭
                 ↓
            ┌──────────┐
            │  Left     │──全员 Left → 触发解散
            └──────────┘
```

#### 4.4.3 Hub 重选协议

当 Hub 离开（心跳超时），剩余成员需要选举新 Hub：

```
1. 所有 Client 检测到 Hub 心跳超时（45s）
2. 断开旧连接，进入"选举态"
3. 每个 Client 计算自身退避时间：backoff = hash(UUID) % 3000ms
4. 退避期间扫描 BLE 广播：
   ├── 收到同群组的新 Hub 广播 → 连接该 Hub，结束选举
   └── 退避结束未发现广播 → 自己成为新 Hub
5. 新 Hub 启动 GATT Server + BLE 广播（携带相同 group_id）
6. 其他 Client 发现并连接
7. 新 Hub 广播 sync（消息历史），全员同步
8. 聊天恢复
```

**选举优先级**：UUID 字典序最小的设备优先（确定性，无冲突）。通过随机退避 + 扫描确认实现。

**消息缓冲**：选举期间各 Client 缓冲发出的消息，重连后批量发送给新 Hub，避免消息丢失。

**用户体验**：选举期间界面显示"正在重新连接..."，典型耗时 ~50 秒（含心跳检测 45s + 退避 3s + 重连 2s）。可通过缩短心跳间隔到 10s 来优化为 ~25s，代价是略增功耗。

#### 4.4.4 自动解散逻辑

```
触发条件（满足任一即解散）：
  ├── 成员列表清空（最后一人手动退出）
  ├── 全员定位关闭（全员心跳停止）
  └── 全员离线超 90s 且无恢复

解散流程：
  1. 最后离开者 / Hub 发送 dissolve 通知（尽力而为）
  2. 各设备本地执行：
     ├── 删除 Room 数据库中该群组的所有消息和成员记录
     ├── 清除 BLE 广播和扫描
     ├── 清除 GATT 连接
     └── 返回主页，显示"群聊已解散"
  3. 不保留任何痕迹（无持久化、无云端备份）
```

**重要细节**：如果最后一个用户是直接杀进程或手机断电（来不及发 dissolve），其他设备通过心跳超时判定其离线。但此时没有"其他设备"了——群聊事实上已消亡。各设备各自在自己的退出/解散流程中清理本地数据。

### 4.5 本地存储模块

#### 4.5.1 数据库设计（Room + SQLCipher）

```
GroupEntity:
  group_id (UUID, PK)
  code (String, 4位邀请码)
  hub_uuid (String, 当前 Hub UUID)
  is_hub (Boolean, 本机是否为 Hub)
  created_at (Long)
  member_count (Int)
  status (Enum: ACTIVE | DISSOLVING | DISSOLVED)

MemberEntity:
  member_uuid (String, PK)
  group_id (String, FK)
  nickname (String)
  is_self (Boolean)
  status (Enum: ACTIVE | OFFLINE | LEFT)
  last_heartbeat (Long)
  joined_at (Long)

MessageEntity:
  msg_id (String, PK)
  group_id (String, FK)
  sender_uuid (String)
  sender_name (String, 冗余存储，解散后也能看)
  content (String, 加密)
  type (Enum: TEXT | SYSTEM | IMAGE)
  timestamp (Long)
  is_delivered (Boolean)
```

#### 4.5.2 加密策略

- **数据库**：SQLCipher 全库 AES-256 加密，密钥由 Android Keystore 生成
- **消息传输**：群组内 ECDH 协商共享密钥 → AES-256-GCM 加密消息体
- **密钥生命周期**：群组创建时生成，仅存内存，解散时销毁。不写入磁盘。

#### 4.5.3 解散时数据清除

```kotlin
fun dissolveGroup(groupId: String) {
    // 1. 删除数据库记录
    messageDao.deleteByGroup(groupId)
    memberDao.deleteByGroup(groupId)
    groupDao.delete(groupId)

    // 2. 清除内存中的密钥和群组信息
    groupKeyManager.removeKey(groupId)

    // 3. 停止所有 BLE 服务
    bleAdvertiser.stop()
    bleScanner.stop()
    gattServer.close()

    // 4. 清除通知栏
    notificationManager.cancelAll()
}
```

### 4.6 用户名系统

#### 4.6.1 随机用户名生成

```kotlin
// 格式：形容词 + 动物 + 编号
val adjectives = listOf("飞行的", "沉默的", "迷路的", "好奇的", "慵懒的", ...)
val animals = listOf("企鹅", "狐狸", "橡树", "海鸥", "考拉", "萤火虫", ...)
val number = Random.nextInt(10, 100)

val randomName = "${adjectives.random()}${animals.random()}$number"
// 示例："飞行的企鹅42"、"沉默的橡树88"
```

词库内置 50 形容词 x 50 动物 x 90 编号 = **225,000 种组合**，小规模群聊内重名概率极低。重名时不强制去重（用 UUID 区分），只在界面加 "(2)" 后缀。

#### 4.6.2 用户名变更

用户加入后可随时修改用户名，发送 `nick` 类型消息广播给全群：

```json
{"t":"nick","u":"uuid","n":"新名字","ts":1695000000}
```

其他成员收到后更新本地 `MemberEntity.nickname`。

### 4.7 UI 自适应设计

#### 4.7.1 设计目标

App 需在不同屏幕分辨率和尺寸的手机上呈现协调的字体与图标比例。核心思路是在 Compose 原生 `dp/sp` 之上叠加一层自适应缩放因子，根据屏幕物理尺寸和密度动态调整。

#### 4.7.2 屏幕分级与缩放因子

| 屏幕类型 | 对角线尺寸 | 缩放因子 | 典型设备 |
|---------|-----------|---------|----------|
| 小屏 | ≤ 4.7" | 0.85 | 紧凑型手机 |
| 标准 | 4.7" - 6.5" | 1.0 | 主流手机 |
| 大屏 | ≥ 6.5" | 1.15 | 平板/折叠屏 |

```kotlin
// 通过 LocalConfiguration 获取屏幕物理尺寸
val configuration = LocalConfiguration.current
val screenWidth = configuration.screenWidthDp.dp
val screenHeight = configuration.screenHeightDp.dp
val density = LocalDensity.current

// 计算对角线英寸
val diagonalInches = sqrt(
    (screenWidth.value / density.density).pow(2) +
    (screenHeight.value / density.density).pow(2)
) / 25.4f

// 推导缩放因子
val scale = when {
    diagonalInches <= 4.7f -> 0.85f
    diagonalInches >= 6.5f -> 1.15f
    else -> 1.0f
}
```

#### 4.7.3 自适应排版系统

```kotlin
// CompositionLocal 全局提供缩放因子
val LocalAdaptiveScale = compositionLocalOf { 1.0f }

// 自适应字号扩展
@Composable
fun adaptiveSp(base: Float): TextUnit {
    val scale = LocalAdaptiveScale.current
    return (base * scale).sp
}

// 自适应图标尺寸扩展
@Composable
fun adaptiveDp(base: Float): Dp {
    val scale = LocalAdaptiveScale.current
    return (base * scale).dp
}
```

使用方式：所有 UI 组件统一通过 `adaptiveSp` / `adaptiveDp` 声明尺寸，不直接硬编码 `sp`/`dp`：

```kotlin
Text(
    text = "群聊",
    fontSize = adaptiveSp(16f),    // 标准屏 16sp，小屏 13.6sp，大屏 18.4sp
)
Icon(
    painter = Icons.Default.Group,
    modifier = Modifier.size(adaptiveDp(24f)),  // 标准屏 24dp，小屏 20.4dp
)
```

#### 4.7.4 Material 3 Typography 覆盖

在 `MaterialTheme` 层面集成自适应缩放，全局 `Typography` 自动继承缩放因子，无需逐个组件手动调用：

```kotlin
@Composable
fun EphemeralChatTheme(content: @Composable () -> Unit) {
    val scale = LocalAdaptiveScale.current
    val baseTypography = MaterialTheme.typography

    val adaptiveTypography = Typography(
        headlineLarge = baseTypography.headlineLarge.copy(
            fontSize = baseTypography.headlineLarge.fontSize * scale
        ),
        bodyLarge = baseTypography.bodyLarge.copy(
            fontSize = baseTypography.bodyLarge.fontSize * scale
        ),
        // ... 其余字号同理覆盖
    )

    MaterialTheme(typography = adaptiveTypography, content = content)
}
```

### 4.8 底部导航

#### 4.8.1 导航架构

采用 Jetpack Compose Navigation + Material 3 `NavigationBar`，以 `NavHost` 管理顶层页面路由。

```
┌─────────────────────────────────────────────┐
│               主内容区 (NavHost)               │
│                                             │
│  当前选中的 Tab 对应的 Composable 内容           │
│  (群聊页 / 我的页 / [未来扩展页])              │
│                                             │
├─────────────────────────────────────────────┤
│  ┌────────────┬────────────┬──────────────┐ │
│  │   群聊      │   我的      │  [可扩展]    │ │
│  │  (Chat)    │ (Profile)  │              │ │
│  └────────────┴────────────┴──────────────┘ │
└─────────────────────────────────────────────┘
```

#### 4.8.2 导航项定义

```kotlin
sealed class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Chat : NavDestination("chat", "群聊", Icons.Default.Chat)
    data object Profile : NavDestination("profile", "我的", Icons.Default.Person)
    // 未来扩展只需新增 data object 即可
}

// 导航项列表——新增 Tab 只需在此追加
val bottomNavItems = listOf(
    NavDestination.Chat,
    NavDestination.Profile,
)
```

#### 4.8.3 与插件系统的集成

导航项列表由 `PluginRegistry` 动态生成。每个 UI 插件通过 `registerNavDestination()` 注册自己的导航项，`BottomNavHost` 从注册中心拉取列表渲染：

```kotlin
@Composable
fun BottomNavHost() {
    val navItems = PluginRegistry.getNavDestinations()  // 动态获取
    var selectedItem by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, dest ->
                    NavigationBarItem(
                        selected = selectedItem == index,
                        onClick = { selectedItem = index },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label, fontSize = adaptiveSp(12f)) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = rememberNavController(),
            startDestination = navItems.first().route,
            modifier = Modifier.padding(padding),
        ) {
            navItems.forEach { dest ->
                composable(dest.route) { dest.content() }
            }
        }
    }
}
```

**可扩展性**：新增一个底部 Tab（如"设置"）只需编写一个新插件并调用 `registerNavDestination()`，无需修改导航框架代码。

### 4.9 插件化架构

#### 4.9.1 设计目标

核心 BLE、定位、存储等功能与业务逻辑（群聊、个人信息）之间尽可能解耦。后续新功能以插件形式接入，不改动核心框架。

#### 4.9.2 架构分层

```
┌─────────────────────────────────────────────────┐
│                  PluginRegistry                    │
│  插件注册中心：管理所有插件的生命周期与依赖关系      │
├─────────────────────────────────────────────────┤
│                     EventBus                      │
│  事件总线：插件间通信，发布/订阅模式，零耦合          │
├─────────────────────────────────────────────────┤
│  核心插件          │  业务插件          │  UI 插件  │
│  ─────────        │  ─────────        │  ──────  │
│  BlePlugin        │  ChatPlugin       │  ChatTab  │
│  LocationPlugin   │  ProfilePlugin    │  ProfileTab│
│  StoragePlugin    │  [未来业务插件]    │  [未来UI] │
│  CryptoPlugin     │                   │           │
│  LifecyclePlugin  │                   │           │
└─────────────────────────────────────────────────┘
```

#### 4.9.3 插件接口定义

```kotlin
interface IPlugin {
    /** 插件唯一标识 */
    val pluginId: String

    /** 依赖的其他插件 ID 列表（可为空） */
    val dependencies: List<String>
        get() = emptyList()

    /** 初始化：注册服务、订阅事件 */
    fun onInit(registry: PluginRegistry, eventBus: EventBus)

    /** 启动：开始运行 */
    fun onStart()

    /** 停止：释放资源 */
    fun onStop()

    /** 销毁：彻底清理 */
    fun onDestroy()
}
```

#### 4.9.4 插件注册中心

```kotlin
class PluginRegistry {
    private val plugins = mutableMapOf<String, IPlugin>()
    private val navDestinations = mutableListOf<NavDestination>()

    /** 注册插件 */
    fun register(plugin: IPlugin) {
        // 1. 检查依赖是否已注册
        plugin.dependencies.forEach { dep ->
            require(plugins.containsKey(dep)) {
                "Missing dependency: $dep for plugin ${plugin.pluginId}"
            }
        }
        plugins[plugin.pluginId] = plugin
        plugin.onInit(this, eventBus)
    }

    /** 按依赖顺序启动所有插件 */
    fun startAll() {
        val sorted = topologicalSort(plugins.values)  // 拓扑排序
        sorted.forEach { it.onStart() }
    }

    /** 注册导航项（UI 插件调用） */
    fun registerNavDestination(dest: NavDestination) {
        navDestinations.add(dest)
    }

    /** 获取所有已注册导航项 */
    fun getNavDestinations(): List<NavDestination> = navDestinations.toList()

    /** 获取其他插件实例（跨插件调用） */
    @Suppress("UNCHECKED_CAST")
    fun <T : IPlugin> getPlugin(id: String): T? = plugins[id] as? T
}
```

#### 4.9.5 EventBus 事件总线

```kotlin
class EventBus {
    private val channels = mutableMapOf<String, MutableSharedFlow<AppEvent>>()

    /** 发布事件 */
    suspend fun publish(channel: String, event: AppEvent) {
        channels.getOrPut(channel) { MutableSharedFlow() }.emit(event)
    }

    /** 订阅事件 */
    fun subscribe(channel: String): Flow<AppEvent> =
        channels.getOrPut(channel) { MutableSharedFlow() }
}

// 事件定义（密封类）
sealed class AppEvent {
    data class MessageReceived(val content: String, val sender: String) : AppEvent()
    data class MemberJoined(val uuid: String, val nickname: String) : AppEvent()
    data class MemberLeft(val uuid: String) : AppEvent()
    data class LocationStateChanged(val enabled: Boolean) : AppEvent()
    data class GroupDissolved(val groupId: String) : AppEvent()
    // ... 后续插件可扩展新事件类型
}
```

#### 4.9.6 插件示例：BLE 插件

```kotlin
class BlePlugin : IPlugin {
    override val pluginId = "ble"
    override val dependencies = listOf("location")  // 依赖定位插件

    private lateinit var advertiser: BluetoothLeAdvertiser
    private lateinit var scanner: BluetoothLeScanner

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        // 订阅定位状态变化事件
        eventBus.subscribe("location_state").onEach { event ->
            if (event is AppEvent.LocationStateChanged && !event.enabled) {
                stopAdvertisingAndScanning()
            }
        }.launchIn(CoroutineScope(Dispatchers.IO))
    }

    override fun onStart() { /* 启动 BLE 广播/扫描 */ }
    override fun onStop() { /* 停止 BLE */ }
    override fun onDestroy() { /* 清理资源 */ }
}
```

#### 4.9.7 与现有架构的一致性

本插件化架构与用户一贯偏好的 `SystemRegistry + EventBus` 低耦合插件化设计一致：
- **SystemRegistry** → `PluginRegistry`（插件注册中心 + 生命周期管理）
- **EventBus** → 插件间通信（发布/订阅，不直接持有引用）
- **系统按生命周期注册** → `IPlugin` 的 `onInit / onStart / onStop / onDestroy`
- **新功能插件化接入** → 新增 `IPlugin` 实现 + 注册到 `PluginRegistry`，不改核心

---

## 五、通信协议设计

### 5.1 消息类型

| 类型代码 | 含义 | 方向 | 说明 |
|---------|------|------|------|
| `join` | 加入请求 | Client -> Hub | 携带用户名 |
| `join_ack` | 加入确认 | Hub -> Client | 携带成员列表 + 群组密钥 |
| `msg` | 文本消息 | Client -> Hub -> All | 加密文本 |
| `sys` | 系统消息 | Hub -> All | "XX 加入了群聊" |
| `hb` | 心跳 | 双向 | 存活检测 |
| `leave` | 主动退出 | Client -> Hub | 通知 Hub 移除自己 |
| `nick` | 改名 | Client -> Hub -> All | 用户名变更 |
| `sync_req` | 历史同步请求 | Client -> Hub | 新 Hub 上线后请求历史 |
| `sync_rsp` | 历史同步响应 | Hub -> Client | 返回最近 N 条消息 |
| `dissolve` | 解散通知 | Hub -> All | 最后一人退出时广播 |

### 5.2 消息格式（紧凑 JSON）

```json
{
  "t": "msg",
  "u": "a1b2",
  "n": "飞行的企鹅42",
  "c": "大家好！",
  "g": "f8e3",
  "ts": 1695000000,
  "mid": "c4d5"
}
```

字段缩写以节省 BLE 带宽：`t`=type, `u`=uuid(截短4位), `n`=name, `c`=content, `g`=group(截短4位), `ts`=timestamp, `mid`=message id。

典型文本消息约 **120-180 字节**，MTU 247 字节下单次传输完成。

### 5.3 消息流转

```
Client A 发送 "大家好"
    ↓
写入 Hub 的 command 特征值（加密）
    ↓
Hub 解密 → 存储 → 分配 msg_id → 加密
    ↓
Hub 通过 notification 特征值通知所有 Client
    ↓
Client B, C, D 收到通知 → 解密 → 存入 Room → 更新 UI
```

**无 ACK 机制**：BLE GATT Notify 本身有链路层重传，应用层不额外确认。丢消息概率极低（BLE 链路层有 CRC + 重传），对临时群聊可接受。

---

## 六、群聊完整生命周期

```
┌──────────────────────────────────────────────────────┐
│                    创建阶段                           │
│                                                      │
│  开定位 → 点"创建群聊" → 生成 4 位码 + 群组 UUID       │
│  → 启动 BLE 广播 + GATT Server → 显示邀请码           │
│  → 等待加入                                          │
└──────────────────────┬───────────────────────────────┘
                       ↓
┌──────────────────────────────────────────────────────┐
│                    加入阶段                           │
│                                                      │
│  开定位 → 输入 4 位码 → BLE 扫描匹配                  │
│  → 距离校验 → GATT 连接 → 发送 join → 收到 join_ack   │
│  → 生成随机用户名（可修改）→ 进入聊天                  │
└──────────────────────┬───────────────────────────────┘
                       ↓
┌──────────────────────────────────────────────────────┐
│                    聊天阶段                           │
│                                                      │
│  收发文本消息 ←→ Hub 中继                             │
│  每 15s 心跳 ←→ 互相检测存活                          │
│  每 15s 检测定位开关状态                               │
│  成员可改名、可手动退出                                │
│  Hub 离开 → 自动重选新 Hub                            │
└──────────────────────┬───────────────────────────────┘
                       ↓
┌──────────────────────────────────────────────────────┐
│                    解散阶段                           │
│                                                      │
│  全员退出/关定位 → 心跳全停 → 成员列表清空             │
│  → 本地删除所有消息和群组数据                         │
│  → 停止 BLE 服务 → 返回主页                          │
│  → 无任何残留痕迹                                     │
└──────────────────────────────────────────────────────┘
```

---

## 七、安全设计

### 7.1 邀请码安全

| 威胁 | 防护措施 |
|------|---------|
| 远程嗅探邀请码 | BLE 广播不含明文码，只含 hash(code+geohash)，远程蓝牙不可达 |
| 同区域暴力匹配 | 4 位码 + 5km Geohash = 有效空间 10,000 x ~80,000 格 = 8 亿组合 |
| 物理邻近冒充加入 | 加入后可由 Hub 验证 GPS 距离 < 阈值（默认 100m） |

### 7.2 消息加密

```
群组密钥协商：
  1. 创建群聊时，Hub 生成 ECDH (secp256r1) 密钥对
  2. 新成员 join 时，Client 生成自己的 ECDH 密钥对
  3. 双方通过 BLE GATT 交换公钥
  4. 各自计算 ECDH 共享密钥 → 派生 AES-256 对称密钥
  5. 后续所有 msg 类型消息用 AES-256-GCM 加密

密钥不持久化：
  - 仅存内存
  - 群组解散时随进程数据清除
```

### 7.3 隐私保护

- **无注册**：不收集手机号、邮箱、任何个人信息
- **无持久标识**：每次创建/加入群聊生成新 UUID，跨群聊不可关联
- **本地存储加密**：SQLCipher + Android Keystore
- **解散即清除**：解散时删除所有本地数据，物理上不可恢复（数据库文件覆写）
- **定位数据不上传**：GPS 坐标仅用于本地距离计算，从不通过网络传输

---

## 八、权限清单

```xml
<!-- BLE -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- 定位（BLE 扫描必需） -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- 前台服务（后台保持 BLE 活跃） -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- 蓝牙硬件声明 -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

**Android 12+ 运行时权限**：需要同时请求 `BLUETOOTH_SCAN`、`BLUETOOTH_ADVERTISE`、`BLUETOOTH_CONNECT`、`ACCESS_FINE_LOCATION` 四项权限。

---

## 九、技术栈

| 层次 | 技术 | 理由 |
|------|------|------|
| UI | Jetpack Compose + Material 3 | 与现有技术栈一致 |
| UI 自适应 | LocalConfiguration + CompositionLocal | 屏幕密度感知缩放 |
| 导航 | Compose Navigation + NavigationBar | Material 3 底部导航 |
| 异步 | Coroutines + Flow | BLE 回调天然适配 Flow |
| DI | Hilt | 标准选择 |
| 插件框架 | PluginRegistry + EventBus | 低耦合插件化 |
| 存储 | Room + SQLCipher | 加密本地存储 |
| 偏好 | DataStore | 替代 SharedPreferences |
| 后台 | Foreground Service + WorkManager | 保持 BLE 活跃 + 定时心跳 |
| BLE | Android Bluetooth LE API | 原生，无第三方依赖 |
| 加密 | Android Keystore + javax.crypto | 系统级密钥管理 |

### 项目结构（英文 PascalCase）

```
EphemeralChat/
├── app/
│   ├── src/main/java/com/xxx/ephemeralchat/
│   │   ├── core/               # 核心框架（不依赖任何插件）
│   │   │   ├── registry/       # PluginRegistry 插件注册中心
│   │   │   ├── eventbus/       # EventBus 事件总线
│   │   │   ├── plugin/         # IPlugin 接口 + 生命周期管理
│   │   │   └── ui/             # AdaptiveUI 自适应系统 + 主题
│   │   ├── plugins/            # 功能插件（每个插件独立解耦）
│   │   │   ├── ble/            # BLE 发现 + GATT 通信插件
│   │   │   ├── location/       # 定位服务插件
│   │   │   ├── storage/       # Room + SQLCipher 存储插件
│   │   │   ├── crypto/        # 加密模块插件
│   │   │   ├── lifecycle/     # 群聊生命周期管理插件
│   │   │   ├── chat/          # 聊天业务插件（群聊页 Tab）
│   │   │   └── profile/       # 个人信息插件（我的页 Tab）
│   │   ├── navigation/        # 底部导航 + NavHost
│   │   ├── protocol/          # 消息协议定义（共享数据类）
│   │   ├── service/           # Foreground Service
│   │   └── MainActivity.kt
│   └── src/main/res/
└── build.gradle.kts
```

---

## 十、局限性与权衡

| 局限 | 影响 | 缓解/后续 |
|------|------|----------|
| 最多 ~7 人同时在线 | BLE 连接数限制 | v2 引入 WiFi Direct 扩展到 15-20 人 |
| 仅文本消息 | BLE 带宽 ~20KB/s | v2 WiFi Direct 通道支持图片/语音 |
| 物理距离限制 ~100m | BLE 通信范围 | 场景设计即如此，不是 bug 是 feature |
| Hub 离开有 ~50s 中断 | 消息延迟 | 缩短心跳间隔可优化至 ~25s，但增加功耗 |
| 无消息持久化 | 解散后消息消失 | 设计如此，临时性是核心理念 |
| 不支持后台长时间运行 | Android 后台限制 | Foreground Service 尽量保活，但不保证 |
| 4 位码同区域碰撞 | 可能加入错误群聊 | Geohash 邻域 + 距离校验已大幅降低概率 |

---

## 十一、分阶段开发路线

### Phase 1：最小可用版（2-3 周）

**架构基建**：
- 插件化框架搭建：PluginRegistry + EventBus + IPlugin 接口定义
- 核心插件注册（BlePlugin、LocationPlugin、StoragePlugin、CryptoPlugin）
- UI 自适应系统搭建：屏幕密度检测 + 缩放因子 + adaptiveSp/adaptiveDp
- 底部导航框架搭建：NavigationBar + NavHost（群聊页 + 我的页）

**功能实现**：
- BLE 广播 + 扫描 + 邀请码匹配
- GATT 连接建立 + 基本文本消息收发
- 随机用户名 + 自定义修改
- 手动退出 + 本地数据清除
- 基础 UI（创建/加入/聊天/成员列表）

**验收标准**：两台手机创建 + 加入 + 文本聊天 + 退出后数据清除，全流程零报错；不同屏幕尺寸下字体/图标比例协调，底部导航可正常切换。

### Phase 2：健壮性增强（2-3 周）

- 心跳机制 + 成员状态机
- Hub 重选协议
- 定位关闭检测 + 自动离群
- 自动解散逻辑
- 消息加密（ECDH + AES-256-GCM）
- Room + SQLCipher 加密存储
- Foreground Service 后台保活
- 消息分片协议

**验收标准**：Hub 退出后自动重选、定位关闭后自动离群、全员离开后自动解散，全流程零报错。

### Phase 3：体验打磨（2 周）

- 消息时间戳显示 + 消息排序
- 成员在线状态可视化
- 系统消息（"XX 加入了群聊"）
- 连接状态提示（"正在重新连接..."）
- 动画与交互细节
- 深色主题（默认）
- 色盲友好设计

### Phase 4（可选）：扩展能力

- WiFi Direct 通道：支持 15-20 人群聊 + 图片/语音消息
- 振动/铃声消息提醒
- 群组名称自定义
- 消息撤回（限时）