---
AIGC:
  ContentProducer: '001191110102MAD55U9H0F10002'
  ContentPropagator: '001191110102MAD55U9H0F10002'
  Label: '1'
  ProduceID: 'd58af56d-7799-4634-958b-bbf4f84f1ee4'
  PropagateID: 'd58af56d-7799-4634-958b-bbf4f84f1ee4'
  ReservedCode1: '061bdb37-c128-464f-bbc9-d3c1de2bb269'
  ReservedCode2: '061bdb37-c128-464f-bbc9-d3c1de2bb269'
---

# EphemeralChat 全自动开发提示词

> 本文件是 AI 全自动开发 EphemeralChat 应用的完整指令集。AI 应严格按阶段顺序执行，每个 Step 完成后须通过编译验证再进入下一步。禁止跳步、禁止留占位符、禁止臆造 API。

---

## 一、角色与总目标

你是一名资深 Android 开发工程师，精通 Kotlin、Jetpack Compose、Material 3、BLE GATT、Room、Hilt、Coroutines。你现在要独立完成 EphemeralChat——一款基于 BLE P2P 的临时群聊 Android 应用——的全部代码编写。

**总目标**：按照设计方案文档（`docs/DesignDoc.md`）从零搭建完整工程，分阶段交付可编译、可运行、零报错的代码。

**核心原则——稳步准确**：
1. 严格按阶段（Phase）和步骤（Step）顺序执行，不跳步
2. 每个 Step 完成后必须确保 `./gradlew assembleDebug` 编译通过
3. 禁止使用 `TODO()`、`FIXME`、空实现、占位符返回值
4. 禁止臆造不存在的 API 或库方法，所有 API 调用须基于 Android SDK 34 / Jetpack Compose 最新稳定版
5. 每个文件须有清晰的 KDoc 注释说明文件职责
6. 遇到设计方案中未明确的细节，选择最简单可靠的方案并在代码注释中说明决策理由

---

## 二、全局约束

### 2.1 技术栈（必须遵守，不可替换）

| 层次 | 技术 | 版本约束 |
|------|------|---------|
| 语言 | Kotlin | 2.0+ |
| UI | Jetpack Compose + Material 3 | Compose BOM 2024.06+ |
| 导航 | Compose Navigation | 与 Compose BOM 对齐 |
| 异步 | Coroutines + Flow | 1.8+ |
| DI | Hilt | 2.51+ |
| 存储 | Room + SQLCipher | Room 2.6+，SQLCipher 4.5+ |
| 偏好 | DataStore | 1.1+ |
| 后台 | Foreground Service + WorkManager | WorkManager 2.9+ |
| BLE | Android Bluetooth LE API | 原生 API，无第三方库 |
| 加密 | Android Keystore + javax.crypto | 系统 API |
| 构建 | Gradle Kotlin DSL | Gradle 8.5+ |
| SDK | compileSdk 34, minSdk 26, targetSdk 34 | minSdk 26 保证 BLE 广播可用 |

### 2.2 命名规范

- 包名：`com.epheremal.chat`（小写，无下划线）
- 文件与目录名：英文 PascalCase（如 `BlePlugin.kt`、`PluginRegistry.kt`）
- 类名：PascalCase（如 `PluginRegistry`）
- 函数/变量：camelCase（如 `registerPlugin`）
- 常量：UPPER_SNAKE_CASE（如 `MAX_CONNECTION_COUNT`）
- Compose 组件函数：PascalCase（如 `ChatScreen`）
- 资源文件：snake_case（如 `ic_chat.xml`）

### 2.3 编码硬性规则

1. **零占位符**：所有函数必须有完整实现，禁止 `TODO()`、`return null // TODO`、空函数体
2. **空安全**：所有可空类型必须显式处理，禁止 `!!` 强解（除非有明确注释说明为何安全）
3. **错误处理**：所有 IO 操作（BLE、数据库、文件）必须 try-catch 并有明确的错误处理逻辑，不允许静默吞异常
4. **生命周期**：所有 Coroutine Scope 必须绑定到合适的 Lifecycle（viewModelScope / lifecycleScope / service scope），禁止使用 GlobalScope
5. **注释语言**：代码注释用中文，KDoc 注释用中文，但变量名/函数名用英文
6. **日志**：使用 `android.util.Log`，Tag 为类名，关键流程（BLE 连接/断开、消息收发、状态变更）须打印日志
7. **无第三方分析 SDK**：不引入任何分析、崩溃上报、广告 SDK
8. **Material 3 优先**：所有 UI 组件优先使用 Material 3 组件，不自造轮子
9. **深色主题为默认**：`darkTheme = true`，同时支持浅色主题切换
10. **色盲友好**：状态指示不依赖颜色单一区分，辅以图标/文字

### 2.4 项目目录结构（严格遵循）

```
EphemeralChat/
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/ephemeral/chat/
│   │   │   │   ├── EphemeralChatApplication.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── core/
│   │   │   │   │   ├── registry/
│   │   │   │   │   │   └── PluginRegistry.kt
│   │   │   │   │   ├── eventbus/
│   │   │   │   │   │   ├── EventBus.kt
│   │   │   │   │   │   └── AppEvent.kt
│   │   │   │   │   ├── plugin/
│   │   │   │   │   │   └── IPlugin.kt
│   │   │   │   │   └── ui/
│   │   │   │   │       ├── theme/
│   │   │   │   │       │   ├── Color.kt
│   │   │   │   │       │   ├── Theme.kt
│   │   │   │   │       │   └── Typography.kt
│   │   │   │   │       └── adaptive/
│   │   │   │   │           ├── AdaptiveScale.kt
│   │   │   │   │           └── AdaptiveModifiers.kt
│   │   │   │   ├── plugins/
│   │   │   │   │   ├── location/
│   │   │   │   │   │   ├── LocationPlugin.kt
│   │   │   │   │   │   └── GeohashUtils.kt
│   │   │   │   │   ├── ble/
│   │   │   │   │   │   ├── BlePlugin.kt
│   │   │   │   │   │   ├── BleAdvertiser.kt
│   │   │   │   │   │   ├── BleScanner.kt
│   │   │   │   │   │   ├── GattServer.kt
│   │   │   │   │   │   ├── GattClient.kt
│   │   │   │   │   │   └── BleConstants.kt
│   │   │   │   │   ├── storage/
│   │   │   │   │   │   ├── StoragePlugin.kt
│   │   │   │   │   │   ├── DatabaseManager.kt
│   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   ├── GroupEntity.kt
│   │   │   │   │   │   │   ├── MemberEntity.kt
│   │   │   │   │   │   │   └── MessageEntity.kt
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── GroupDao.kt
│   │   │   │   │   │   │   ├── MemberDao.kt
│   │   │   │   │   │   │   └── MessageDao.kt
│   │   │   │   │   │   └── EphemeralDatabase.kt
│   │   │   │   │   ├── crypto/
│   │   │   │   │   │   ├── CryptoPlugin.kt
│   │   │   │   │   │   ├── KeyManager.kt
│   │   │   │   │   │   └── CryptoUtils.kt
│   │   │   │   │   ├── lifecycle/
│   │   │   │   │   │   ├── LifecyclePlugin.kt
│   │   │   │   │   │   ├── HeartbeatManager.kt
│   │   │   │   │   │   ├── MemberStateMachine.kt
│   │   │   │   │   │   └── HubElection.kt
│   │   │   │   │   ├── chat/
│   │   │   │   │   │   ├── ChatPlugin.kt
│   │   │   │   │   │   ├── ChatViewModel.kt
│   │   │   │   │   │   ├── NicknameGenerator.kt
│   │   │   │   │   │   └── ui/
│   │   │   │   │   │       ├── ChatScreen.kt
│   │   │   │   │   │       ├── CreateGroupScreen.kt
│   │   │   │   │   │       ├── JoinGroupScreen.kt
│   │   │   │   │   │       ├── MemberListScreen.kt
│   │   │   │   │   │       └── ChatComponents.kt
│   │   │   │   │   └── profile/
│   │   │   │   │       ├── ProfilePlugin.kt
│   │   │   │   │       ├── ProfileViewModel.kt
│   │   │   │   │       └── ui/
│   │   │   │   │           └── ProfileScreen.kt
│   │   │   │   ├── navigation/
│   │   │   │   │   ├── NavDestination.kt
│   │   │   │   │   └── BottomNavHost.kt
│   │   │   │   ├── protocol/
│   │   │   │   │   ├── MessageProtocol.kt
│   │   │   │   │   ├── MessageType.kt
│   │   │   │   │   └── MessageFragmenter.kt
│   │   │   │   └── service/
│   │   │   │       └── EphemeralForegroundService.kt
│   │   │   └── res/
│   │   │       ├── values/
│   │   │       │   ├── strings.xml
│   │   │       │   ├── colors.xml
│   │   │       │   └── themes.xml
│   │   │       ├── values-night/
│   │   │       │   └── themes.xml
│   │   │       └── drawable/
│   │   └── build/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── docs/
│   ├── DesignDoc.md
│   └── DevPrompt.md
└── .temp/
```

### 2.5 关键设计决策回顾（必须在代码中体现）

