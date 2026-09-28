package com.ephemeral.chat.plugins.game.numberbomb

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
import org.json.JSONObject
import kotlin.random.Random

/**
 * 数字炸弹游戏插件。
 *
 * 游戏流程：
 * 1. 任意成员发起游戏，选择数字范围
 * 2. Hub 自动在范围内随机生成炸弹数字（仅存 Hub 内存，全保密）
 * 3. 按成员顺序轮流猜测，每次猜测后收窄范围
 * 4. 猜中炸弹者出局，剩余最后一人获胜
 *
 * 通信协议（GAME 消息，gid = "numberBomb"）：
 * - start:    发起人→Hub，{"rangeMin":1,"rangeMax":100,"initiator":"uuid"}
 * - round:    Hub→All，{"rangeMin":1,"rangeMax":100,"turn":"uuid","order":["uuid1",...]}
 * - guess:    玩家→Hub，{"num":42}
 * - result:   Hub→All，{"num":42,"player":"uuid","result":"narrow"|"explode","rangeMin":42,"rangeMax":100}
 * - end:      Hub→All，{"winner":"uuid","loser":"uuid"}
 */
class NumberBombPlugin : IPlugin {
    override val pluginId = "numberBomb"
    override val dependencies = emptyList<String>()

    private val TAG = "NumberBombPlugin"
    private lateinit var eventBus: EventBus
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 游戏 ID（用于 GAME 消息的 gid 字段） */
    companion object {
        const val GAME_ID = "numberBomb"
    }

    // ---- UI 状态 ----

    data class BombUiState(
        val phase: GamePhase = GamePhase.IDLE,
        val rangeMin: Int = 0,
        val rangeMax: Int = 100,
        val turnUuid: String = "",
        val turnName: String = "",
        val order: List<Pair<String, String>> = emptyList(),
        val activePlayers: List<String> = emptyList(),
        val lastGuess: Int? = null,
        val lastGuessPlayer: String = "",
        val lastResult: String = "",
        val winner: String = "",
        val loser: String = "",
        val isHub: Boolean = false,
        val myUuid: String = "",
        val errorMessage: String? = null,
    )

    enum class GamePhase { IDLE, WAITING_START, PLAYING, ENDED }

    private val _uiState = MutableStateFlow(BombUiState())
    val uiState: StateFlow<BombUiState> = _uiState.asStateFlow()

    // ---- Hub 侧游戏状态（仅 Hub 维护） ----

    private var bombNumber: Int = 0
    private var currentRangeMin: Int = 0
    private var currentRangeMax: Int = 100
    private var turnOrder: MutableList<String> = mutableListOf()
    private var currentIndex: Int = 0
    private var activePlayers: MutableList<String> = mutableListOf()
    private var isRunning: Boolean = false
    private var myUuidShort: String = ""
    private var isHub: Boolean = false

    /** 游戏消息发送接口（由外部注入，参数为 act + data，gid 已由外部封装） */
    var sendGameBroadcast: ((String, String) -> Boolean)? = null
    var sendGameToHub: ((String, String) -> Boolean)? = null
    var getMemberUuids: (() -> List<String>)? = null
    var getMemberName: ((String) -> String)? = null

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        this.eventBus = eventBus
        Log.i(TAG, "NumberBomb plugin initialized")

