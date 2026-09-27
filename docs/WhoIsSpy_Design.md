# 谁是卧底 — 游戏设计方案

## 一、游戏概述

谁是卧底是一款发言推理类聚会游戏。多数玩家拿到相同词语（平民词），少数玩家拿到相近词语（卧底词）。每轮所有人依次描述自己的词语，然后投票淘汰一人。平民目标是淘汰所有卧底，卧底目标是存活到人数持平。

**App 定位**：仅用作发牌、投票收集和结果宣布。描述、讨论等互动环节由玩家线下自行进行，App 不参与。

## 二、人数与角色

| 配置 | 说明 |
|---|---|
| 总人数 | 4-12 人 |
| 卧底数 | 4-6 人时 1 名卧底；7-9 人时 2 名卧底；10-12 人时 3 名卧底 |
| 白板（可选） | 8+ 人时可设 1 名白板——看不到任何词语，胜利条件为卧底全出局且自己存活 |
| 发起人 | 任意成员发起，发起人参与游戏（与所有人一样拿词、描述、投票） |

**所有人不可见自身角色**——每个玩家只看到自己的词语，不知道自己是平民、卧底还是白板。被淘汰后身份不公布。游戏结束后公布所有人的身份和词语。

**Hub 持有人也参与游戏**——Hub 设备持有人在游戏中的体验与普通玩家完全一致：只看到自己的词语，不知道其他人的身份和词语。Hub 仅在后台执行发牌、收票、判定等系统逻辑，持有人看不到任何额外信息。

## 三、词语库

App 内置 100 对相近词语，每条包含平民词 + 卧底词，如：

- 泡面 / 方便面
- 草莓 / 蓝莓
- 汉堡 / 三明治
- 咖啡 / 可可
- ...（共 100 对）

Hub 随机选一对，随机决定哪个词为平民词、哪个为卧底词。

## 四、通信协议

gid = `"whoIsSpy"`

| 动作 (act) | 方向 | data 字段 | 说明 |
|---|---|---|---|
| `start` | 发起人→Hub | `{"enableWhiteBlank":false}` | 发起游戏 |
| `deal` | Hub→各玩家（定向） | `{"word":"泡面"}` | 私密发牌：每人收到自己的词语。白板收到 `"word":""` |
| `round_start` | Hub→All | `{"round":1,"firstSpeaker":"uuid","direction":"cw"}` | 新轮开始，随机指定起始发言者和方向（cw=顺时针, ccw=逆时针）。不广播完整发言顺序，各玩家根据存活列表和方向自行判断下一个发言者 |
| `describe` | 玩家→Hub | `{"text":"热水一泡就能吃"}` | 玩家提交描述 |
| `describe_broadcast` | Hub→All | `{"player":"uuid","text":"热水一泡就能吃"}` | Hub 广播描述给全体 |
| `vote_start` | Hub→All | `{"round":1}` | 所有人描述完毕，进入投票 |
| `vote` | 玩家→Hub | `{"target":"uuid"}` | 玩家投票（弃权 `target=""`） |
| `vote_result` | Hub→All | `{"votes":{"uuid1":"uuid2",...},"eliminated":"uuid"}` | 所有人投票完毕，广播结果和被淘汰者。**不公布被淘汰者身份** |
| `eliminated_notice` | Hub→All | `{"player":"uuid","message":"<用户名> 淘汰，游戏继续"}` | 淘汰通知（不公布身份） |
| `end` | Hub→All | `{"winner":"civilian"\|"spy"\|"white_blank","reveals":[{"player":"uuid","word":"泡面","role":"civilian"},...]}` | 游戏结束，公布所有人的词语和身份 |

## 五、游戏流程