| 决策 | 要点 |
|------|------|
| BLE 星型拓扑 | Hub = GATT Server + 广播；Client = GATT Client + 通知接收 |
| 邀请码+Geohash | 广播内容 = SHA-256(4位码 + Geohash精度5) 截前4字节 |
| 9 邻域扫描 | 加入者计算自身 + 8 邻域共 9 个 hash 同时匹配 |
| GATT 两特征值 | `command`(Write) + `notification`(Notify)，业务用消息类型字段区分 |
| MTU 247 | 连接建立后立即请求 MTU=247 |
| 消息分片 | >MTU 的消息按 3 字节头分片，10s 超时丢弃 |
| 心跳 15s/超时 45s/移除 90s | 三个时间阈值严格按此设置 |
| Hub 重选 | UUID 字典序最小优先 + hash(UUID)%3000ms 退避 |
| 定位=心跳电源 | 定位关闭→BLE 停止→心跳停止→判定离群 |
| 随机用户名 | 50形容词×50动物×90编号=225,000组合，重名加"(2)"后缀 |
| 消息加密 | ECDH(secp256r1) 协商 → AES-256-GCM 加密，密钥仅内存 |
| 数据库加密 | SQLCipher 全库加密，密钥由 Android Keystore 生成 |
| 解散即清除 | 删除全部本地数据，物理擦除 |
| UI 自适应 | 屏幕对角线≤4.7"×0.85 / 标准×1.0 / ≥6.5"×1.15 |
| 底部导航 | 群聊页 + 我的页，架构支持动态扩展 |
| 插件化 | IPlugin 接口 + PluginRegistry(拓扑排序启动) + EventBus(SharedFlow) |

---

## 三、项目环境与配置

### 3.1 开发环境

- 操作系统：Windows
- IDE：VS Code（配合命令行构建）
- JDK：17（路径 D:\DevTools\jdk-17）
- Android SDK：已安装（compileSdk 34）
- Gradle：8.5+
- Kotlin：2.0+

### 3.2 Gradle 配置要求

**`settings.gradle.kts`**：
- `pluginManagement` 使用 `gradlePluginPortal()` + `google()` + `mavenCentral()`
- `dependencyResolutionManagement` 使用 `google()` + `mavenCentral()`

**根 `build.gradle.kts`**：
- 应用 Kotlin 2.0+ 插件
- 应用 Hilt 插件
- 应用 Compose Compiler 插件（Kotlin 2.0+ 方式：`org.jetbrains.kotlin.plugin.compose`）

**`app/build.gradle.kts`** 关键配置：
- `compileSdk = 34`，`minSdk = 26`，`targetSdk = 34`
- 启用 Compose（`buildFeatures { compose = true }`）
- 启用 ViewBinding（`buildFeatures { viewBinding = true }`）
- 依赖列表须包含：Compose BOM、Material 3、Navigation Compose、Hilt、Room（含 compiler）、SQLCipher、DataStore、Coroutines、WorkManager、Lifecycle ViewModel Compose
- 所有依赖使用最新稳定版，禁止使用 alpha/beta 版本（除非无稳定版替代）

### 3.3 AndroidManifest.xml 完整权限清单

```xml
<!-- BLE（Android 12 以下） -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<!-- BLE（Android 12+） -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<!-- 定位（BLE 扫描必需） -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<!-- 前台服务 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<!-- 蓝牙硬件声明 -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

注意：`BLUETOOTH_SCAN` 的 `neverForLocation` flag 仅在不依赖 BLE 读取位置时使用。本项目需要定位权限用于 Geohash 计算，因此不加 `neverForLocation`，而是在运行时同时请求定位权限。

---

## 四、分阶段开发指令

### Phase 0：项目脚手架（必须最先完成）

**目标**：创建可编译的空项目骨架，验证 Gradle 构建链路通畅。

**Step 0.1：创建 Gradle 构建文件**

创建以下文件，确保内容完整可编译：

1. `settings.gradle.kts`：配置仓库源
2. 根 `build.gradle.kts`：声明插件版本
3. `app/build.gradle.kts`：完整依赖配置
4. `gradle.properties`：配置 JVM 参数（`org.gradle.jvmargs=-Xmx2048m`）、AndroidX 兼容
5. `app/src/main/AndroidManifest.xml`：完整权限清单 + Application 声明

**Step 0.2：创建 Application 与 MainActivity 骨架**

1. `EphemeralChatApplication.kt`：
   - 继承 `Application`
   - 标注 `@HiltAndroidApp`
   - 在 `onCreate()` 中初始化 `PluginRegistry`（先创建空实例，后续 Phase 填充插件）

2. `MainActivity.kt`：
   - 标注 `@AndroidEntryPoint`
   - 继承 `ComponentActivity`
   - `onCreate()` 中 `setContent` 调用 `EphemeralChatTheme { BottomNavHost() }`
   - 此时 `BottomNavHost` 和 `EphemeralChatTheme` 可以是临时空 Composable（显示一个 `Text("EphemeralChat")`），Phase 1 再替换

**Step 0.3：创建 res 资源文件**

1. `values/strings.xml`：app_name = "EphemeralChat"
2. `values/colors.xml`：基础颜色定义（Material 3 色板）
3. `values/themes.xml`：Material 3 主题（深色为默认）
4. `values-night/themes.xml`：夜间主题覆盖

**Step 0.4：编译验证**

执行 `./gradlew assembleDebug`，必须编译通过，零报错。如有错误必须修复后再进入下一阶段。

---

### Phase 1：架构基建

**目标**：搭建插件化框架、UI 自适应系统、底部导航框架。这三者是所有后续插件的运行基础。

#### Step 1.1：插件框架核心

创建以下文件，实现完整的插件化基础设施：

**文件 1：`core/plugin/IPlugin.kt`**

```kotlin
/**
 * 插件接口——所有功能模块的统一抽象。
 * 生命周期顺序：onInit → onStart → onStop → onDestroy
 */
interface IPlugin {
    val pluginId: String
    val dependencies: List<String>
        get() = emptyList()

    fun onInit(registry: PluginRegistry, eventBus: EventBus)
    fun onStart()
    fun onStop()
    fun onDestroy()
}
```

实现要求：
- 接口定义完整，无遗漏
- KDoc 注释说明每个方法的调用时机和线程上下文
- `dependencies` 默认空列表，子类可覆盖

**文件 2：`core/eventbus/AppEvent.kt`**

定义所有跨插件事件的密封类。必须包含设计方案中列出的所有事件类型：

```kotlin
sealed class AppEvent {
    // BLE 相关
    data class DeviceDiscovered(val deviceAddress: String, val codeHash: ByteArray) : AppEvent()
    data class GattConnected(val deviceAddress: String) : AppEvent()
    data class GattDisconnected(val deviceAddress: String) : AppEvent()
    // 消息相关
    data class MessageReceived(val content: String, val senderUuid: String, val senderName: String, val timestamp: Long) : AppEvent()
    data class MessageSent(val msgId: String) : AppEvent()
    // 成员相关
    data class MemberJoined(val uuid: String, val nickname: String) : AppEvent()
    data class MemberLeft(val uuid: String) : AppEvent()
    data class MemberOffline(val uuid: String) : AppEvent()
    data class NicknameChanged(val uuid: String, val newNickname: String) : AppEvent()
    // 定位相关
    data class LocationStateChanged(val enabled: Boolean) : AppEvent()
    data class LocationAcquired(val latitude: Double, val longitude: Double) : AppEvent()
    // 群聊生命周期
    data class GroupCreated(val groupId: String, val code: String) : AppEvent()
    data class GroupJoined(val groupId: String, val nickname: String) : AppEvent()
    data class GroupDissolved(val groupId: String) : AppEvent()
    data class HubChanged(val newHubUuid: String) : AppEvent()
    data class HeartbeatReceived(val uuid: String, val timestamp: Long) : AppEvent()
    data class HeartbeatTimeout(val uuid: String) : AppEvent()
}
```

实现要求：
- 所有 `data class` 的 `ByteArray` 类型须重写 `equals()` 和 `hashCode()`（用内容比较）
- 每个事件类须有 KDoc 说明触发条件和消费方

**文件 3：`core/eventbus/EventBus.kt`**

```kotlin
/**
 * 事件总线——基于 SharedFlow 的发布/订阅通信。
 * 插件间零耦合通信通道，所有事件通过 channel 名分区。
 */
class EventBus {
    // 使用 MutableSharedFlow 实现，replay=0，extraBufferCapacity=64
    // 每个通道独立，互不干扰
    // publish 是 suspend 函数
    // subscribe 返回 Flow<AppEvent>
    // 线程安全：使用 ConcurrentHashMap 或同步锁
}
```

实现要求：
- `publish(channel: String, event: AppEvent)`：suspend 函数，向指定频道发布事件
- `subscribe(channel: String): Flow<AppEvent>`：返回指定频道的事件流
- 内部用 `mutableMapOf<String, MutableSharedFlow<AppEvent>>`，加同步锁保证线程安全
- `MutableSharedFlow` 配置：`replay = 0, extraBufferCapacity = 64, onBufferOverflow = DROP_OLDEST`
- 提供 `clearAll()` 方法清除所有频道（用于解散时清理）

**文件 4：`core/registry/PluginRegistry.kt`**

```kotlin
/**
 * 插件注册中心——管理所有插件的生命周期与依赖关系。
 * 负责按拓扑排序启动插件，确保依赖先于被依赖者启动。
 */
class PluginRegistry(private val eventBus: EventBus) {
    // 插件存储：Map<String, IPlugin>
    // 导航项存储：MutableList<NavDestination>
    // 注册方法：检查依赖→存入→调用 onInit
    // 启动方法：拓扑排序→依次调用 onStart
    // 停止方法：逆拓扑排序→依次调用 onStop
    // 销毁方法：逆拓扑排序→依次调用 onDestroy
    // 注册/获取导航项
    // 获取其他插件实例（泛型方法）
}
```

实现要求：
- `register(plugin: IPlugin)`：注册前检查 `dependencies` 列表中所有依赖是否已注册，未注册则抛出 `IllegalStateException` 并说明缺哪个依赖
- `startAll()`：对已注册插件做拓扑排序（Kahn 算法），按依赖顺序依次调用 `onStart()`
- `stopAll()`：逆拓扑排序调用 `onStop()`
- `destroyAll()`：逆拓扑排序调用 `onDestroy()`
- `registerNavDestination(dest: NavDestination)` / `getNavDestinations(): List<NavDestination>`
- `getPlugin<T>(id: String): T?`：泛型方法，用 `as?` 安全转型
- 拓扑排序须处理循环依赖（检测到环则抛出异常）
- 所有公开方法须线程安全（`synchronized` 或 `ReentrantLock`）

**文件 5：`navigation/NavDestination.kt`**

```kotlin
/**
 * 底部导航项定义。
 * 每个 UI 插件注册自己的导航项，BottomNavHost 动态拉取渲染。
 */
