package com.ephemeral.chat.plugins.game.whospy

import android.util.Log
import com.ephemeral.chat.core.eventbus.AppEvent
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * 谁是卧底游戏插件。
 *
 * 游戏流程：
 * 1. 任意成员发起游戏，选择是否启用白板
 * 2. Hub 随机选词对 + 分配角色，定向发牌给每个玩家
 * 3. 所有玩家只看到自己的词语，不知道自己的角色
 * 4. 每轮：Hub 指定起始发言者+方向 -> 玩家描述 -> 投票 -> 淘汰
 * 5. 胜负判定后游戏结束，公布所有人身份和词语
 *
 * 通信协议（GAME 消息，gid = "whoIsSpy"）：
 * - start:              发起人->Hub {"enableWhiteBlank":false}
 * - deal:               Hub->各玩家(定向) {"word":"泡面"}
 * - round_start:        Hub->All {"round":1,"firstSpeaker":"uuid","direction":"cw"}
 * - describe:           玩家->Hub {"text":"描述"}
 * - describe_broadcast: Hub->All {"player":"uuid","text":"描述"}
 * - vote_start:         Hub->All {"round":1}
 * - vote:               玩家->Hub {"target":"uuid"}
 * - vote_result:        Hub->All {"votes":{...},"eliminated":"uuid"}
 * - end:                Hub->All {"winner":"civilian","reveals":[...]}
 */
class SpyPlugin : IPlugin {
    override val pluginId = "whoIsSpy"
    override val dependencies = emptyList<String>()

