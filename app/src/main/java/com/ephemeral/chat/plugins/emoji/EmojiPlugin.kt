package com.ephemeral.chat.plugins.emoji

import android.util.Log
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import kotlin.random.Random

/**
 * 表情插件——提供常用 Emoji 和游戏表情（剪刀石头布、骰子）。
 * 以独立插件形式接入，不侵入 ChatPlugin 核心逻辑。
 * 通过 EventBus 或直接调用与 Chat 交互。
 */
class EmojiPlugin : IPlugin {
    override val pluginId = "emoji"
    override val dependencies = emptyList<String>()

    private val TAG = "EmojiPlugin"

    /**
     * 常用 Emoji 分类。
     * 每个 Tab 对应一组表情，用户可切换浏览。
     */
    data class EmojiCategory(
        val name: String,
        val emojis: List<String>,
    )

    val categories: List<EmojiCategory> = listOf(
        EmojiCategory(
            "常用",
            listOf(
                "😀", "😄", "😁", "😊", "😍", "🥰", "😎", "🤔",
                "😂", "🤣", "😅", "😭", "😢", "😡", "🥺", "😱",
                "👍", "👎", "👏", "🙏", "💪", "🤝", "✌️", "🤙",
                "❤️", "💔", "🔥", "✨", "🎉", "🎁", "💯", "💤",
            ),
        ),
        EmojiCategory(
            "动物",
            listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
                "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
                "🐧", "🐦", "🦆", "🦉", "🐺", "🐗", "🐴", "🦄",
                "🐝", "🐛", "🦋", "🐌", "🐞", "🐢", "🐍", "🦎",
            ),
        ),
        EmojiCategory(
            "食物",
            listOf(
                "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍑",
                "🥝", "🍍", "🥭", "🍒", "🫐", "🍅", "🥑", "🍆",
                "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥙",
                "🍜", "🍝", "🍣", "🍱", "🍛", "🍤", "🥟", "🍰",
            ),
        ),
        EmojiCategory(
            "出行",
            listOf(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
                "🚒", "🚐", "🚚", "🚛", "🏍️", "🛵", "🚲", "🛴",
                "✈️", "🛫", "🛬", "🚂", "🚄", "🚅", "🚆", "🚇",
                "🚢", "⛵", "🚤", "🗺️", "🧭", "🏔️", "🏖️", "🏝️",
            ),
        ),
    )

    /**
     * 游戏表情：剪刀石头布。
     * 随机返回一个结果 emoji。
     */
    fun randomRockPaperScissors(): String {
        val options = listOf("✊", "✌️", "🖐️")
        val result = options[Random.nextInt(3)]
        Log.d(TAG, "RPS: $result")
        return result
    }

    /**
     * 游戏表情：骰子。
     * 返回 "🎲N" 格式（emoji + 点数），UI 显示时解析为彩色骰子+大号数字。
     */
    fun randomDice(): Pair<String, Int> {
        val point = Random.nextInt(1, 7)
        Log.d(TAG, "Dice: $point")
        return "🎲" to point
    }

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        Log.i(TAG, "Emoji plugin initialized")
    }

    override fun onStart() {}
    override fun onStop() {}
    override fun onDestroy() {}
}