sealed class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    abstract val content: @Composable () -> Unit

    data object Chat : NavDestination("chat", "群聊", Icons.AutoMirrored.Filled.Chat)
    data object Profile : NavDestination("profile", "我的", Icons.Default.Person)
}
```

实现要求：
- `content` 为抽象 Composable 属性，子类提供具体 UI 实现
- `route` 唯一，用作 NavHost 路由键
- 后续新增 Tab 只需新增 `data object`，无需修改 `NavDestination`

**编译验证**：执行 `./gradlew assembleDebug`，零报错。

---

#### Step 1.2：UI 自适应系统

**文件 1：`core/ui/adaptive/AdaptiveScale.kt`**

```kotlin
/**
 * UI 自适应缩放系统。
 * 根据屏幕物理对角线尺寸计算缩放因子，通过 CompositionLocal 全局注入。
 * 所有 UI 组件统一使用 adaptiveSp() / adaptiveDp() 声明尺寸。
 */

// 1. 定义 LocalAdaptiveScale: compositionLocalOf<Float>，默认 1.0f
// 2. 定义 AdaptiveScaleProvider Composable：
//    - 通过 LocalConfiguration 获取屏幕宽高（dp）
//    - 通过 LocalDensity 获取屏幕密度
//    - 计算对角线英寸 = sqrt((widthDp/density)^2 + (heightDp/density)^2) / 25.4
//    - 推导缩放因子：≤4.7"→0.85, ≥6.5"→1.15, 其余→1.0
//    - 用 CompositionLocalProvider 注入 LocalAdaptiveScale
// 3. 定义 adaptiveSp(base: Float): TextUnit —— 取 LocalAdaptiveScale.current 乘以 base
// 4. 定义 adaptiveDp(base: Float): Dp —— 同上
```

实现要求：
- 对角线计算须考虑 `LocalConfiguration.screenWidthDp` 和 `screenHeightDp` 的单位是 dp，需除以 `density.density` 转为英寸
- 使用 `kotlin.math.sqrt` 和 `kotlin.math.pow`
- `AdaptiveScaleProvider` 须包裹在最外层（Theme 之前），使 Theme 内部也能读取缩放因子

**文件 2：`core/ui/theme/Color.kt`**

定义 Material 3 深色主题和浅色主题色板：
- 深色主题：背景 `#1A1A2E`，表面 `#16213E`，主色 `#0F3460`，次色 `#E94560`，OnPrimary `#FFFFFF`
- 浅色主题：背景 `#F5F5F5`，表面 `#FFFFFF`，主色 `#1976D2`，次色 `#E53935`，OnPrimary `#FFFFFF`
- 使用 `Color` 类定义，不使用十六进制硬编码在 Composable 中

**文件 3：`core/ui/theme/Typography.kt`**

定义 Material 3 Typography，所有字号通过 `adaptiveSp()` 设置：
- `headlineLarge` → 32sp 基准
- `headlineMedium` → 28sp 基准
- `titleLarge` → 22sp 基准
- `titleMedium` → 16sp 基准
- `bodyLarge` → 16sp 基准
- `bodyMedium` → 14sp 基准
- `bodySmall` → 12sp 基准
- `labelLarge` → 14sp 基准
- `labelMedium` → 12sp 基准
- `labelSmall` → 11sp 基准
- 实际字号 = 基准 × LocalAdaptiveScale.current

**文件 4：`core/ui/theme/Theme.kt`**

```kotlin
/**
 * EphemeralChat 主题入口。
 * 深色为默认，支持浅色切换。
 * 集成自适应缩放：先 AdaptiveScaleProvider → 再 MaterialTheme。
 */
@Composable
fun EphemeralChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 1. AdaptiveScaleProvider 包裹
    // 2. 根据 darkTheme 选择 ColorScheme
    // 3. 创建 adaptiveTypography（从基准 Typography × scale）
    // 4. MaterialTheme(colorScheme, typography, content)
}
```

实现要求：
- `darkTheme` 默认为 `isSystemInDarkTheme()`，但应用内默认深色（Phase 3 加 DataStore 持久化偏好）
- Typography 的所有字号须乘以 `LocalAdaptiveScale.current`
- 主题须同时定义 `colorScheme` 和 `typography`

**文件 5：`core/ui/adaptive/AdaptiveModifiers.kt`**

提供便捷的 Composable 扩展函数：

```kotlin
/** 自适应图标尺寸 Modifier */
@Composable
fun Modifier.adaptiveIconSize(base: Float): Modifier =
    this.size(adaptiveDp(base))

/** 自适应内边距 */
@Composable
fun Modifier.adaptivePadding(base: Float): Modifier =
    this.padding(adaptiveDp(base))
```

**编译验证**：执行 `./gradlew assembleDebug`，零报错。

---

#### Step 1.3：底部导航框架

**文件：`navigation/BottomNavHost.kt`**

```kotlin
/**
 * 底部导航主框架。
 * 从 PluginRegistry 动态获取导航项列表，渲染 NavigationBar + NavHost。
 * 新增 Tab 只需插件注册 NavDestination，无需修改此文件。
 */
@Composable
fun BottomNavHost() {
    // 1. 从 PluginRegistry（通过 Hilt 或 Application 单例）获取 getNavDestinations()
    // 2. 创建 NavController
    // 3. Scaffold + NavigationBar：
    //    - 遍历 navItems 渲染 NavigationBarItem
    //    - 选中状态绑定到 NavController 当前路由
    //    - icon 和 label 使用 adaptiveSp/adaptiveDp
    // 4. NavHost：
    //    - startDestination = navItems.first().route
    //    - 每个 dest.route 对应 composable { dest.content() }
    // 5. 底部导航栏图标/文字尺寸使用 adaptiveSp(12f) / adaptiveDp(24f)
}
```

实现要求：
- `PluginRegistry` 实例通过 `LocalContext.current.applicationContext as EphemeralChatApplication` 获取
- NavigationBar 的 `selected` 状态须与 NavController 的实际路由同步（监听 `navController.currentBackStackEntryAsState()`）
- 切换 Tab 时使用 `navController.navigate(route) { popUpTo(startDestination) { saveState = true } }` 保证不重复入栈
- 底部导航的图标和文字必须通过 `adaptiveSp`/`adaptiveDp` 自适应

**更新 `MainActivity.kt`**：将临时空 Composable 替换为真正的 `EphemeralChatTheme { BottomNavHost() }`。

**更新 `EphemeralChatApplication.kt`**：在 `onCreate()` 中创建 `PluginRegistry` 和 `EventBus` 实例，注册 `ChatPlaceholder` 和 `ProfilePlaceholder` 两个临时插件（Phase 3 替换为真实插件）。临时插件只注册 NavDestination，显示 `Text("群聊（开发中）")` / `Text("我的（开发中）")`。

**编译验证**：执行 `./gradlew assembleDebug`，零报错。启动 App 须显示底部导航栏，可切换两个 Tab。

---

#### Step 1.4：权限请求框架

**在 `MainActivity.kt` 中实现运行时权限请求**：

- 使用 `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`
- 需请求的权限列表（Android 12+）：`BLUETOOTH_SCAN`、`BLUETOOTH_ADVERTISE`、`BLUETOOTH_CONNECT`、`ACCESS_FINE_LOCATION`
- 需请求的权限列表（Android 11 及以下）：`BLUETOOTH`、`BLUETOOTH_ADMIN`、`ACCESS_FINE_LOCATION`
- 根据 `Build.VERSION.SDK_INT` 分组
- 权限被拒绝时显示解释弹窗（`AlertDialog`），引导用户到设置开启
- 权限全部通过后才进入 `BottomNavHost`

**编译验证**：执行 `./gradlew assembleDebug`，零报错。

---

### Phase 2：核心插件实现

**目标**：实现五个核心插件（定位、BLE、存储、加密、协议），为业务层提供底层能力。

#### Step 2.1：定位插件

**文件 1：`plugins/location/GeohashUtils.kt`**

```kotlin
/**
 * Geohash 编码工具。
 * 将经纬度编码为指定精度的 Geohash 字符串。
 * 精度 5 ≈ 5km × 5km 网格。
 */
object GeohashUtils {
    private val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** 编码：经纬度 → Geohash 字符串 */
    fun encode(lat: Double, lon: Double, precision: Int = 5): String

    /** 计算给定 Geohash 的 8 个邻域（含自身共 9 个） */
    fun neighbors(geohash: String): List<String>

    /** 计算两点间距离（米），Haversine 公式 */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
}
```

实现要求：
- `encode()`：标准 Geohash base32 编码算法，逐位二分查找
- `neighbors()`：返回给定 Geohash 的 N/S/E/W/NE/NW/SE/SW 共 8 个邻域 + 自身 = 9 个
- `distanceMeters()`：Haversine 公式，地球半径 6371000m
- 所有方法须有单元测试级别的健壮性（处理极值：纬度 ±90，经度 ±180）

**文件 2：`plugins/location/LocationPlugin.kt`**

```kotlin
/**
 * 定位服务插件。
 * 职责：
 * 1. 获取当前 GPS 位置（单次）
 * 2. 监测定位开关状态（每 15s 轮询）
 * 3. 定位状态变化时发布 LocationStateChanged 事件
 * 4. 依赖：无（底层插件）
 */
class LocationPlugin : IPlugin {
    override val pluginId = "location"
    override val dependencies = emptyList<String>()

    // onInit: 获取 LocationManager，订阅定位状态轮询
    // onStart: 启动定位状态监测协程（15s 间隔）
    // onStop: 停止监测
    // onDestroy: 清理资源

    // 公开方法：
    // fun getCurrentLocation(callback: (Double, Double) -> Unit)
    //   - 通过 LocationManager.getLastKnownLocation() 或 requestSingleUpdate()
    //   - 回调在主线程
    // fun isLocationEnabled(): Boolean
    //   - GPS_PROVIDER 或 NETWORK_PROVIDER 任一可用即 true
}
```