```
发起游戏
  |
  v
Hub 随机选词对 + 分配角色（仅存 Hub 内存，UI 不可见）
  |
  +-- 定向发牌给每个玩家（sendGamePrivate）
  |     每人只收到自己的词语，不知道自己的角色
  |     白板收到空白提示
  |     Hub 持有人也只收到自己的词语
  |
  v
第 1 轮描述阶段
  |  Hub 随机指定起始发言者 + 方向（顺时针/逆时针）
  |  玩家根据方向依次发言（线下自行推进，App 不强制顺序）
  |  App 仅收集描述文本并广播给全体
  |  所有人描述完毕 -> 进入投票
  |
  v
第 1 轮投票阶段
  |  全体存活玩家投票（无倒计时，所有人投完即截止）
  |  弃权 = target 为空
  |  Hub 统计：票数最高者淘汰
  |  平票 -> 不淘汰，直接进入下一轮
  |
  v
淘汰通知
  |  广播 "<用户名> 淘汰，游戏继续"（不公布身份和词语）
  |  判断胜负条件：
  |    - 卧底全出局 -> 平民胜，游戏结束
  |    - 卧底数 >= 存活非卧底数 -> 卧底胜，游戏结束
  |    - 否则 -> 下一轮
  |
  v
第 2 轮（重复上述流程）
  |
  v
游戏结束
  |  广播 "游戏结束，<身份>胜利"
  |  公布所有人的词语和身份
  |  若存在白板角色，显示"宣布白板胜利"手动按钮
```

## 六、Hub 侧状态

```kotlin
data class SpyGameState(
    val isRunning: Boolean,
    val civilianWord: String,          // 平民词（仅 Hub 内存，UI 不可见）
    val spyWord: String,               // 卧底词（仅 Hub 内存，UI 不可见）
    val assignments: Map<String, PlayerRole>,  // uuid -> 角色（仅 Hub 内存，UI 不可见）
    val round: Int,                    // 当前轮次
    val firstSpeaker: String,          // 本轮起始发言者
    val direction: String,             // "cw" 或 "ccw"
    val descriptions: Map<String, String>,     // 本轮已提交的描述
    val activePlayers: List<String>,   // 存活玩家
    val votes: Map<String, String>,    // 投票者 -> 被投者
    val isVoting: Boolean,
    val enableWhiteBlank: Boolean,
)

enum class PlayerRole { CIVILIAN, SPY, WHITE_BLANK }
```

## 七、UI 状态与页面

### UI 状态

```kotlin
data class SpyUiState(
    val phase: GamePhase,
    val myWord: String,                // 我的词语（白板为空字符串）
    val round: Int,                    // 当前轮次
    val firstSpeaker: String,          // 本轮起始发言者 uuid
    val direction: String,             // "cw" 或 "ccw"
    val descriptions: List<Pair<String, String>>,  // 本轮描述列表 (玩家uuid, 描述)
    val isVoting: Boolean,
    val myVote: String,                // 我投的人（空字符串=未投/弃权）
    val activePlayers: List<Pair<String, String>>,  // 存活玩家 (uuid, 昵称)
    val lastEliminated: String,        // 上轮被淘汰者 uuid
    val lastEliminatedName: String,    // 上轮被淘汰者昵称
    val winner: String,                // "civilian" | "spy" | "white_blank"
    val reveals: List<Triple<String, String, String>>,  // 游戏结束后的公开列表 (uuid, 词语, 角色)
    val errorMessage: String?,
    val isHub: Boolean,
    val myUuid: String,
    val enableWhiteBlank: Boolean,
    val hasWhiteBlank: Boolean,        // 本局是否有白板角色
)

enum class GamePhase { 
    IDLE,               // 发起页
    WAITING_DEAL,       // 等待发牌
    DESCRIBE,           // 描述阶段
    VOTING,             // 投票阶段
    VOTE_RESULT,        // 投票结果（淘汰通知）
    GAME_END,           // 游戏结束
}
```

### 页面结构

```
按 phase 切换：
|
+-- IDLE -> 发起页
|   +-- 游戏规则说明
|   +-- 白板开关（8+ 人时可选）
|   +-- 开始游戏按钮
|
+-- WAITING_DEAL -> 等待发牌页
|   +-- "等待分配词语..."
|
+-- DESCRIBE -> 描述页
|   +-- 顶部：你的词语（大字显示）
|   +-- 当前轮次 + 起始发言者 + 方向（顺时针/逆时针）
|   +-- 已提交的描述列表（玩家名 + 描述文本）
|   +-- 底部输入框（随时可输入，不限顺序——App 不强制发言顺序）
|   +-- 所有人描述完毕后自动进入投票
|
+-- VOTING -> 投票页
|   +-- 存活玩家列表（点击投票）
|   +-- 弃权按钮
|   +-- 已投票人数 / 总存活人数
|   +-- 所有人投票完毕后自动跳转
|
+-- VOTE_RESULT -> 淘汰通知页
|   +-- "<用户名> 淘汰，游戏继续"
|   +-- 不公布身份和词语
|   +-- 自动进入下一轮或游戏结束
|
+-- GAME_END -> 结束页
    +-- "游戏结束，<身份>胜利"
    +-- 所有人的词语和身份公开列表
    +-- 若存在白板角色：显示"宣布白板胜利"按钮（手动宣布）
    +-- 再来一局按钮
```

