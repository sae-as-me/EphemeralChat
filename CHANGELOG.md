---
AIGC:
  ContentProducer: '001191110102MAD55U9H0F10002'
  ContentPropagator: '001191110102MAD55U9H0F10002'
  Label: '1'
  ProduceID: '4c01dc8f-6332-4e7a-b40f-8986ec768eea'
  PropagateID: '4c01dc8f-6332-4e7a-b40f-8986ec768eea'
  ReservedCode1: '191a09db-f232-4412-95f5-9413d397f2aa'
  ReservedCode2: '191a09db-f232-4412-95f5-9413d397f2aa'
---

# Changelog

本项目所有重要变更记录于此文件。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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