实现要求：
- 需要 `Context`：通过 `onInit` 的 `registry` 获取 Application Context（`PluginRegistry` 须持有 `Context` 引用，在构造时传入）
- 定位状态监测：用 `launchIn` + `delay(15_000)` 循环检测 `LocationManager.isProviderEnabled`
- 状态变化时通过 `EventBus.publish("location_state", AppEvent.LocationStateChanged(enabled))`
- `getCurrentLocation()` 优先用 `getLastKnownLocation(GPS_PROVIDER)`，为 null 则 `requestSingleUpdate`，超时 10s 回退到 `NETWORK_PROVIDER`
- 所有 LocationManager 调用须 try-catch `SecurityException`（权限未授予时）

**更新 `PluginRegistry`**：构造函数须接收 `Context` 参数，供插件获取系统服务。

**更新 `EphemeralChatApplication.kt`**：创建 `PluginRegistry(eventBus, applicationContext)`，注册 `LocationPlugin`。

**编译验证**：零报错。

---

#### Step 2.2：BLE 发现与通信插件

**文件 1：`plugins/ble/BleConstants.kt`**

```kotlin
/**
 * BLE 相关常量定义。
 */
object BleConstants {
    // GATT Service UUID（自定义）
    const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    // Command 特征值 UUID（Client→Hub Write）
    const val COMMAND_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
    // Notification 特征值 UUID（Hub→All Notify）
    const val NOTIFICATION_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
    // 广播 Service UUID（用于扫描过滤）
    const val ADVERTISE_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    // MTU
    const val REQUESTED_MTU = 247
    // 广播间隔
    const val ADVERTISE_INTERVAL_MS = 250L
    // 最大连接数
    const val MAX_CONNECTIONS = 7
    // 距离阈值（米）
    const val DISTANCE_THRESHOLD_M = 100.0
}
```

**文件 2：`plugins/ble/BleAdvertiser.kt`**

```kotlin
/**
 * BLE 广播管理器。
 * 职责：启动/停止 BLE 广播，广播内容包含 code_hash + group_id_short。
 */
class BleAdvertiser(private val context: Context) {
    // BluetoothLeAdvertiser 实例
    // start(codeHash: ByteArray, groupIdShort: ByteArray):
    //   - 构建 AdvertiseSettings: LOW_LATENCY, 250ms 间隔
    //   - 构建 AdvertiseData: Service UUID + Service Data(codeHash + groupIdShort)
    //   - 启动广播，回调中发布事件
    // stop(): 停止广播
    // switchToLowPower(): 切换为低功耗模式（1s 间隔），群聊建立后调用
}
```

实现要求：
- `AdvertiseSettings`：`setAdvertiseMode(ADVERTISE_MODE_LOW_LATENCY)`，`setInterval(250L)`（单位为 0.625ms，250ms ≈ 400 单位）
- `AdvertiseData`：`addServiceUuid(ParcelUuid(SERVICE_UUID))`，`addServiceData(ParcelUuid(SERVICE_UUID), payload)`，payload = codeHash(4 bytes) + groupIdShort(4 bytes)
- 须检查 `bluetoothAdapter.isMultipleAdvertisementSupported`，不支持时发布错误事件
- 回调中 `onStartSuccess`/`onStartFailure` 须打印日志

**文件 3：`plugins/ble/BleScanner.kt`**

```kotlin
/**
 * BLE 扫描管理器。
 * 职责：扫描 BLE 广播，过滤匹配的 Service UUID，回调匹配结果。
 */
class BleScanner(private val context: Context) {
    // BluetoothLeScanner 实例
    // start(targetHashes: List<ByteArray>, onMatched: (String, ByteArray) -> Unit):
    //   - 构建 ScanFilter: Service UUID 匹配
    //   - 构建 ScanSettings: LOW_LATENCY
    //   - 回调中比对 codeHash 与 targetHashes
    //   - 匹配成功调用 onMatched(deviceAddress, groupIdShort)
    // stop(): 停止扫描
}
```

实现要求：
- `ScanFilter`：`setServiceUuid(ParcelUuid(SERVICE_UUID))`
- `ScanSettings`：`setScanMode(SCAN_MODE_LOW_LATENCY)`
- 回调中从 `scanResult.scanRecord.getServiceData(ParcelUuid(SERVICE_UUID))` 提取 payload，前 4 字节为 codeHash
- 匹配逻辑：遍历 `targetHashes`，用 `contentEquals` 比对
- 须处理 `onScanFailed` 错误码
- 扫描须设置超时（30s），超时自动停止并回调

**文件 4：`plugins/ble/GattServer.kt`**

```kotlin
/**
 * GATT Server 端（Hub 角色）。
 * 职责：提供 GATT Service 供 Client 连接，接收 Client 写入，向 Client 发送通知。
 */
class GattServer(private val context: Context) {
    // BluetoothGattServer 实例
    // 已连接设备列表: Map<String, BluetoothDevice>
    // onMessageReceived: (ByteArray) -> Unit  回调
    // start():
    //   - 添加 GATT Service（command + notification 两个特征值）
    //   - notification 特征值须设置 PROPERTY_NOTIFY + PROPERTY_READ
    //   - command 特征值须设置 PROPERTY_WRITE + WRITE_TYPE_DEFAULT
    //   - 设置 BluetoothGattServerCallback
    // onConnectionStateChange: 管理连接列表
    // onCharacteristicWrite: 解析写入数据，回调 onMessageReceived
    // broadcast(data: ByteArray): 向所有已连接 Client 发送通知
    //   - 遍历设备列表，设置 notification 特征值值 → notifyCharacteristicChanged
    // stop(): 关闭 Server，清除连接
    // setMtu(device: BluetoothDevice, mtu: Int): 请求 MTU
}
```

实现要求：
- `BluetoothGattService`：`UUID.randomUUID()` 或使用固定 UUID，type = `SERVICE_TYPE_PRIMARY`
- `command` 特征值：`PROPERTY_WRITE` | `PROPERTY_WRITE_NO_RESPONSE`，`WRITE_TYPE_DEFAULT`
- `notification` 特征值：`PROPERTY_NOTIFY` | `PROPERTY_READ`
- 须为 `notification` 特征值添加 `BluetoothGattDescriptor`（CLIENT_CHARACTERISTIC_CONFIGURATION，UUID `00002902-0000-1000-8000-00805f9b34fb`），值为 `ENABLE_NOTIFICATION_VALUE`
- `onCharacteristicWriteRequest` 中须校验 offset、确认发送 `sendResponse`
- `broadcast()` 中须先 `setValue` 再 `notifyCharacteristicChanged`
- 连接/断开时发布 `GattConnected`/`GattDisconnected` 事件
- 须维护 `Map<String, BluetoothDevice>` 已连接设备列表，线程安全

**文件 5：`plugins/ble/GattClient.kt`**

```kotlin
/**
 * GATT Client 端（Client 角色）。
 * 职责：连接 Hub 的 GATT Server，写入指令，接收通知。
 */
class GattClient(private val context: Context) {
    // BluetoothGatt 实例
    // onNotification: (ByteArray) -> Unit  回调
    // connect(deviceAddress: String):
    //   - BluetoothAdapter.getRemoteDevice(address)
    //   - device.connectGatt(context, false, callback)
    //   - onConnectionStateChange → 连接成功后 discoverServices
    //   - onServicesDiscovered → 请求 MTU 247 → 启用 notification
    // write(data: ByteArray):
    //   - 获取 command 特征值，setValue + writeCharacteristic
    // disconnect(): 关闭连接
}
```

实现要求：
- `connectGatt` 使用 `false`（不自动重连）
- `onServicesDiscovered` 后：`gatt.requestMtu(247)` → `onMtuChanged` 中启用通知
- 启用通知：设置 `descriptor.value = ENABLE_NOTIFICATION_VALUE`，`gatt.writeDescriptor(descriptor)`
- `onCharacteristicChanged` 中回调 `onNotification(value)`
- 须处理 `onConnectionStateChange(STATE_DISCONNECTED)`，发布 `GattDisconnected` 事件
- `write()` 须检查 GATT 连接状态，未连接时缓存或返回 false

**文件 6：`plugins/ble/BlePlugin.kt`**

```kotlin
/**
 * BLE 插件——整合 Advertiser + Scanner + GattServer/GattClient。
 * 依赖：LocationPlugin（定位关闭时须停止 BLE）
 */
class BlePlugin : IPlugin {
    override val pluginId = "ble"
    override val dependencies = listOf("location")

    // 持有 BleAdvertiser, BleScanner, GattServer, GattClient
    // onInit:
    //   - 获取 BluetoothAdapter
    //   - 订阅 "location_state" 事件，定位关闭时停止广播+扫描
    //   - 发布 "ble_state" 事件
    // onStart: 准备资源（不自动启动广播）
    // onStop: 停止所有 BLE 活动
    // onDestroy: 释放资源

    // 公开方法供 ChatPlugin 调用：
    // fun startAdvertising(codeHash: ByteArray, groupIdShort: ByteArray)
    // fun stopAdvertising()
    // fun startScanning(targetHashes: List<ByteArray>, onMatched: ...)
    // fun stopScanning()
    // fun startGattServer(onMessage: (ByteArray) -> Unit)
    // fun stopGattServer()
    // fun connectAsClient(deviceAddress: String, onNotification: (ByteArray) -> Unit)
    // fun disconnectClient()
    // fun broadcastToClients(data: ByteArray)
    // fun writeToHub(data: ByteArray)
    // fun isBluetoothEnabled(): Boolean
}
```

