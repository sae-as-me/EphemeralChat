---
AIGC:
  ContentProducer: '001191110102MAD55U9H0F10002'
  ContentPropagator: '001191110102MAD55U9H0F10002'
  Label: '1'
  ProduceID: 'b5fabe81-2015-4412-8f57-b5270eb98fda'
  PropagateID: 'b5fabe81-2015-4412-8f57-b5270eb98fda'
  ReservedCode1: '2dc9f266-8ef7-455d-808f-4cdd0e170bc8'
  ReservedCode2: '2dc9f266-8ef7-455d-808f-4cdd0e170bc8'
---

# EphemeralChat

**临时群聊，在场即在场，离场即消散。**

EphemeralChat 是一款基于 BLE（蓝牙低功耗）P2P 技术的 Android 临时群聊 + 线下聚会小游戏合集应用。不依赖任何服务器和网络，利用手机定位 + 4 位邀请码即可创建/加入群聊，随时随地与附近的朋友聊天、传图片文件、玩聚会游戏。

## 核心特性

### 群聊

- **零服务器零网络**：纯 BLE GATT 星型拓扑 P2P 通信，创建者作为 Hub 中继消息，全程不经过互联网
- **邀请码 + Geohash 定位**：4 位邀请码 + 5 级 Geohash 联合编码，同一区域的人才能互相发现；扫描时同时匹配 9 邻域哈希，边缘位置也能加入
- **关定位即离群**：关闭定位 → BLE 停止 → 心跳停止 → 自动判定离群
- **消息端到端加密**：ECDH(secp256r1) 密钥协商 → AES-256-GCM 消息加密，密钥仅存内存
- **全库加密存储**：Room + SQLCipher，密钥由 Android Keystore 管理
- **自动解散清除**：退出/解散即删除全部本地数据（含聊天记录、图片、文件），不留痕迹
- **图片/文件传输**：相册选图、拍照发送（自动压缩至 200KB 内）、任意文件（≤10MB）；收到的图片可另存相册，文件可另存下载目录
- **表情发送**：常用 Emoji 4 分类（常用/动物/食物/出行）128 个，点击追加到消息输入框；猜拳和骰子带动画效果
- **后台运行 + 弹窗通知**：切到其他应用仍保持 BLE 连接收消息；"我的"页面可开启后台新消息弹窗通知（默认关闭）
- **深色主题默认** + 色盲友好状态标识 + UI 屏幕自适应缩放

### 聚会小游戏（以插件形式接入，互不干扰）

- **数字炸弹**：单人可玩（本地模式）或多人轮流竞猜；支持快捷范围（1-100/1-1000）或自定义范围；猜中炸弹者出局，游戏结束
- **谁是卧底**：4-12 人，内置 102 对相近词库；App 仅负责发牌、收集投票和宣布结果；所有人不知道自己的身份，被淘汰不公布，游戏结束后统一揭晓；支持白板角色（8+ 人可选，胜利需群主手动宣布）
- **游戏邀请机制**：群主发起游戏后，其他成员收到邀请弹窗，显示已同意/总人数，全员同意后自动开始
- **更多游戏开发中**：摇骰子、狼人杀、情书等

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.0 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2024.06.00 |
| 导航 | Compose Navigation | 2.7.7 |
| 异步 | Coroutines + Flow | 1.8.1 |
| DI | Hilt | 2.51.1 |
| 存储 | Room + SQLCipher | Room 2.6.1 / SQLCipher 4.5.4 |
| 偏好 | DataStore Preferences | 1.1.1 |
| 后台 | Foreground Service | Android SDK 34 |
| BLE | Android Bluetooth LE API | 原生 API |
| 加密 | Android Keystore + javax.crypto | 系统 API |
| 构建 | Gradle Kotlin DSL | Gradle 8.5 |
| SDK | compileSdk 34 / minSdk 26 / targetSdk 34 | minSdk 26 保证 BLE 广播可用 |

## 核心设计

### BLE 星型拓扑

```
创建者（Hub）                    加入者（Client）
┌──────────────┐               ┌──────────────┐
│ GATT Server  │◄──连接/写入──►│ GATT Client  │
│ + 广播       │──通知/广播────►│ + 扫描       │
└──────────────┘               └──────────────┘
```

- **Hub** = GATT Server + 广播者，负责消息中继、成员管理、心跳广播、游戏发牌
- **Client** = GATT Client + 扫描者，扫描到匹配广播后连接 Hub
- **GATT 两个特征值**：`command`（Write）+ `notification`（Notify）
- **MTU 动态协商**：连接后协商 MTU 247，分片器动态适配（协商失败自动回退 20 字节/片）
- **定向消息**：`sendToDevice` 支持只向指定设备发通知（游戏秘密发牌），广播与私发分离

### 消息协议

紧凑 JSON 格式，12 种消息类型：

`JOIN` / `JOIN_ACK` / `MSG` / `SYS` / `HB` / `LEAVE` / `NICK` / `FILE` / `MEMBER_SYNC` / `GAME` / `SYNC_REQ` / `SYNC_RSP` / `DISSOLVE`

- **GAME 消息**：`c` 字段携带 `GameMessagePayload`（游戏 ID + 动作 + 数据 + 是否私密），按 `game_{gid}` 频道路由到对应游戏插件
- **分片协议**：超长消息按 3 字节头（msgId + seqFlag）自动分片，接收端按序重组

### 心跳与生命周期

