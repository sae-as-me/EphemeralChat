package com.ephemeral.chat.plugins.profile

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 个人信息 ViewModel。
 * 管理当前用户名、主题切换、App 版本。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    data class ProfileUiState(
        val currentNickname: String = "",
        val isDarkTheme: Boolean = true,
        val appVersion: String = "1.0.0",
    )

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * 切换深色/浅色主题。
     */
    fun toggleTheme() {
        _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme)
    }

    /**
     * 设置当前昵称。
     */
    fun setNickname(nickname: String) {
        _uiState.value = _uiState.value.copy(currentNickname = nickname)
    }
}