实现要求：
- `onInit` 中订阅 `EventBus.subscribe("location_state")`，收到 `LocationStateChanged(false)` 时停止广播和扫描
- `BluetoothAdapter` 为 null 时发布错误事件（设备不支持蓝牙）
- 所有公开方法须检查蓝牙是否开启，未开启时返回 false/空并打印日志
- `GATT Server` 和 `GattClient` 不会同时运行（Hub 用 Server，Client 用 Client），但切换时须先关闭旧的

**更新 `EphemeralChatApplication.kt`**：注册 `BlePlugin`（须在 `LocationPlugin` 之后注册）。

**编译验证**：零报错。

---

#### Step 2.3：存储插件

**文件 1：`plugins/storage/entity/GroupEntity.kt`**

```kotlin
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,      // UUID
    val code: String,                      // 4 位邀请码
    val hubUuid: String,                   // 当前 Hub UUID
    val isHub: Boolean,                    // 本机是否为 Hub
    val createdAt: Long,                   // 创建时间戳
    val memberCount: Int,                  // 成员数
    val status: GroupStatus                // 群组状态
)

enum class GroupStatus { ACTIVE, DISSOLVING, DISSOLVED }
```

**文件 2：`plugins/storage/entity/MemberEntity.kt`**

```kotlin
@Entity(tableName = "members", foreignKeys = [
    ForeignKey(entity = GroupEntity::class, parentColumns = ["groupId"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)
])
data class MemberEntity(
    @PrimaryKey val memberUuid: String,
    val groupId: String,
    val nickname: String,
    val isSelf: Boolean,
    val status: MemberStatus,
    val lastHeartbeat: Long,
    val joinedAt: Long
)

enum class MemberStatus { ACTIVE, OFFLINE, LEFT }
```

**文件 3：`plugins/storage/entity/MessageEntity.kt`**

```kotlin
@Entity(tableName = "messages", foreignKeys = [
    ForeignKey(entity = GroupEntity::class, parentColumns = ["groupId"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("groupId")])
data class MessageEntity(
    @PrimaryKey val msgId: String,
    val groupId: String,
    val senderUuid: String,
    val senderName: String,
    val content: String,     // 加密后的 Base64
    val type: MessageType,
    val timestamp: Long,
    val isDelivered: Boolean
)

enum class MessageType { TEXT, SYSTEM, IMAGE }
```

**文件 4-6：`dao/GroupDao.kt`、`dao/MemberDao.kt`、`dao/MessageDao.kt`**

每个 DAO 须包含：
- `@Insert(onConflict = REPLACE)`
- `@Delete`
- `@Query("SELECT * FROM ... WHERE groupId = :groupId")`
- `@Query("DELETE FROM ... WHERE groupId = :groupId")`（解散时批量删除）
- `@Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")` 返回 `Flow<List<MessageEntity>>`
- `@Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY joinedAt ASC")` 返回 `Flow<List<MemberEntity>>`

**文件 7：`plugins/storage/EphemeralDatabase.kt`**

```kotlin
@Database(entities = [GroupEntity::class, MemberEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class EphemeralDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun messageDao(): MessageDao
}
```

实现要求：
- 使用 `Room.databaseBuilder` + SQLCipher 的 `SupportFactory`
- 数据库密钥由 `CryptoPlugin`（Keystore）生成，通过 `openHelper` 注入
- 数据库实例为单例（在 `StoragePlugin.onInit` 中创建）

**文件 8：`plugins/storage/DatabaseManager.kt`**

```kotlin
/**
 * 数据库管理器——封装 Room + SQLCipher。
 * 提供加密数据库的创建和访问。
 */
class DatabaseManager(private val context: Context, private val passphrase: ByteArray) {
    // createDatabase(): EphemeralDatabase
    //   - Room.databaseBuilder()
    //   - .openHelperFactory(SupportFactory(passphrase))
    //   - .fallbackToDestructiveMigration()
    //   - .build()
    // clearGroupData(groupId: String): 删除该群组的所有消息和成员
    // clearAll(): 删除所有数据（解散时调用）
}
```

实现要求：
- SQLCipher 集成：依赖 `net.zetetic:android-database-sqlcipher:4.5.4` + `androidx.sqlite:sqlite-ktx`
- `SupportFactory` 来自 `net.zetetic.database.sqlcipher.SupportFactory`
- `passphrase` 用完即清零（`Arrays.fill(passphrase, 0)`）

**文件 9：`plugins/storage/StoragePlugin.kt`**

```kotlin
class StoragePlugin : IPlugin {
    override val pluginId = "storage"
    override val dependencies = listOf("crypto")  // 需要加密插件提供数据库密钥

    // onInit: 从 CryptoPlugin 获取数据库密钥 → 创建 DatabaseManager
    // 公开方法：
    // fun getGroupDao(): GroupDao
    // fun getMemberDao(): MemberDao
    // fun getMessageDao(): MessageDao
    // fun clearGroupData(groupId: String)
    // fun clearAll()
}
```

**编译验证**：零报错。

---

#### Step 2.4：加密插件

**文件 1：`plugins/crypto/CryptoUtils.kt`**

```kotlin
/**
 * 加密工具——ECDH 密钥协商 + AES-256-GCM 加解密。
 */
object CryptoUtils {
    // ECDH 密钥对生成（secp256r1 / NIST P-256）
    fun generateKeyPair(): KeyPair

    // ECDH 共享密钥计算
    fun computeSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): SecretKey

    // AES-256-GCM 加密
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray  // 返回 IV + 密文 + tag
    // AES-256-GCM 解密
    fun decrypt(ciphertext: ByteArray, key: SecretKey): ByteArray

    // SHA-256 哈希
    fun sha256(input: String): ByteArray

    // Base64 编解码
    fun base64Encode(data: ByteArray): String
    fun base64Decode(data: String): ByteArray

    // 随机邀请码生成（4位数字）
    fun generateInviteCode(): String  // "0000"-"9999"
}
```

实现要求：
- ECDH 使用 `KeyPairGenerator.getInstance("EC")` + `ECGenParameterSpec("secp256r1")`
- 共享密钥用 `KeyAgreement.getInstance("ECDH")`
- AES-256-GCM：`Cipher.getInstance("AES/GCM/NoPadding")`，IV 12 字节随机生成，拼在密文前
- `sha256` 用 `MessageDigest.getInstance("SHA-256")`
- 邀请码：`Random.nextInt(0, 10000).toString().padStart(4, '0')`
- Base64 用 `android.util.Base64`

**文件 2：`plugins/crypto/KeyManager.kt`**

```kotlin
/**
 * 密钥管理器——管理群组密钥的生命周期。
 * 密钥仅存内存，不持久化，解散时销毁。
 */
class KeyManager {
    // 群组密钥存储：Map<groupId, SecretKey>
    // Android Keystore 数据库密钥
    private val keystore: KeyStore

    // init: 初始化 Keystore ("AndroidKeyStore")
    // getDatabaseKey(): ByteArray
    //   - 从 Keystore 获取或生成 AES-256 密钥
    //   - 返回密钥字节（用于 SQLCipher）
    // setGroupKey(groupId: String, key: SecretKey)
    // getGroupKey(groupId: String): SecretKey?
    // removeGroupKey(groupId: String)
    // clearAllKeys()
}
```

实现要求：
- 数据库密钥：`KeyGenerator.getInstance("AES", "AndroidKeyStore")` + `KeyGenParameterSpec` 设置 `setBlockModes("GCM")` + `setEncryptionPaddings("NoPadding")` + `setKeySize(256)`
- 从 Keystore 取出密钥后需转为 raw bytes 供 SQLCipher 使用
- 群组密钥 Map 须线程安全（`ConcurrentHashMap`）
- `clearAllKeys()` 清除内存中所有群组密钥

**文件 3：`plugins/crypto/CryptoPlugin.kt`**

```kotlin
class CryptoPlugin : IPlugin {
    override val pluginId = "crypto"
    override val dependencies = emptyList<String>()

    private lateinit var keyManager: KeyManager

    // onInit: 初始化 KeyManager
    // 公开方法代理到 CryptoUtils 和 KeyManager：
    // fun getDatabaseKey(): ByteArray
    // fun generateKeyPair(): KeyPair
    // fun computeSharedSecret(priv: PrivateKey, pub: PublicKey): SecretKey
    // fun encrypt(data: ByteArray, key: SecretKey): ByteArray
    // fun decrypt(data: ByteArray, key: SecretKey): ByteArray
    // fun sha256(input: String): ByteArray
    // fun generateInviteCode(): String
    // fun setGroupKey(groupId: String, key: SecretKey)
    // fun getGroupKey(groupId: String): SecretKey?
    // fun removeGroupKey(groupId: String)
}
```

**更新 `EphemeralChatApplication.kt`**：注册顺序：`LocationPlugin` → `CryptoPlugin` → `StoragePlugin` → `BlePlugin`。验证依赖顺序正确。

**编译验证**：零报错。

---

#### Step 2.5：消息协议定义

**文件 1：`protocol/MessageType.kt`**

```kotlin
/**
 * 消息类型枚举——对应设计方案 5.1 节。
 * 每种类型有固定的 JSON 字段集和传输方向。
 */
enum class MessageType(val code: String) {
    JOIN("join"),           // Client→Hub: 加入请求
    JOIN_ACK("join_ack"),   // Hub→Client: 加入确认
    MSG("msg"),             // Client→Hub→All: 文本消息
    SYS("sys"),             // Hub→All: 系统消息
    HB("hb"),               // 双向: 心跳
    LEAVE("leave"),         // Client→Hub: 退出
    NICK("nick"),           // Client→Hub→All: 改名
    SYNC_REQ("sync_req"),   // Client→Hub: 历史同步请求
    SYNC_RSP("sync_rsp"),   // Hub→Client: 历史同步响应
    DISSOLVE("dissolve");   // Hub→All: 解散通知

    companion object {
        fun fromCode(code: String): MessageType? = entries.find { it.code == code }
    }
}
```

**文件 2：`protocol/MessageProtocol.kt`**

