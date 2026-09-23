package com.ephemeral.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局共享状态管理器。
 * 持有跨 ViewModel / 跨页面共享的应用级状态。
 * 主题状态由 MainActivity 读取以驱动 EphemeralChatTheme；
 * 昵称状态由 ChatViewModel 和 ProfileViewModel 共同读写。
 */
object SharedStateManager {

    /** 深色主题开关，默认 true（深色优先） */
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    /** 当前用户昵称，空字符串表示尚未设置 */
    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    /**
     * 切换深色/浅色主题。
     */
    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    /**
     * 设置主题。
     */
    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
    }

    /**
     * 设置当前昵称。
     */
    fun setNickname(name: String) {
        _nickname.value = name
    }
}
