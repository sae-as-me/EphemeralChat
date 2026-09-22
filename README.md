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

EphemeralChat 是一款基于 BLE（蓝牙低功耗）P2P 技术的 Android 临时群聊应用。不依赖任何服务器，利用手机定位 + 4 位邀请码即可创建/加入群聊。所有人手动退出或关闭定位后，群聊自动解散且不留任何痕迹。

## 核心特性

- **零服务器架构**：纯 BLE GATT 星型拓扑 P2P 通信，创建者作为 Hub 中继消息，支持 Hub 自动重选
- **邀请码 + Geohash 定位**：4 位邀请码 + 5 级 Geohash 联合编码，同一区域的人才能互相发现，碰撞概率极低
- **9 邻域扫描**：加入者计算自身 + 8 邻域共 9 个哈希同时匹配，边缘位置也能加入
- **关定位即离群**：利用"Android BLE 扫描必须开启定位"的系统约束，关闭定位 → BLE 停止 → 心跳停止 → 自动判定离群
- **端到端加密**：ECDH(secp256r1) 密钥协商 → AES-256-GCM 消息加密，密钥仅存内存
- **全库加密存储**：Room + SQLCipher 加密数据库，密钥由 Android Keystore 管理
- **自动解散清除**：退出/解散即删除全部本地数据，物理擦除不留痕迹
- **随机用户名**：50 形容词 × 50 动物 × 90 编号 = 225,000 种组合，重名自动加后缀
- **UI 自适应**：根据屏幕对角线自动缩放字体与图标（≤4.7"×0.85 / 标准×1.0 / ≥6.5"×1.15）
- **深色主题默认** + 色盲友好状态标识（不依赖颜色单一区分）

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

- **Hub** = GATT Server + 广播者，负责消息中继、成员管理、心跳广播
- **Client** = GATT Client + 扫描者，扫描到匹配广播后连接 Hub
- **GATT 两个特征值**：`command`（Write）+ `notification`（Notify），业务用消息类型字段区分
- **MTU 247**：连接建立后立即协商 MTU，超长消息按 3 字节头自动分片

### 消息协议

紧凑 JSON 格式（约 120-180 字节/条），10 种消息类型：

`JOIN` / `JOIN_ACK` / `MSG` / `SYS` / `HB` / `LEAVE` / `NICK` / `SYNC_REQ` / `SYNC_RSP` / `DISSOLVE`

### 心跳与生命周期

- 心跳间隔 **15s**，超时 **45s** 判定离线，**90s** 移除成员
- 成员列表清空 / 全员定位关闭 → 自动触发解散
- Hub 退出 → UUID 字典序最小优先 + hash(UUID)%3000ms 退避重选

## 项目结构

```
EphemeralChat/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml          # 权限清单 + Application + Service 声明
│       ├── java/com/ephemeral/chat/
│       │   ├── EphemeralChatApplication.kt   # 应用入口，插件注册中心
│       │   ├── MainActivity.kt               # 权限网关 + 主界面宿主
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
│       │   │   ├── chat/                         # 聊天业务（ViewModel + UI）
│       │   │   └── profile/                      # 个人信息
│       │   ├── navigation/                       # 底部导航框架
│       │   ├── protocol/                         # 消息协议 + 分片协议
│       │   └── service/                          # 前台服务（保活）
│       └── res/                                  # 资源文件
├── docs/
│   ├── DesignDoc.md          # 完整设计方案（11 章）
│   └── DevPrompt.md          # 全自动开发提示词
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

# 清理后重新构建
./gradlew clean assembleDebug
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

### 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

或直接将 APK 拷贝到手机安装（需允许未知来源安装）。

## 使用说明

1. **首次启动**：授予蓝牙 + 定位权限（必须，BLE 扫描依赖定位）
2. **创建群聊**：点击「创建群聊」→ 获取 4 位邀请码 → 分享给附近的人
3. **加入群聊**：点击「加入群聊」→ 输入 4 位邀请码 → 自动扫描附近匹配设备并连接
4. **聊天**：发送消息（Hub 中继广播给全体成员）、查看成员列表、修改昵称
5. **退出**：手动退出群聊，或直接关闭定位（自动离群）
6. **解散**：Hub 可解散群聊，全员数据立即清除

## 插件注册顺序

```
LocationPlugin → CryptoPlugin → StoragePlugin → BlePlugin → LifecyclePlugin → ChatPlugin → ProfilePlugin
```

新增功能只需实现 `IPlugin` 接口并注册，即可挂载到主框架，无需改动既有代码。

## 许可

本项目为个人学习项目，仅供学习交流使用。数据全部存储在本地，不收集任何用户信息，不含任何广告、分析、崩溃上报 SDK。