```kotlin
/**
 * 消息协议——紧凑 JSON 格式。
 * 字段缩写以节省 BLE 带宽。
 * @see 设计方案 5.2 节
 */
data class ChatMessage(
    val t: String,        // type
    val u: String,        // uuid（截短4位）
    val n: String?,       // name
    val c: String?,      // content（加密后的 Base64）
    val g: String,        // group（截短4位）
    val ts: Long,         // timestamp
    val mid: String?      // message id
) {
    fun toJson(): String          // 用 org.json.JSONObject 序列化
    companion object {
        fun fromJson(json: String): ChatMessage  // 反序列化
    }
}
```

实现要求：
- 使用 `org.json.JSONObject`（Android 内置，不引入 Gson/Moshi 以减少依赖）
- `toJson()` 中 null 字段不输出
- `fromJson()` 须处理缺失字段的默认值
- 典型消息 JSON 约 120-180 字节

**文件 3：`protocol/MessageFragmenter.kt`**

```kotlin
/**
 * 消息分片协议——超过 MTU 的消息自动分片。
 * 分片头 3 bytes: msg_id(2) + seq_flag(1) [1 bit is_last | 7 bits seq 0-127]
 */
class MessageFragmenter(private val mtu: Int = 247) {
    // fragment(data: ByteArray): List<ByteArray>
    //   - 按 MTU - 3(头) 切分
    //   - 每片前加 3 字节头
    //   - msg_id 递增，最后一片 is_last=1
    // reassemble(fragments: List<ByteArray>): ByteArray?
    //   - 按 msg_id 分组，按 seq 排序
    //   - 收到 is_last 后重组
    //   - 超时 10s 丢弃
}
```

实现要求：
- 每片最大 payload = MTU - 3(头) = 244 字节
- `msg_id` 为 `Short` 范围内递增，溢出回绕
- `seq_flag`：最高位 1=最后一片，低 7 位=序号(0-127)
- 重组时须按 seq 排序，缺片则返回 null
- 单条消息最大 128 片 × 244 = 31,232 字节，足够长文本

**编译验证**：零报错。

---

### Phase 3：业务插件实现

**目标**：实现聊天业务、个人信息、生命周期管理三个业务插件，替换 Phase 1 的临时占位插件。

#### Step 3.1：聊天业务插件

**文件 1：`plugins/chat/NicknameGenerator.kt`**

```kotlin
/**
 * 随机用户名生成器。
 * 格式：形容词 + 动物 + 编号（如"飞行的企鹅42"）
 * 50 形容词 × 50 动物 × 90 编号 = 225,000 种组合
 */
object NicknameGenerator {
    private val adjectives = listOf(
        // 50 个中文形容词
    )
    private val animals = listOf(
        // 50 个中文动物名
    )

    fun generate(): String  // "${adjectives.random()}${animals.random()}${Random.nextInt(10, 100)}"
}
```

实现要求：
- 形容词库 50 个：飞行的、沉默的、迷路的、好奇的、慵懒的、快乐的、勇敢的、害羞的、神秘的、温柔的、活泼的、安静的、笨拙的、优雅的、调皮的、严肃的、浪漫的、冷酷的、热情的、忧郁的、机智的、呆萌的、潇洒的、害羞的、固执的、随和的、挑剔的、慷慨的、吝啬的、健谈的、寡言的、乐观的、悲观的、谨慎的、冒失的、沉稳的、浮躁的、勤奋的、慵懒的、聪慧的、憨厚的、狡猾的、天真的、成熟的、稚嫩的、威武的、娇小的、蓬松的、灵动的、笨重的
- 动物库 50 个：企鹅、狐狸、橡树、海鸥、考拉、萤火虫、刺猬、水母、章鱼、蝴蝶、松鼠、海豚、熊猫、老虎、兔子、猫咪、柴犬、海龟、猫头鹰、鲸鱼、蚂蚁、蜜蜂、蜗牛、蝙蝠、浣熊、袋鼠、树懒、鹿、狼、熊、鹰、燕子、麻雀、乌鸦、鹦鹉、金鱼、螃蟹、龙虾、青蛙、壁虎、变色龙、鳄鱼、斑马、长颈鹿、河马、犀牛、企鹅、海象、海豹、北极熊
- 确保每个列表恰好 50 个元素

**文件 2：`plugins/chat/ChatViewModel.kt`**

```kotlin
/**
 * 聊天业务 ViewModel。
 * 职责：
 * 1. 创建群聊流程
 * 2. 加入群聊流程
 * 3. 消息发送与接收
 * 4. 成员列表管理
 * 5. 用户名管理
 * 6. 退出与解散
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val registry: PluginRegistry,
    private val eventBus: EventBus,
) : ViewModel() {

    // UI 状态（StateFlow）
    data class ChatUiState(
        val screen: Screen = Screen.Home,        // Home/Create/Join/Chat/Members
        val inviteCode: String = "",
        val groupId: String = "",
        val nickname: String = "",
        val messages: List<MessageEntity> = emptyList(),
        val members: List<MemberEntity> = emptyList(),
        val isHub: Boolean = false,
        val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
        val errorMessage: String? = null,
    )

    enum class Screen { Home, Create, Join, Chat, Members }
    enum class ConnectionStatus { Disconnected, Scanning, Connecting, Connected, Reconnecting }

    // 创建群聊
    fun createGroup()
    //   1. CryptoPlugin.generateInviteCode() → 4位码
    //   2. LocationPlugin.getCurrentLocation { lat, lon →
    //      a. GeohashUtils.encode(lat, lon, 5) → geohash
    //      b. CryptoPlugin.sha256(code + geohash) → codeHash (4 bytes)
    //      c. 生成 groupId (UUID)
    //      d. BlePlugin.startAdvertising(codeHash, groupIdShort)
    //      e. BlePlugin.startGattServer(onMessageReceived)
    //      f. StoragePlugin 存 GroupEntity
    //      g. 生成随机用户名
    //      h. 更新 UI 状态 → 显示邀请码
    //   }

    // 加入群聊
    fun joinGroup(code: String)
    //   1. LocationPlugin.getCurrentLocation { lat, lon →
    //      a. geohash = GeohashUtils.encode(lat, lon, 5)
    //      b. neighbors = GeohashUtils.neighbors(geohash)
    //      c. codeHashes = neighbors.map { sha256(code + it).take(4) }
    //      d. BlePlugin.startScanning(codeHashes) { deviceAddress, groupIdShort →
    //         - 距离校验: LocationPlugin.getCurrentLocation { lat2, lon2 →
    //           distance = GeohashUtils.distanceMeters(lat, lon, lat2, lon2)
    //           if distance < 100m → BlePlugin.connectAsClient(deviceAddress)
    //         }
    //      }
    //      e. 连接成功后发送 join 消息
    //      f. 收到 join_ack 后更新状态
    //      g. 生成随机用户名（可修改）
    //   }

    // 发送消息
    fun sendMessage(content: String)
    //   1. 构造 ChatMessage(t="msg", u=uuid, n=nickname, c=encrypt(content), g=groupId, ts=now, mid=generateId)
    //   2. 如果是 Hub：广播给所有 Client + 本地存储
    //   3. 如果是 Client：写入 Hub 的 command 特征值
    //   4. UI 立即显示（乐观更新）

    // 修改用户名
    fun changeNickname(newName: String)
    //   构造 ChatMessage(t="nick") 广播

    // 退出群聊
    fun leaveGroup()
    //   1. 发送 leave 消息
    //   2. 清除本地数据
    //   3. 停止 BLE
    //   4. 回到 Home

    // 解散群聊
    fun dissolveGroup()
    //   1. 发送 dissolve 通知
    //   2. 清除本地数据
    //   3. 停止 BLE
    //   4. 回到 Home

    // 接收消息处理
    private fun handleReceivedMessage(data: ByteArray)
    //   1. 解密
    //   2. 解析 JSON → ChatMessage
    //   3. 按 t 分发：
    //      - join: Hub 处理加入请求，回复 join_ack，广播 sys
    //      - join_ack: Client 处理加入确认，保存成员列表和群组密钥
    //      - msg: 解密内容，存 Room，更新 UI
    //      - sys: 存为系统消息
    //      - hb: 更新成员心跳时间
    //      - leave: 移除成员，检查是否触发解散
    //      - nick: 更新成员昵称
    //      - sync_req: Hub 返回历史消息
    //      - sync_rsp: Client 同步历史
    //      - dissolve: 清除本地数据

    // 订阅 EventBus 事件
    init {
        // 订阅 "ble_message" 频道 → handleReceivedMessage
        // 订阅 "location_state" → 定位关闭时 leaveGroup
    }
}
```

实现要求：
- 所有异步操作用 `viewModelScope.launch`
- UI 状态用 `StateFlow<ChatUiState>` 暴露
- 消息收发必须通过 `EventBus` 解耦 BLE 层
- `handleReceivedMessage` 中每个消息类型分支须完整实现，不留 TODO
- 距离校验须用加入时的两组坐标计算，不是用 Geohash 估算

**文件 3-7：`plugins/chat/ui/` 下的 Composable**

**`CreateGroupScreen.kt`**：
- 显示"创建群聊"按钮
- 创建后显示 4 位邀请码（大字体居中，可复制）
- 显示"等待加入..."状态
- 用 `adaptiveSp` 设置所有字号

**`JoinGroupScreen.kt`**：
- 4 位数字输入框（每格一个数字，自动跳格）
- "加入"按钮
- 扫描中状态提示
- 距离过远提示

**`ChatScreen.kt`**：
- 顶部：群聊邀请码 + 成员数
- 消息列表（`LazyColumn`）：自己的消息右对齐，他人左对齐
- 底部：输入框 + 发送按钮
- 消息气泡：圆角矩形，自己=主色背景，他人=表面色
- 系统消息：居中灰色文字
- 所有字号用 `adaptiveSp`，间距用 `adaptiveDp`