    private val TAG = "SpyPlugin"
    private lateinit var eventBus: EventBus
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val GAME_ID = "whoIsSpy"
    }

    // ---- UI 状态 ----

    data class SpyUiState(
        val phase: GamePhase = GamePhase.IDLE,
        val myWord: String = "",
        val round: Int = 0,
        val firstSpeaker: String = "",
        val direction: String = "cw",
        val descriptions: List<Pair<String, String>> = emptyList(),
        val isVoting: Boolean = false,
        val myVote: String = "",
        val votedCount: Int = 0,
        val totalVoters: Int = 0,
        val activePlayers: List<Pair<String, String>> = emptyList(),
        val lastEliminated: String = "",
        val lastEliminatedName: String = "",
        val winner: String = "",
        val reveals: List<Triple<String, String, String>> = emptyList(),
        val errorMessage: String? = null,
        val isHub: Boolean = false,
        val myUuid: String = "",
        val hasWhiteBlank: Boolean = false,
        val enableWhiteBlank: Boolean = false,
    )

    enum class GamePhase {
        IDLE, WAITING_DEAL, DESCRIBE, VOTING, VOTE_RESULT, GAME_END
    }

    private val _uiState = MutableStateFlow(SpyUiState())
    val uiState: StateFlow<SpyUiState> = _uiState.asStateFlow()

    // ---- Hub 侧游戏状态（仅 Hub 内存，UI 不可见） ----

    private var civilianWord: String = ""
    private var spyWord: String = ""
    private var assignments: MutableMap<String, PlayerRole> = mutableMapOf()
    private var round: Int = 0
    private var activePlayers: MutableList<String> = mutableListOf()
    private var descriptions: MutableMap<String, String> = mutableMapOf()
    private var votes: MutableMap<String, String> = mutableMapOf()
    private var isRunning: Boolean = false
    private var myUuidShort: String = ""
    private var isHub: Boolean = false
    private var enableWhiteBlank: Boolean = false
    private var hasWhiteBlank: Boolean = false

    /** 游戏消息发送接口（由外部注入） */
    var sendGameBroadcast: ((String, String) -> Boolean)? = null
    var sendGamePrivate: ((String, String, String) -> Boolean)? = null
    var sendGameToHub: ((String, String) -> Boolean)? = null
    var getMemberUuids: (() -> List<String>)? = null
    var getMemberName: ((String) -> String)? = null

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        this.eventBus = eventBus
        Log.i(TAG, "SpyPlugin initialized")

        scope.launch {
            eventBus.subscribe("game_$GAME_ID").collect { event ->
                if (event is AppEvent.GameMessage) {
                    handleGameMessage(event)
                }
            }
        }
    }

    /** 所有玩家信息查询（实时）——供结束页显示名字 */
    var allPlayersProvider: (() -> List<Pair<String, String>>)? = null

    fun bindContext(
        isHub: Boolean,
        myUuidShort: String,
        sendBroadcast: (String, String) -> Boolean,
        sendToHub: (String, String) -> Boolean,
        sendPrivate: (String, String, String) -> Boolean,
        membersProvider: () -> List<Pair<String, String>>, // (uuid, nickname) 实时查询
    ) {
        this.isHub = isHub
        this.myUuidShort = myUuidShort
        this.sendGameBroadcast = sendBroadcast
        this.sendGameToHub = sendToHub
        this.sendGamePrivate = sendPrivate
        this.getMemberUuids = { membersProvider().map { it.first } }
        this.getMemberName = { uuid -> membersProvider().firstOrNull { it.first == uuid }?.second ?: "玩家" }
        allPlayersProvider = membersProvider
        _uiState.value = _uiState.value.copy(isHub = isHub, myUuid = myUuidShort)
    }

    // ---- 发起游戏 ----

    fun startGame(enableWhiteBlank: Boolean = false) {
        _uiState.value = _uiState.value.copy(phase = GamePhase.WAITING_DEAL, errorMessage = null, enableWhiteBlank = enableWhiteBlank)

        if (isHub) {
            beginGame(enableWhiteBlank)
        } else {
            val data = JSONObject().apply {
                put("enableWhiteBlank", enableWhiteBlank)
            }.toString()
            sendGameToHub?.invoke("start", data)
        }
    }

    private fun beginGame(enableWB: Boolean) {
        if (!isHub || isRunning) return

        val members = getMemberUuids?.invoke() ?: return
        if (members.size < 4) {
            _uiState.value = _uiState.value.copy(phase = GamePhase.IDLE, errorMessage = "至少需要 4 人才能开始游戏")
            return
        }

        // 选词对
        val wordPair = WordPairs.pairs[Random.nextInt(WordPairs.pairs.size)]
        val swap = Random.nextBoolean()
        civilianWord = if (swap) wordPair.spyWord else wordPair.civilianWord
        spyWord = if (swap) wordPair.civilianWord else wordPair.spyWord

        // 分配角色
        assignments.clear()
        val playerList = members.shuffled()
        val spyCount = when {
            members.size <= 6 -> 1
            members.size <= 9 -> 2
            else -> 3
        }

        var hasWB = false
        if (enableWB && members.size >= 8) {
            hasWB = true
            assignments[playerList[0]] = PlayerRole.WHITE_BLANK
            for (i in 1..spyCount) {
                assignments[playerList[i]] = PlayerRole.SPY
            }
            for (i in (spyCount + 1) until members.size) {
                assignments[playerList[i]] = PlayerRole.CIVILIAN
            }
        } else {
            for (i in 0 until spyCount) {
                assignments[playerList[i]] = PlayerRole.SPY
            }
            for (i in spyCount until members.size) {
                assignments[playerList[i]] = PlayerRole.CIVILIAN
            }
        }

        hasWhiteBlank = hasWB
        activePlayers = members.toMutableList()
        round = 0
        isRunning = true

        Log.i(TAG, "游戏开始: ${members.size}人, 卧底${spyCount}人, 白板=${hasWB}")

        // 定向发牌
        for (uuid in members) {
            val word = when (assignments[uuid]) {
                PlayerRole.SPY -> spyWord
                PlayerRole.CIVILIAN -> civilianWord
                PlayerRole.WHITE_BLANK -> ""
                null -> civilianWord
            }
            val data = JSONObject().apply { put("word", word) }.toString()
            sendGamePrivate?.invoke("deal", uuid, data)
        }

        // Hub 给自己也发词（本地直接设置）
        val myWord = when (assignments[myUuidShort]) {
            PlayerRole.SPY -> spyWord
            PlayerRole.CIVILIAN -> civilianWord
            PlayerRole.WHITE_BLANK -> ""
            null -> civilianWord
        }
        _uiState.value = _uiState.value.copy(myWord = myWord, hasWhiteBlank = hasWB)

        startNewRound()
    }

    // ---- 回合管理 ----

    private fun startNewRound() {
        if (!isHub || !isRunning) return
        round++
        descriptions.clear()

        val firstSpeaker = activePlayers[Random.nextInt(activePlayers.size)]
        val direction = if (Random.nextBoolean()) "cw" else "ccw"

        _uiState.value = _uiState.value.copy(
            phase = GamePhase.DESCRIBE,
            round = round,
            firstSpeaker = firstSpeaker,
            direction = direction,
            descriptions = emptyList(),
            isVoting = false,
            myVote = "",
            votedCount = 0,
            totalVoters = activePlayers.size,
            activePlayers = activePlayers.map { it to (getMemberName?.invoke(it) ?: "玩家") },
        )

        val data = JSONObject().apply {
            put("round", round)
            put("firstSpeaker", firstSpeaker)
            put("direction", direction)
        }.toString()
        sendGameBroadcast?.invoke("round_start", data)
    }

    // ---- 玩家描述 ----

    fun submitDescription(text: String) {
        if (_uiState.value.phase != GamePhase.DESCRIBE) return
        if (text.isBlank()) return
        if (myUuidShort !in activePlayers) return

        if (isHub) {
            handleDescription(myUuidShort, text)
        } else {
            val data = JSONObject().apply { put("text", text) }.toString()
            sendGameToHub?.invoke("describe", data)
        }
    }

    private fun handleDescription(playerUuid: String, text: String) {
        if (!isHub || !isRunning) return
        if (playerUuid !in activePlayers) return
        if (descriptions.containsKey(playerUuid)) return

        descriptions[playerUuid] = text

        // 广播给全体
        val data = JSONObject().apply {
            put("player", playerUuid)
            put("text", text)
        }.toString()
        sendGameBroadcast?.invoke("describe_broadcast", data)

        // 更新本地 UI
        val descList = descriptions.map { it.key to it.value }
        _uiState.value = _uiState.value.copy(descriptions = descList)

        // 所有人描述完毕 -> 进入投票
        if (descriptions.size >= activePlayers.size) {
            startVoting()
        }
    }

    // ---- 投票 ----

    private fun startVoting() {
        if (!isHub || !isRunning) return
        votes.clear()

        _uiState.value = _uiState.value.copy(
            phase = GamePhase.VOTING,
            isVoting = true,
            myVote = "",
            votedCount = 0,
            totalVoters = activePlayers.size,
        )

        val data = JSONObject().apply { put("round", round) }.toString()
        sendGameBroadcast?.invoke("vote_start", data)
    }

    fun submitVote(target: String) {
        if (_uiState.value.phase != GamePhase.VOTING) return
        if (myUuidShort !in activePlayers) return
        // 允许投自己或弃权

        if (isHub) {
            handleVote(myUuidShort, target)
        } else {
            val data = JSONObject().apply { put("target", target) }.toString()
            sendGameToHub?.invoke("vote", data)
        }
        _uiState.value = _uiState.value.copy(myVote = target)
    }

    private fun handleVote(voterUuid: String, target: String) {
        if (!isHub || !isRunning) return
        if (voterUuid !in activePlayers) return
        if (votes.containsKey(voterUuid)) return

        votes[voterUuid] = target

        val newVotedCount = votes.size
        _uiState.value = _uiState.value.copy(votedCount = newVotedCount)

        // 所有人投票完毕 -> 统计
        if (votes.size >= activePlayers.size) {
            tallyVotes()
        }
    }

    // ---- 统计投票 ----

    private fun tallyVotes() {
        if (!isHub || !isRunning) return

        // 统计票数
        val voteCount = mutableMapOf<String, Int>()
        for (target in votes.values) {
            if (target.isNotEmpty()) {
                voteCount[target] = (voteCount[target] ?: 0) + 1
            }
        }

        // 找最高票
        val maxVotes = voteCount.values.maxOrNull() ?: 0
        val topCandidates = voteCount.filter { it.value == maxVotes }.keys.toList()

        val eliminated: String
        val votesJson = JSONObject()
        for ((voter, target) in votes) {
            votesJson.put(voter, target)
        }

        if (topCandidates.size == 1) {
            // 唯一最高票 -> 淘汰
            eliminated = topCandidates[0]
            activePlayers.remove(eliminated)
        } else {
            // 平票 -> 不淘汰
            eliminated = ""
        }

        // 广播结果
        val data = JSONObject().apply {
            put("votes", votesJson)
            put("eliminated", eliminated)
        }.toString()
        sendGameBroadcast?.invoke("vote_result", data)

        if (eliminated.isNotEmpty()) {
            val eliminatedName = getMemberName?.invoke(eliminated) ?: "玩家"

            _uiState.value = _uiState.value.copy(
                phase = GamePhase.VOTE_RESULT,
                lastEliminated = eliminated,
                lastEliminatedName = eliminatedName,
            )

            // 检查胜负
            val result = checkWinCondition()
            if (result != null) {
                // 游戏结束
                endGame(result)
            } else {
                // 继续下一轮（延迟一下让玩家看到结果）
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    if (isRunning) startNewRound()
                }
            }
        } else {
            // 平票不淘汰，直接下一轮
            _uiState.value = _uiState.value.copy(
                phase = GamePhase.VOTE_RESULT,
                lastEliminated = "",
                lastEliminatedName = "",
            )
            scope.launch {
                kotlinx.coroutines.delay(2000)
                if (isRunning) startNewRound()
            }
        }
    }

    // ---- 胜负判定 ----

    private fun checkWinCondition(): String? {
        val activeAssignments = activePlayers.mapNotNull { assignments[it] }
        val activeSpies = activeAssignments.count { it == PlayerRole.SPY }
        val activeNonSpies = activeAssignments.count { it != PlayerRole.SPY }

        if (activeSpies == 0) {
            // 卧底全出局
            // 检查白板是否存活
            val whiteBlankAlive = activePlayers.any { assignments[it] == PlayerRole.WHITE_BLANK }
            return if (whiteBlankAlive) "white_blank_pending" else "civilian"
        }

        if (activeSpies >= activeNonSpies) {
            return "spy"
        }

        return null
    }

    private fun endGame(winner: String) {
        isRunning = false

        val revealsArr = JSONArray()
        for (uuid in assignments.keys) {
            val role = assignments[uuid] ?: PlayerRole.CIVILIAN
            val word = when (role) {
                PlayerRole.SPY -> spyWord
                PlayerRole.CIVILIAN -> civilianWord
                PlayerRole.WHITE_BLANK -> "(白板)"
            }
            revealsArr.put(JSONObject().apply {
                put("player", uuid)
                put("name", allPlayersProvider?.invoke()?.firstOrNull { it.first == uuid }?.second ?: "玩家")
                put("word", word)
                put("role", role.name.lowercase())
            })
        }

        val winnerStr = when (winner) {
            "civilian" -> "civilian"
            "spy" -> "spy"
            "white_blank_pending" -> "white_blank"
            else -> winner
        }

        val data = JSONObject().apply {
            put("winner", winnerStr)
            put("reveals", revealsArr)
        }.toString()
        sendGameBroadcast?.invoke("end", data)

        // 更新本地 UI
        val revealsList = assignments.map { (uuid, role) ->
            val word = when (role) {
                PlayerRole.SPY -> spyWord
                PlayerRole.CIVILIAN -> civilianWord
                PlayerRole.WHITE_BLANK -> "(白板)"
            }
            // 修复：包含玩家名，避免结束时 activePlayers 已移除淘汰者导致找不到名字
            val name = allPlayersProvider?.invoke()?.firstOrNull { it.first == uuid }?.second ?: "玩家"
            Triple("$uuid|$name", word, role.name.lowercase())
        }
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.GAME_END,
            winner = winnerStr,
            reveals = revealsList,
        )
        Log.i(TAG, "游戏结束: 胜者=$winnerStr")
    }

    /** Hub 手动宣布白板胜利 */
    fun announceWhiteBlankWin() {
        if (!isHub || !isRunning) return
        val activeBlank = activePlayers.any { assignments[it] == PlayerRole.WHITE_BLANK }
        val allSpiesOut = activePlayers.none { assignments[it] == PlayerRole.SPY }
        if (activeBlank && allSpiesOut) {
            endGame("white_blank")
        }
    }

    // ---- 接收消息 ----

    private fun handleGameMessage(event: AppEvent.GameMessage) {
        when (event.action) {
            "start" -> {
                if (isHub) {
                    val data = JSONObject(event.data)
                    val enableWB = data.optBoolean("enableWhiteBlank", false)
                    beginGame(enableWB)
                }
            }
            "deal" -> {
                // Client 收到自己的词语
                if (!isHub) {
                    val data = JSONObject(event.data)
                    val word = data.optString("word", "")
                    _uiState.value = _uiState.value.copy(myWord = word)
                }
            }
            "round_start" -> {
                if (!isHub) {
                    val data = JSONObject(event.data)
                    val r = data.optInt("round", 1)
                    val firstSpeaker = data.optString("firstSpeaker", "")
                    val dir = data.optString("direction", "cw")
                    _uiState.value = _uiState.value.copy(
                        phase = GamePhase.DESCRIBE,
                        round = r,
                        firstSpeaker = firstSpeaker,
                        direction = dir,
                        descriptions = emptyList(),
                        isVoting = false,
                        myVote = "",
                    )
                }
            }
            "describe_broadcast" -> {
                val data = JSONObject(event.data)
                val player = data.optString("player", "")
                val text = data.optString("text", "")
                val currentDescs = _uiState.value.descriptions.toMutableList()
                if (currentDescs.none { it.first == player }) {
                    currentDescs.add(player to text)
                    _uiState.value = _uiState.value.copy(descriptions = currentDescs)
                }
            }
            "vote_start" -> {
                if (!isHub) {
                    _uiState.value = _uiState.value.copy(
                        phase = GamePhase.VOTING,
                        isVoting = true,
                        myVote = "",
                        votedCount = 0,
                        totalVoters = _uiState.value.activePlayers.size,
                    )
                }
            }
            "vote" -> {
                if (isHub && isRunning) {
                    val data = JSONObject(event.data)
                    val target = data.optString("target", "")
                    handleVote(event.senderUuid, target)
                }
            }
            "vote_result" -> {
                if (!isHub) {
                    val data = JSONObject(event.data)
                    val eliminated = data.optString("eliminated", "")
                    val eliminatedName = if (eliminated.isNotEmpty()) getMemberName?.invoke(eliminated) ?: "玩家" else ""

                    // 先更新存活玩家列表（淘汰者移除），再更新 UI 状态
                    val newActive = _uiState.value.activePlayers.filter { it.first != eliminated }

                    _uiState.value = _uiState.value.copy(
                        phase = GamePhase.VOTE_RESULT,
                        lastEliminated = eliminated,
                        lastEliminatedName = eliminatedName,
                        activePlayers = newActive,
                    )
                }
            }
            "end" -> {
                if (!isHub) {
                    val data = JSONObject(event.data)
                    val winner = data.optString("winner", "")
                    val revealsArr = data.optJSONArray("reveals")
                    val revealsList = mutableListOf<Triple<String, String, String>>()
                    if (revealsArr != null) {
                        for (i in 0 until revealsArr.length()) {
                            val item = revealsArr.getJSONObject(i)
                            val pUuid = item.optString("player", "")
                            val pName = item.optString("name", "")
                            if (pName.isEmpty()) {
                                revealsList.add(Triple(
                                    "$pUuid|${getMemberName?.invoke(pUuid) ?: "玩家"}",
                                    item.optString("word", ""),
                                    item.optString("role", ""),
                                ))
                            } else {
                                revealsList.add(Triple(
                                    "$pUuid|$pName",
                                    item.optString("word", ""),
                                    item.optString("role", ""),
                                ))
                            }
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        phase = GamePhase.GAME_END,
                        winner = winner,
                        reveals = revealsList,
                    )
                }
                Log.i(TAG, "Game ended: winner=${event.data}")
            }
            "describe" -> {
                if (isHub && isRunning) {
                    val data = JSONObject(event.data)
                    val text = data.optString("text", "")
                    if (text.isNotEmpty()) {
                        handleDescription(event.senderUuid, text)
                    }
                }
            }
        }
    }

    // ---- 重置 ----

    fun resetGame() {
        isRunning = false
        civilianWord = ""
        spyWord = ""
        assignments.clear()
        activePlayers.clear()
        descriptions.clear()
        votes.clear()
        round = 0
        hasWhiteBlank = false
        _uiState.value = SpyUiState(isHub = isHub, myUuid = myUuidShort)
    }

    override fun onStart() {}
    override fun onStop() {}
    override fun onDestroy() {
        scope.cancel()
    }
}

enum class PlayerRole { CIVILIAN, SPY, WHITE_BLANK }
