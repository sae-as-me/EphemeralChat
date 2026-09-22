---
AIGC:
  ContentProducer: '001191110102MAD55U9H0F10002'
  ContentPropagator: '001191110102MAD55U9H0F10002'
  Label: '1'
  ProduceID: 'd79d4640-9c95-4042-8719-1b30b68cb660'
  PropagateID: 'd79d4640-9c95-4042-8719-1b30b68cb660'
  ReservedCode1: 'decde430-d4bd-438f-b086-c236b7d1880c'
  ReservedCode2: 'decde430-d4bd-438f-b086-c236b7d1880c'
---

# Changelog

本项目所有重要变更记录于此文件。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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