**`MemberListScreen.kt`**：
- 成员列表（`LazyColumn`）
- 每行：头像占位（圆形+首字）+ 昵称 + 在线状态图标
- 在线=绿色圆点+人形图标，离线=灰色圆点+人形图标
- 状态指示不依赖颜色（辅以图标形状区分）
- Hub 标识（皇冠图标）

**`ChatComponents.kt`**：
- `MessageBubble(message: MessageEntity, isMine: Boolean)` —— 消息气泡组件
- `MemberRow(member: MemberEntity, isHub: Boolean)` —— 成员行组件
- `ConnectionStatusBar(status: ConnectionStatus)` —— 连接状态条
- `InviteCodeDisplay(code: String)` —— 邀请码展示组件

**文件 8：`plugins/chat/ChatPlugin.kt`**

```kotlin
class ChatPlugin : IPlugin {
    override val pluginId = "chat"
    override val dependencies = listOf("ble", "storage", "crypto", "lifecycle")

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        // 注册 NavDestination.Chat，content 指向 ChatScreen
        registry.registerNavDestination(NavDestination.Chat)
    }
}
```

实现要求：
- `NavDestination.Chat` 的 `content` 须通过 Hilt 获取 `ChatViewModel`，渲染 `ChatScreen`
- 因 NavDestination 是 sealed class 的 data object，`content` 须在 `ChatPlugin.onInit` 中通过 `registry.registerNavDestination()` 注册时一并绑定
- 可改为：`NavDestination` 不持有 `content`，而是 `PluginRegistry` 维护 `Map<String, @Composable () -> Unit>`，`ChatPlugin` 注册时传入 content lambda

**编译验证**：零报错。

---

#### Step 3.2：个人信息插件

**文件 1：`plugins/profile/ProfileViewModel.kt`**

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {
    // 简单状态：当前用户名、App 版本、主题切换
    data class ProfileUiState(
        val currentNickname: String = "",
        val isDarkTheme: Boolean = true,
        val appVersion: String = "1.0.0",
    )
}
```

实现要求：
- Phase 1 阶段个人信息页较简单，Phase 3 可扩展
- 主题切换用 DataStore 持久化（Phase 3 实现）

**文件 2：`plugins/profile/ui/ProfileScreen.kt`**

- 显示当前用户名（如果在群聊中）
- 主题切换开关（深色/浅色）
- App 版本号
- 关于说明（"EphemeralChat - 临时群聊，在场即在场，离场即消散"）
- 所有字号用 `adaptiveSp`

**文件 3：`plugins/profile/ProfilePlugin.kt`**

```kotlin
class ProfilePlugin : IPlugin {
    override val pluginId = "profile"
    override val dependencies = emptyList()

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        registry.registerNavDestination(NavDestination.Profile)
    }
}
```

**编译验证**：零报错。

---

#### Step 3.3：生命周期管理插件

**文件 1：`plugins/lifecycle/HeartbeatManager.kt`**

```kotlin
/**
 * 心跳管理器。
 * 每 15 秒发送心跳，45 秒未收到判定离线，90 秒移除。
 */
class HeartbeatManager(
    private val eventBus: EventBus,
    private val coroutineScope: CoroutineScope,
) {
    private val heartbeatInterval = 15_000L
    private val offlineThreshold = 45_000L
    private val removeThreshold = 90_000L

    // 成员心跳记录：Map<uuid, Long(上次心跳时间)>
    private val memberHeartbeats = ConcurrentHashMap<String, Long>()

    fun start(isHub: Boolean)
    //   - 如果是 Hub：定时向所有 Client 发心跳通知
    //   - 定时检查所有成员心跳，超时发布 HeartbeatTimeout 事件
    //   - 如果是 Client：定时向 Hub 发心跳指令

    fun onHeartbeatReceived(uuid: String)
    //   - 更新 memberHeartbeats[uuid] = System.currentTimeMillis()

    fun stop()
    //   - 取消所有协程任务
    //   - 清空 memberHeartbeats
}
```

实现要求：
- 使用 `coroutineScope.launch` + `delay(heartbeatInterval)` 实现定时循环
- 检查超时：遍历 `memberHeartbeats`，`now - lastHeartbeat > offlineThreshold` → 发布 `MemberOffline`，`> removeThreshold` → 发布 `MemberLeft`
- Hub 心跳通过 `BlePlugin.broadcastToClients()` 发送
- Client 心跳通过 `BlePlugin.writeToHub()` 发送

**文件 2：`plugins/lifecycle/MemberStateMachine.kt`**

```kotlin
/**
 * 成员状态机——管理单个成员的状态转换。
 * Disconnected → Joining → Active → Offline → Left
 */
class MemberStateMachine(private val eventBus: EventBus) {
    fun transition(current: MemberStatus, event: MemberEvent): MemberStatus
    //   - Disconnected + GattConnected → Joining
    //   - Joining + JoinAckReceived → Active
    //   - Active + HeartbeatTimeout45s → Offline
    //   - Offline + HeartbeatReceived → Active
    //   - Offline + Timeout90s → Left
    //   - Active + ManualLeave → Left
    //   - Any + LocationDisabled → Left
}
```

**文件 3：`plugins/lifecycle/HubElection.kt`**

```kotlin
/**
 * Hub 重选协议。
 * 当 Hub 离开时，剩余成员通过随机退避+扫描确认选举新 Hub。
 */
class HubElection(
    private val registry: PluginRegistry,
    private val eventBus: EventBus,
    private val coroutineScope: CoroutineScope,
) {
    fun startElection(myUuid: String, groupId: String, groupIdShort: ByteArray)
    //   1. 计算 backoff = hash(UUID) % 3000ms
    //   2. 启动定时器，退避期间扫描 BLE 广播
    //   3. 收到同群组新 Hub 广播 → 连接该 Hub
    //   4. 退避结束未发现 → 自己成为新 Hub
    //   5. 新 Hub 启动 GATT Server + 广播（携带相同 groupId）
    //   6. 其他 Client 发现并连接
    //   7. 新 Hub 广播 sync（消息历史）
    //   8. 发布 HubChanged 事件

    fun cancel()
    //   - 取消选举定时器
    //   - 停止扫描
}
```

实现要求：
- `hash(UUID) % 3000` 用 `UUID.toString().hashCode().absoluteValue % 3000`
- 退避期间用 `BleScanner` 扫描同 `groupIdShort` 的广播
- 成为新 Hub 后：`GattServer.start()` + `BleAdvertiser.start()`（携带相同 groupIdShort）
- 新 Hub 需从本地 Room 读取历史消息，构造 `sync_rsp` 发给新连接的 Client

**文件 4：`plugins/lifecycle/LifecyclePlugin.kt`**

```kotlin
class LifecyclePlugin : IPlugin {
    override val pluginId = "lifecycle"
    override val dependencies = listOf("ble", "storage")

    private lateinit var heartbeatManager: HeartbeatManager
    private lateinit var hubElection: HubElection

    // onInit:
    //   - 订阅 "ble_message" 中的 hb 类型消息 → HeartbeatManager.onHeartbeatReceived
    //   - 订阅 "member" 频道的 MemberLeft 事件 → 检查是否全员离开 → 触发解散
    //   - 订阅 "location_state" → 定位关闭触发离群
    //   - 订阅 GattDisconnected → 如果是 Hub 断开 → 启动 HubElection

    // onStart: 准备资源
    // onStop: 停止心跳和选举
    // onDestroy: 清理

    // 公开方法供 ChatPlugin 调用：
    // fun startHeartbeat(isHub: Boolean)
    // fun stopHeartbeat()
    // fun startElection(myUuid: String, groupId: String, groupIdShort: ByteArray)
    // fun cancelElection()
}
```

**更新 `EphemeralChatApplication.kt`**：完整注册顺序：
`LocationPlugin` → `CryptoPlugin` → `StoragePlugin` → `BlePlugin` → `LifecyclePlugin` → `ChatPlugin` → `ProfilePlugin`

移除 Phase 1 的临时占位插件。

**编译验证**：零报错。启动 App 须显示底部导航，群聊页有创建/加入入口。

---

### Phase 4：健壮性增强

**目标**：实现心跳、Hub 重选、自动解散、消息加密、Foreground Service、消息分片。

#### Step 4.1：Foreground Service

**文件：`service/EphemeralForegroundService.kt`**

```kotlin
/**
 * 前台服务——保持 BLE 活跃，防止后台被杀。
 * 显示通知栏："EphemeralChat 正在运行"
 */
