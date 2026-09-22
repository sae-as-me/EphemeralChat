package com.ephemeral.chat.plugins.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ui.collectAsStateLifecycle
import com.ephemeral.chat.plugins.profile.ProfileViewModel

/**
 * 个人信息界面。
 */
@Composable
fun ProfileScreen() {
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(adaptiveDp(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(adaptiveDp(32f)))

        Text(
            text = "EphemeralChat",
            fontSize = adaptiveSp(24f),
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(24f)))

        // 当前昵称
        if (state.currentNickname.isNotEmpty()) {
            Text(
                text = "当前昵称: ${state.currentNickname}",
                fontSize = adaptiveSp(14f),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(adaptiveDp(16f)))
        }

        HorizontalDivider()

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // 深色主题切换
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(adaptiveDp(8f)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("深色主题", fontSize = adaptiveSp(14f))
            Switch(
                checked = state.isDarkTheme,
                onCheckedChange = { viewModel.toggleTheme() },
            )
        }

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // 版本号
        Text(
            text = "版本 ${state.appVersion}",
            fontSize = adaptiveSp(14f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(32f)))

        // 关于
        Text(
            text = "EphemeralChat - 临时群聊，在场即在场，离场即消散",
            fontSize = adaptiveSp(12f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
