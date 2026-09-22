package com.ephemeral.chat.plugins.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow

/**
 * 在 Compose 中收集 StateFlow 的便捷扩展。
 * 与 collectAsState 等效，初始值取 StateFlow 当前值。
 */
@Composable
fun <T> StateFlow<T>.collectAsStateLifecycle(): State<T> {
    return collectAsState()
}