@AndroidEntryPoint
class EphemeralForegroundService : Service() {
    // onCreate:
    //   - 创建通知渠道（NotificationChannel，IMPORTANCE_LOW）
    //   - 创建前台通知
    //   - startForeground(NOTIFICATION_ID, notification)
    // onStartCommand:
    //   - 返回 START_STICKY
    // onDestroy:
    //   - 停止所有插件
    //   - stopForeground(true)
}
```

实现要求：
- 通知渠道 ID = "ephemeral_chat_service"，名称 = "EphemeralChat 服务"
- 通知内容："EphemeralChat 正在运行"，无振动、无声音
- `startForeground` 须在 `onCreate` 中调用（Android 12+ 要求 5 秒内调用）
- `AndroidManifest.xml` 中声明该 Service + `FOREGROUND_SERVICE_CONNECTED_DEVICE` 权限

**更新 `MainActivity.kt`**：进入群聊时 `startForegroundService(Intent(...))`，退出群聊时 `stopService(Intent(...))`

#### Step 4.2：完整心跳与自动解散

在 `LifecyclePlugin` 中实现完整的心跳和自动解散逻辑：

- 心跳定时器每 15s 执行
- 45s 超时 → 发布 `MemberOffline`，UI 显示离线状态
- 90s 超时 → 发布 `MemberLeft`，从成员列表移除
- 成员列表清空 → 触发解散流程
- 全员定位关闭（全员心跳停止）→ 触发解散
- 解散流程：`dissolve` 通知 → 本地清除 → 停止 BLE → 停止 Service → 回到首页

#### Step 4.3：消息加密完整实现

在 `ChatViewModel.handleReceivedMessage()` 中：
- `join` 时交换 ECDH 公钥，计算共享密钥
- `join_ack` 中携带 Hub 的 ECDH 公钥
- 后续所有 `msg` 类型消息用 AES-256-GCM 加解密
- 加密后的密文 Base64 编码放入 JSON 的 `c` 字段

#### Step 4.4：消息分片完整实现

在 `BlePlugin.writeToHub()` 和 `BlePlugin.broadcastToClients()` 中：
- 发送前检查数据长度，超过 MTU-3 则分片
- 接收方在 `onMessageReceived` 中先尝试重组
- 使用 `MessageFragmenter` 类处理分片/重组

#### Step 4.5：Hub 重选完整实现

在 `HubElection` 中：
- 完整实现退避+扫描+自选 Hub 流程
- 新 Hub 上线后广播 `sync_rsp` 同步历史消息
- 其他 Client 收到新 Hub 广播后连接并发送 `sync_req`
- UI 显示"正在重新连接..."状态

**编译验证**：零报错。验收标准：Hub 退出后自动重选、定位关闭后自动离群、全员离开后自动解散。

---

### Phase 5：体验打磨

#### Step 5.1：消息排序与时间戳

- 消息按 `timestamp` 升序排列
- 每条消息显示相对时间（"刚刚"、"3分钟前"、"14:30"）
- 日期分割线（"今天"、"昨天"、"9月22日"）

#### Step 5.2：成员在线状态可视化

- 在线：绿色圆点 + 人形图标
- 离线：灰色圆点 + 人形图标（虚线）
- 离开：不显示
- Hub：皇冠图标
- 状态变化时有动画过渡（`AnimatedVisibility`）

#### Step 5.3：系统消息

- "XX 加入了群聊" → 居中灰色文字
- "XX 离开了群聊" → 居中灰色文字
- "XX 已改名为 YY" → 居中灰色文字
- "群聊已解散" → 居中红色文字
- "正在重新连接..." → 居中橙色文字+加载动画

#### Step 5.4：连接状态提示

- 扫描中：顶部进度条
- 连接中：Snackbar "正在连接..."
- 重新连接：全屏半透明遮罩 + "正在重新连接..."
- 已连接：无提示（正常状态）

#### Step 5.5：动画与交互细节

- 消息发送：从底部滑入动画
- 成员加入/离开：列表项动画
- Tab 切换：淡入淡出
- 按钮点击：涟漪效果（Material 3 自带）
- 下拉刷新消息列表（SwipeRefresh）

#### Step 5.6：深色主题完善

- 定义完整的深色色板（所有 Material 3 颜色角色）
- 深色为默认
- 浅色主题完整对照
- 在个人信息页可切换

#### Step 5.7：色盲友好设计

- 在线状态：绿色圆点 + "在线"文字标签，离线灰色圆点 + "离线"文字标签
- 不仅靠颜色区分，辅以图标形状（实心圆=在线，虚线圆=离线）
- 消息气泡：自己消息有"我"标签，他人消息有昵称标签

#### Step 5.8：strings.xml 完善

所有用户可见字符串须定义在 `strings.xml` 中，不硬编码：
- app_name、create_group、join_group、invite_code、waiting_join、scan_group、distance_too_far
- send、leave_group、dissolve_group、member_list、online、offline、hub
- connecting、reconnecting、group_dissolved、joined_group、left_group、renamed_to
- profile、about、dark_theme、light_theme、version

**编译验证**：零报错。

---

## 五、编码规范（全局适用）

### 5.1 Kotlin 编码规范

1. 所有 `public` 类/函数须有 KDoc 注释
2. `private` 函数可只加单行注释
3. 使用 `data class` 不可变数据类，配合 `copy()` 更新
4. 使用 `enum class` 而非 `Int` 常量表示状态
5. 使用 `sealed class` 表示有限状态集
6. `Flow` 优先于 `LiveData`
7. `StateFlow` 暴露 UI 状态
8. `SharedFlow` 用于一次性事件（如错误提示、导航指令）
9. 所有协程须绑定 `viewModelScope` 或 `lifecycleScope`
10. 禁止 `GlobalScope`

### 5.2 Compose 编码规范

1. Composable 函数须有 `@Composable` 注解
2. 状态提升：Composable 须无状态，状态由 ViewModel 管理
3. 预览：每个 Screen 须有 `@Preview` 函数（深色和浅色各一个）
4. 所有字号用 `adaptiveSp()`，尺寸用 `adaptiveDp()`
5. 不硬编码颜色，使用 `MaterialTheme.colorScheme`
6. 不硬编码字号，使用 `MaterialTheme.typography` 或 `adaptiveSp()`
7. 列表用 `LazyColumn` / `LazyRow`，不用 `Column` + `forEach`（大数据时）
8. 使用 `Spacer` 控制间距，不用空 `Box`
9. 使用 `Modifier` 链式调用，保持可读性

### 5.3 BLE 编码规范

1. 所有 BLE 操作须在 `Dispatchers.IO` 执行
2. `BluetoothGattCallback` 中的回调须切到主线程更新 UI（通过 EventBus）
3. 连接/断开须打印日志（设备地址+状态）
4. GATT 资源须在 `onStop`/`onDestroy` 中释放（`close()`）
5. 广播/扫描须有超时保护
6. 须检查 `BluetoothAdapter` 是否为 null（设备不支持蓝牙）
7. 须检查 `isBluetoothEnabled`（用户未开蓝牙时提示）

### 5.4 错误处理规范

1. 所有 `try-catch` 须捕获具体异常，不捕获 `Throwable`
2. catch 块须有处理逻辑（日志 + 用户提示 + 恢复/降级），不静默吞
3. BLE/IO 操作失败时发布错误事件，UI 显示 Snackbar
4. 权限被拒时显示解释弹窗，引导到系统设置
5. 蓝牙未开启时显示提示，引导到蓝牙设置

---

## 六、验收与测试规则

### 6.1 编译验收（每个 Step 必须通过）

每个 Step 完成后执行：

```bash
./gradlew assembleDebug --no-daemon 2>&1
```

- 零 ERROR
- 零 WARNING（除非是依赖库的警告）
- 输出 `BUILD SUCCESSFUL`

如有错误，必须修复后再进入下一步。不允许带着编译错误前进。

### 6.2 Phase 验收标准

| Phase | 验收标准 |
|-------|---------|
| Phase 0 | 空项目编译通过，App 可启动显示空白界面 |
| Phase 1 | App 启动显示底部导航（群聊+我的），Tab 可切换，不同屏幕尺寸字体比例协调 |
| Phase 2 | 编译通过，各插件可独立实例化，无运行时崩溃 |
| Phase 3 | 两台手机：创建+加入+文本聊天+退出+数据清除，全流程零报错 |
| Phase 4 | Hub 退出自动重选、定位关闭自动离群、全员离开自动解散，全流程零报错 |
| Phase 5 | 深色/浅色切换正常，色盲友好标识清晰，动画流畅 |

### 6.3 全局检查清单（每个 Phase 结束后核对）

- [ ] 所有文件 KDoc 注释完整
- [ ] 无 `TODO()`、`FIXME`、空实现
- [ ] 无 `!!` 强解（除非有注释说明）
- [ ] 无 `GlobalScope`
- [ ] 所有 IO 操作有 try-catch
- [ ] 所有 BLE 操作在 IO 线程
- [ ] 所有 UI 字号用 `adaptiveSp()`
- [ ] 所有 UI 尺寸用 `adaptiveDp()`
- [ ] 无硬编码颜色（用 MaterialTheme.colorScheme）
- [ ] strings.xml 中定义所有用户可见文字
- [ ] `./gradlew assembleDebug` 零报错

---

## 七、执行指令模板

当开始执行此提示词时，按以下格式逐步输出：

```
正在执行 Phase X, Step X.X: [步骤名]

创建文件：path/to/File.kt
[代码内容]

编译验证中...
✅ 编译通过 / ❌ 编译失败（错误列表 + 修复）

→ 进入下一步
```

**绝对禁止的行为**：
1. 一次性生成所有代码不验证
2. 跳过编译验证步骤
3. 留 TODO 或占位符
4. 假设编译通过而不实际验证
5. 在同一 Step 中创建过多文件导致无法验证
6. 引入设计方案未提及的第三方库
7. 修改已通过的 Step 的代码（除非后续 Step 显式要求修改）

**遇到问题时**：
1. 编译失败 → 分析错误 → 修复 → 重新编译 → 通过后再继续
2. API 不确定 → 查阅 Android 官方文档 → 选择最简方案
3. 设计方案有歧义 → 选择最简方案 + 注释说明决策
4. 依赖冲突 → 对齐版本号 → 锁定到兼容版本

---

## 八、依赖版本参考清单

以下为推荐版本，实际使用时须确认最新稳定版：

```
// Compose BOM
implementation(platform("androidx.compose:compose-bom:2024.06.00"))

// Compose
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.activity:activity-compose:1.9.0")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// Hilt
implementation("com.google.dagger:hilt-android:2.51.1")
ksp("com.google.dagger:hilt-compiler:2.51.1")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// SQLCipher
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.sqlite:sqlite-ktx:2.4.0")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.1.1")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

// JSON（内置 org.json，无需额外依赖）
```

**KSP 版本须与 Kotlin 版本匹配**：Kotlin 2.0.x → KSP 2.0.x-1.0.x

---

## 九、总结

此提示词文件定义了 EphemeralChat 的完整开发路径：

- **5 个 Phase**，**20+ 个 Step**，**50+ 个文件**
- 每个 Step 编译通过后再前进
- 零占位符，零 TODO，所有代码完整可运行
- 插件化架构贯穿始终，新功能以插件接入
- UI 自适应 + 底部导航 + 深色主题 + 色盲友好

执行者须严格按此文件逐步推进，最终交付一个可编译、可运行的完整 Android 工程。