        // 订阅游戏消息
        scope.launch {
            eventBus.subscribe("game_$GAME_ID").collect { event ->
                if (event is AppEvent.GameMessage) {
                    handleGameMessage(event)
                }
            }
        }
    }

    /**
     * 注入运行时上下文（在群聊建立后由 ChatViewModel 调用）。
     */
    fun bindContext(
        isHub: Boolean,
        myUuidShort: String,
        sendBroadcast: (String, String) -> Boolean,
        sendToHub: (String, String) -> Boolean,
        membersProvider: () -> List<Pair<String, String>>, // (uuid, nickname) 实时查询
    ) {
        this.isHub = isHub
        this.myUuidShort = myUuidShort
        this.sendGameBroadcast = sendBroadcast
        this.sendGameToHub = sendToHub
        this.getMemberUuids = { membersProvider().map { it.first } }
        this.getMemberName = { uuid -> membersProvider().firstOrNull { it.first == uuid }?.second ?: "玩家" }
        _uiState.value = _uiState.value.copy(isHub = isHub, myUuid = myUuidShort)
    }

    // ---- 发起游戏 ----

    /** 游戏规则说明（Hub 发起游戏后发到群聊） */
    val ruleText: String
        get() = "【数字炸弹】在 0-N 范围内藏有一个炸弹数字，轮流猜测（不能猜边界），每次猜测后范围收窄，猜中炸弹者出局，游戏结束。"

    /**
     * 发起游戏（任意成员可调用）。
     * 单人时本地直接开始；多人时先邀请（Hub 广播 invite），全员同意后由 Hub 广播 round。
     */
    fun startGame(rangeMin: Int, rangeMax: Int) {
        if (rangeMin >= rangeMax - 9) {
            _uiState.value = _uiState.value.copy(errorMessage = "范围太小，最小值与最大值至少差 10")
            return
        }

        val members = getMemberUuids?.invoke() ?: return

        if (isHub) {
            if (members.size <= 1) {
                // 单人模式：本地直接开始（Bug 1 修复）
                _uiState.value = _uiState.value.copy(phase = GamePhase.WAITING_START, errorMessage = null)
                beginSoloRound(rangeMin, rangeMax)
            } else {
                // 多人模式：广播邀请，等待全员同意（Bug 5 修复）
                _uiState.value = _uiState.value.copy(phase = GamePhase.WAITING_START, errorMessage = null)
                pendingRangeMin = rangeMin
                pendingRangeMax = rangeMax
                sendSystemMessage?.invoke(ruleText + "（范围 $rangeMin-$rangeMax）")
                sendInvite?.invoke(GAME_ID, "数字炸弹")
            }
        } else {
            // Client 发送 start 给 Hub
            _uiState.value = _uiState.value.copy(phase = GamePhase.WAITING_START, errorMessage = null)
            val data = JSONObject().apply {
                put("rangeMin", rangeMin)
                put("rangeMax", rangeMax)
                put("initiator", myUuidShort)
            }.toString()
            sendGameToHub?.invoke("start", data)
        }
    }

    /** 邀请相关的回调（由外部注入） */
    var sendInvite: ((String, String) -> Unit)? = null
    var sendSystemMessage: ((String) -> Unit)? = null

    /** 待开始的范围（邀请期间暂存） */
    private var pendingRangeMin: Int = 0
    private var pendingRangeMax: Int = 100

    /** 单人模式：本地生成炸弹自己猜 */
    private fun beginSoloRound(rangeMin: Int, rangeMax: Int) {
        bombNumber = Random.nextInt(rangeMin + 1, rangeMax)
        currentRangeMin = rangeMin
        currentRangeMax = rangeMax
        turnOrder = mutableListOf(myUuidShort)
        activePlayers = mutableListOf(myUuidShort)
        currentIndex = 0
        isRunning = true
        isSolo = true
        Log.i(TAG, "单人模式开始: 炸弹=$bombNumber, 范围=[$rangeMin-$rangeMax]")
        broadcastRound()
    }

    /** 是否单人模式 */
    private var isSolo = false

    /**
     * Hub 侧：全员同意后开始一局游戏（由 ChatViewModel 邀请完成时回调）。
     */
    fun onAllAccepted() {
        if (!isHub || isRunning) return
        val members = getMemberUuids?.invoke() ?: return
        if (members.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "没有成员")
            return
        }
        beginRoundInternal(pendingRangeMin, pendingRangeMax, members)
    }

    /**
     * Hub 侧：开始一局游戏（内部）。
     */
    private fun beginRoundInternal(rangeMin: Int, rangeMax: Int, members: List<String>) {
        bombNumber = Random.nextInt(rangeMin + 1, rangeMax)
        currentRangeMin = rangeMin
        currentRangeMax = rangeMax
        turnOrder = members.toMutableList()
        activePlayers = members.toMutableList()
        currentIndex = 0
        isRunning = true
        isSolo = false

        Log.i(TAG, "游戏开始: 炸弹=$bombNumber, 范围=[$rangeMin-$rangeMax], 玩家=${members.size}人")
        broadcastRound()
    }

    /**
     * 广播当前回合状态给全体。
     */
    private fun broadcastRound() {
        if (!isRunning) return
        val currentUuid = turnOrder.getOrElse(currentIndex) { "" }
        val currentName = getMemberName?.invoke(currentUuid) ?: "玩家"
        val orderNames = turnOrder.map { it to (getMemberName?.invoke(it) ?: "玩家") }

        _uiState.value = _uiState.value.copy(
            phase = GamePhase.PLAYING,
            rangeMin = currentRangeMin,
            rangeMax = currentRangeMax,
            turnUuid = currentUuid,
            turnName = currentName,
            order = orderNames,
            activePlayers = activePlayers.toList(),
        )

        val data = JSONObject().apply {
            put("rangeMin", currentRangeMin)
            put("rangeMax", currentRangeMax)
            put("turn", currentUuid)
            val orderArr = org.json.JSONArray()
            turnOrder.forEach { orderArr.put(it) }
            put("order", orderArr)
        }.toString()
        sendGameBroadcast?.invoke("round", data)
    }

    // ---- 玩家猜测 ----

    /**
     * 当前轮到的玩家提交猜测。
     */
    fun submitGuess(number: Int) {
        if (_uiState.value.phase != GamePhase.PLAYING) return
        if (_uiState.value.turnUuid != myUuidShort) {
            _uiState.value = _uiState.value.copy(errorMessage = "还没轮到你")
            return
        }
        // 修复：Client 侧 currentRangeMin/currentRangeMax 是 Hub 专属变量且不被更新，
        // 应使用 UI 状态中的 rangeMin/rangeMax（由 round/result 消息同步）
        val rMin = _uiState.value.rangeMin
        val rMax = _uiState.value.rangeMax
        if (number <= rMin || number >= rMax) {
            _uiState.value = _uiState.value.copy(errorMessage = "请输入 ${rMin + 1} - ${rMax - 1} 之间的数字")
            return
        }

        if (isHub) {
            handleGuess(number, myUuidShort)
        } else {
            val data = JSONObject().apply { put("num", number) }.toString()
            sendGameToHub?.invoke("guess", data)
        }
    }

    /**
     * Hub 侧：处理玩家猜测。
     */
    private fun handleGuess(number: Int, playerUuid: String) {
        if (!isHub || !isRunning) return

        // 修复：只处理当前轮到玩家的猜测，防止过期/乱序猜测干扰游戏
        val expectedUuid = turnOrder.getOrElse(currentIndex) { "" }
        if (playerUuid != expectedUuid) {
            Log.w(TAG, "忽略非当前玩家的猜测: $playerUuid, expected=$expectedUuid")
            return
        }

        val playerName = getMemberName?.invoke(playerUuid) ?: "玩家"

        if (number == bombNumber) {
            // 炸弹爆炸
            val loser = playerUuid
            activePlayers.remove(playerUuid)
            val winner = activePlayers.firstOrNull() ?: ""

            val data = JSONObject().apply {
                put("num", number)
                put("player", playerUuid)
                put("result", "explode")
                put("rangeMin", currentRangeMin)
                put("rangeMax", currentRangeMax)
            }.toString()
            sendGameBroadcast?.invoke("result", data)

            // 广播结束
            val endData = JSONObject().apply {
                put("winner", winner)
                put("loser", loser)
            }.toString()
            sendGameBroadcast?.invoke("end", endData)

            isRunning = false
            _uiState.value = _uiState.value.copy(
                phase = GamePhase.ENDED,
                lastGuess = number,
                lastGuessPlayer = playerUuid,
                lastResult = "explode",
                winner = winner,
                loser = loser,
            )
            Log.i(TAG, "游戏结束: 炸弹=$bombNumber, 出局=$playerName, 胜者=${getMemberName?.invoke(winner)}")
        } else {
            // 缩窄范围：猜的数本身成为新的边界
            // 例：范围 [0,100]，炸弹 63，猜 30 → 新范围 [30,100]，可输入 31-99
            if (number < bombNumber) {
                currentRangeMin = number
            } else {
                currentRangeMax = number
            }

            // 广播猜测结果
            val data = JSONObject().apply {
                put("num", number)
                put("player", playerUuid)
                put("result", "narrow")
                put("rangeMin", currentRangeMin)
                put("rangeMax", currentRangeMax)
            }.toString()
            sendGameBroadcast?.invoke("result", data)

            // 下一轮
            advanceTurn()
            broadcastRound()
        }
    }

    /**
     * 推进到下一个玩家。
     */
    private fun advanceTurn() {
        if (activePlayers.isEmpty()) return
        currentIndex = (currentIndex + 1) % turnOrder.size
        // 跳过已出局的玩家
        var attempts = 0
        while (turnOrder[currentIndex] !in activePlayers && activePlayers.isNotEmpty() && attempts < turnOrder.size) {
            currentIndex = (currentIndex + 1) % turnOrder.size
            attempts++
        }
    }

    // ---- 接收游戏消息 ----

    private fun handleGameMessage(event: AppEvent.GameMessage) {
        when (event.action) {
            "start" -> {
                // Hub 收到发起请求（Client 发起）
                if (isHub) {
                    val data = JSONObject(event.data)
                    val rangeMin = data.optInt("rangeMin", 1)
                    val rangeMax = data.optInt("rangeMax", 100)
                    val members = getMemberUuids?.invoke() ?: return
                    if (members.size <= 1) {
                        beginRoundInternal(rangeMin, rangeMax, members)
                    } else {
                        // 多人：广播邀请+规则
                        pendingRangeMin = rangeMin
                        pendingRangeMax = rangeMax
                        sendSystemMessage?.invoke(ruleText + "（范围 $rangeMin-$rangeMax）")
                        sendInvite?.invoke(GAME_ID, "数字炸弹")
                    }
                }
            }
            "round" -> {
                // Client 收到回合状态
                if (!isHub) {
                    val data = JSONObject(event.data)
                    val rangeMin = data.optInt("rangeMin", 1)
                    val rangeMax = data.optInt("rangeMax", 100)
                    val turn = data.optString("turn", "")
                    val orderArr = data.optJSONArray("order")
                    val orderList = mutableListOf<Pair<String, String>>()
                    if (orderArr != null) {
                        for (i in 0 until orderArr.length()) {
                            val uuid = orderArr.getString(i)
                            orderList.add(uuid to (getMemberName?.invoke(uuid) ?: "玩家"))
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        phase = GamePhase.PLAYING,
                        rangeMin = rangeMin,
                        rangeMax = rangeMax,
                        turnUuid = turn,
                        turnName = getMemberName?.invoke(turn) ?: "玩家",
                        order = orderList,
                        activePlayers = orderList.map { it.first },
                    )
                }
            }
            "guess" -> {
                // Hub 收到玩家猜测
                if (isHub && isRunning) {
                    val data = JSONObject(event.data)
                    val num = data.optInt("num", -1)
                    if (num >= 0) {
                        handleGuess(num, event.senderUuid)
                    }
                }
            }
            "result" -> {
                // Client 收到猜测结果
                if (!isHub) {
                    val data = JSONObject(event.data)
                    val num = data.optInt("num", 0)
                    val player = data.optString("player", "")
                    val result = data.optString("result", "")
                    val rMin = data.optInt("rangeMin", _uiState.value.rangeMin)
                    val rMax = data.optInt("rangeMax", _uiState.value.rangeMax)

                    _uiState.value = _uiState.value.copy(
                        rangeMin = rMin,
                        rangeMax = rMax,
                        lastGuess = num,
                        lastGuessPlayer = player,
                        lastResult = result,
                    )
                }
            }
            "end" -> {
                // Client 收到游戏结束
                if (!isHub) {
                    val data = JSONObject(event.data)
                    _uiState.value = _uiState.value.copy(
                        phase = GamePhase.ENDED,
                        winner = data.optString("winner", ""),
                        loser = data.optString("loser", ""),
                    )
                }
                Log.i(TAG, "Game ended")
            }
        }
    }

    // ---- 退出/重置 ----

    /**
     * 重置游戏状态。
     */
    fun resetGame() {
        isRunning = false
        bombNumber = 0
        currentRangeMin = 0
        currentRangeMax = 100
        turnOrder.clear()
        activePlayers.clear()
        currentIndex = 0
        _uiState.value = BombUiState(isHub = isHub, myUuid = myUuidShort)
    }

    override fun onStart() {}
    override fun onStop() {}
    override fun onDestroy() {
        scope.cancel()
    }
}
