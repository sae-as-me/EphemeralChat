package com.ephemeral.chat.plugins.profile

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.ephemeral.chat.SharedStateManager

/**
 * 个人信息 ViewModel。
 * 管理当前用户名、主题切换、App 版本。
 * 主题和昵称通过 SharedStateManager 实现跨页面共享。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    data class ProfileUiState(
        val currentNickname: String = "",
        val isDarkTheme: Boolean = true,
        val showNotifications: Boolean = false,
        val appVersion: String = com.ephemeral.chat.BuildConfig.VERSION_NAME,
    )

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            currentNickname = SharedStateManager.nickname.value,
            isDarkTheme = SharedStateManager.isDarkTheme.value,
            showNotifications = SharedStateManager.showNotifications.value,
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * 刷新状态——从 SharedStateManager 同步最新值。
     * 在 ProfileScreen 进入时调用。
     */
    fun refresh() {
        _uiState.value = _uiState.value.copy(
            currentNickname = SharedStateManager.nickname.value,
            isDarkTheme = SharedStateManager.isDarkTheme.value,
            showNotifications = SharedStateManager.showNotifications.value,
        )
    }

    /**
     * 切换深色/浅色主题。
     * 同时更新 SharedStateManager，MainActivity 自动响应。
     */
    fun toggleTheme() {
        SharedStateManager.toggleTheme()
        _uiState.value = _uiState.value.copy(isDarkTheme = SharedStateManager.isDarkTheme.value)
    }

    /**
     * 设置当前昵称。
     * 同时更新 SharedStateManager，ChatViewModel 和首页自动读取。
     */
    fun setNickname(nickname: String) {
        if (nickname.isBlank()) return
        SharedStateManager.setNickname(nickname)
        _uiState.value = _uiState.value.copy(currentNickname = nickname)
    }

    /**
     * 切换后台新消息弹窗开关。
     */
    fun toggleShowNotifications(enabled: Boolean) {
        SharedStateManager.setShowNotifications(enabled)
        _uiState.value = _uiState.value.copy(showNotifications = enabled)
    }
}