- 心跳间隔 **15s**，超时 **45s** 判定离线，**90s** 移除成员
- 成员列表清空 / 全员定位关闭 → 自动触发解散

## 项目结构

```
EphemeralChat/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml          # 权限清单 + Application + Service 声明
│       ├── java/com/ephemeral/chat/
│       │   ├── EphemeralChatApplication.kt   # 应用入口，插件注册中心
│       │   ├── MainActivity.kt               # 权限网关 + 双击退出 + 主界面宿主
│       │   ├── SharedStateManager.kt          # 全局共享状态（主题/昵称/通知开关）
│       │   ├── AppModule.kt                  # Hilt 依赖注入模块
│       │   ├── core/
│       │   │   ├── registry/PluginRegistry.kt    # 插件注册中心（拓扑排序）
│       │   │   ├── eventbus/EventBus.kt          # SharedFlow 事件总线
│       │   │   ├── plugin/IPlugin.kt             # 插件接口
│       │   │   └── ui/
│       │   │       ├── adaptive/                 # 屏幕自适应缩放系统
│       │   │       └── theme/                    # Material 3 主题（深色默认）
│       │   ├── plugins/
│       │   │   ├── location/                     # 定位插件 + Geohash 工具
│       │   │   ├── ble/                          # BLE 广播/扫描/GATT 客户端服务端
│       │   │   ├── crypto/                       # ECDH + AES-256-GCM + Keystore
│       │   │   ├── storage/                      # Room + SQLCipher 加密存储
│       │   │   ├── lifecycle/                    # 心跳管理 + 状态机 + Hub 重选
│       │   │   ├── chat/                         # 聊天业务（ViewModel + UI + 文件传输）
│       │   │   ├── emoji/                        # 表情插件（Emoji 网格 + 猜拳骰子）
│       │   │   ├── profile/                      # 个人信息（主题/昵称/通知开关）
│       │   │   └── game/
│       │   │       ├── ui/GameListScreen.kt      # 游戏列表入口页
│       │   │       ├── numberbomb/               # 数字炸弹插件
│       │   │       └── whospy/                   # 谁是卧底插件（含词库）
│       │   ├── navigation/                       # 底部导航框架
│       │   ├── protocol/                         # 消息协议 + 分片协议 + 游戏消息封装
│       │   └── service/                          # 前台服务（保活）+ 通知工具
│       └── res/                                  # 资源文件
├── docs/
│   ├── DesignDoc.md          # 完整设计方案（11 章）
│   ├── DevPrompt.md          # 全自动开发提示词
│   └── WhoIsSpy_Design.md    # 谁是卧底设计方案
├── CHANGELOG.md              # 版本变更记录
└── build.gradle.kts / settings.gradle.kts / gradle.properties
```

## 构建

### 环境要求

- JDK 17
- Android SDK（compileSdk 34）
- Gradle 8.5+（项目已包含 wrapper）

### 构建命令

```bash
# 编译 Debug 版
./gradlew assembleDebug

# 编译 Release 版
./gradlew assembleRelease
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

### 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

或直接将 APK 拷贝到手机安装（需允许未知来源安装）。

## 使用说明

### 群聊

1. **首次启动**：授予蓝牙 + 定位权限（必须，BLE 扫描依赖定位）
2. **创建群聊**：点击「创建群聊」→ 获取 4 位邀请码 → 分享给附近的人
3. **加入群聊**：点击「加入群聊」→ 输入 4 位邀请码 → 自动扫描附近匹配设备并连接
4. **聊天**：发文字、发表情（点 😊 图标展开面板）、发图片（点 + → 相册/拍照）、发文件（点 + → 文件）
5. **后台收消息**：切到其他应用仍保持在线；开启「弹窗通知」后有新消息弹窗提醒
6. **退出**：手动退出群聊，或关闭定位（自动离群），或 Hub 解散群聊

### 聚会游戏

1. **进入游戏**：聊天页顶栏 🎮 图标 → 游戏列表 → 选择游戏
2. **数字炸弹**：
   - 单人模式：本地生成炸弹，自己不断猜测直到踩中
   - 多人模式：发起后全员收到邀请，同意后轮流竞猜，猜中者出局
3. **谁是卧底**：
   - 群主发起 → 全员收到邀请 → 同意进入
   - 每人收到一个私密词语（含白板角色看不到词）
   - 依次描述 → 全员投票 → 票高者出局（不公布身份）
   - 卧底全出局平民胜 / 卧底数≥非卧底数卧底胜 / 白板存活可由群主宣布白板胜
   - 游戏结束公布所有人身份和词语

## 插件架构

```
LocationPlugin → CryptoPlugin → StoragePlugin → BlePlugin → LifecyclePlugin
    → ChatPlugin → EmojiPlugin → NumberBombPlugin → SpyPlugin → ProfilePlugin
```

- 每个功能/游戏都是独立插件，实现 `IPlugin` 接口并注册到 `PluginRegistry` 即可挂载
- 插件间通过 `EventBus`（SharedFlow 频道）通信，零耦合
- 游戏插件订阅 `game_{gid}` 频道接收游戏消息，通过注入的接口发送广播/私信/指令

## 下载

最新版本请访问 [Releases](https://github.com/sae-as-me/EphemeralChat/releases) 页面。

## 许可

本项目为个人学习项目，仅供学习交流使用。数据全部存储在本地，不收集任何用户信息，不含任何广告、分析、崩溃上报 SDK。