## 八、胜负判定

| 条件 | 判定 |
|---|---|
| 卧底全部被淘汰 | 平民胜 |
| 存活卧底数 >= 存活非卧底数（含白板） | 卧底胜 |
| 白板存活且卧底全出局 | 白板胜（需 Hub 手动点击"宣布白板胜利"按钮确认） |
| 白板被淘汰 | 不影响游戏继续 |
| 只剩 2 人 | 按上述规则判定（若其中 1 个是卧底且另一个非卧底，卧底数=1>=非卧底数=1 -> 卧底胜） |

## 九、边界规则

- **不可见角色**：所有人在游戏过程中只看到自己的词语，不知道自己的角色。被淘汰后身份不公布。
- **Hub 持有人视角**：Hub 持有人与普通玩家体验完全一致，只看到自己的词语。Hub 系统逻辑（发牌、收票、判定）在后台运行，UI 不暴露任何额外信息。
- **发言顺序**：App 不强制发言顺序，仅指定起始发言者和方向供玩家线下参考。描述输入框对所有存活玩家始终开放。
- **描述限制**：不能直接说出自己的词语本身，但不做文本过滤（线下自行约束）
- **投票无倒计时**：所有人投票完毕即截止，自动进入下一阶段
- **平票处理**：平票则不淘汰，直接进入下一轮
- **退群处理**：玩家退群时从存活列表移除，若导致胜负条件达成则游戏结束
- **身份公布时机**：仅在游戏结束后统一公布所有人的词语和角色

## 十、与现有架构的对接

| 对接点 | 方式 |
|---|---|
| 插件注册 | `EphemeralChatApplication` 中 `pluginRegistry.register(SpyPlugin())`，注册顺序在 Emoji 之后 |
| 消息路由 | ChatViewModel 的 GAME 分支已按 gid 转发到 `game_whoIsSpy` 频道，插件订阅该频道 |
| 定向发牌 | 使用 `sendGamePrivate(gid, act, targetUuid, data)`——已就绪 |
| 广播 | 使用 `sendGameBroadcast(gid, act, data)`——已就绪 |
| Client->Hub | 使用 `sendGameToHub(gid, act, data)`——已就绪 |
| 成员列表 | 使用 `getMemberUuids()` 获取当前群成员——已就绪 |
| 游戏入口 | ChatScreen 顶栏游戏按钮 -> GameListScreen 中新增"谁是卧底"卡片 |
| bindContext | 进入游戏页时调用，注入 isHub/myUuidShort/sendBroadcast/sendToHub/members |

## 十一、文件结构

```
plugins/game/whospy/
+-- SpyPlugin.kt          // 游戏插件（状态管理 + 消息处理）
+-- WordPairs.kt          // 内置 100 对词库数据
+-- ui/
    +-- SpyScreen.kt      // UI 页面（按 phase 切换）
```

## 十二、信任方案

- Hub 在后台执行发牌逻辑，技术上是经过所有人词语和角色信息的
- Hub UI 与普通玩家完全一致：只显示自己的词语，不显示其他人的
- 日志不打印具体分配结果（`Log.i` 中只打印"已发牌给 N 人"，不打印谁拿了什么词）
- Hub 持有人在游戏中的信息获取与普通玩家完全相同
- 线下聚会信任成本极低，此方案足够

## 十三、不做的事（边界）

- 不做文本过滤（描述中是否说出词语本身由玩家线下约束）
- 不做 AI 生成词对（仅用内置词库）
- 不做历史记录持久化（游戏结束后状态清空）
- 不做观众模式（仅群聊成员可参与）
- 不做自定义词对上传（后续可扩展）
- 不做投票倒计时（所有人投完即截止）
- 不做被淘汰者身份即时公布（仅游戏结束后统一公布）
- 不做发言顺序强制（仅指定起始发言者和方向，App 不限制输入顺序）
