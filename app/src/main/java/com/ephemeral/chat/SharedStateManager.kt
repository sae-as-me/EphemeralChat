package com.ephemeral.chat

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 全局共享状态管理器。
 * 持有跨 ViewModel / 跨页面共享的应用级状态，并通过 DataStore 持久化。
 * - 主题状态：MainActivity 读取驱动 EphemeralChatTheme；ProfileViewModel 切换写入
 * - 昵称状态：ChatViewModel 和 ProfileViewModel 共同读写
 */
object SharedStateManager {

    private const val TAG = "SharedStateManager"

    private val Context.dataStore by preferencesDataStore(name = "settings")

    /** DataStore key 定义 */
    private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
    private val KEY_NICKNAME = stringPreferencesKey("nickname")

    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO)

    /** 深色主题开关，默认 true（深色优先） */
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    /** 当前用户昵称，空字符串表示尚未设置 */
    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    /** 是否已初始化 */
    private var initialized = false

    /**
     * 初始化——从 DataStore 异步读取持久化的主题和昵称。
     * 须在 Application.onCreate 中调用一次。
     * 注意：异步读取避免阻塞主线程（低端机上 DataStore 首次读取可能导致 ANR/启动慢）。
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        scope.launch {
            try {
                appContext.dataStore.data.first().let { prefs ->
                    _isDarkTheme.value = prefs[KEY_DARK_THEME] ?: true
                    _nickname.value = prefs[KEY_NICKNAME] ?: ""
                }
                android.util.Log.i(TAG, "初始化完成: darkTheme=${_isDarkTheme.value}, nickname=${_nickname.value}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "初始化失败（使用默认值）", e)
            }
        }
    }

    /**
     * 切换深色/浅色主题。
     */
    fun toggleTheme() {
        val newValue = !_isDarkTheme.value
        setDarkTheme(newValue)
    }

    /**
     * 设置主题并持久化。
     */
    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        scope.launch {
            appContext.dataStore.edit { it[KEY_DARK_THEME] = enabled }
        }
    }

    /**
     * 设置当前昵称并持久化。
     */
    fun setNickname(name: String) {
        _nickname.value = name
        scope.launch {
            appContext.dataStore.edit { it[KEY_NICKNAME] = name }
        }
    }
}