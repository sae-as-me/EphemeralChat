package com.ephemeral.chat.plugins.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ui.collectAsStateLifecycle
import com.ephemeral.chat.plugins.profile.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * 个人信息界面。
 * 显示当前昵称、修改昵称、主题切换、版本号、关于。
 */
@Composable
fun ProfileScreen() {
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateLifecycle()
    val context = LocalContext.current

    // 进入页面时刷新状态
    LaunchedEffect(Unit) { viewModel.refresh() }

    // 通知权限请求（Android 13+ 开启弹窗开关时才需要）
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // 用户拒绝了通知权限：回退开关为关闭状态
            viewModel.toggleShowNotifications(false)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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
        HorizontalDivider()
        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // ---- 昵称区域 ----
        Text(
            text = "当前昵称: ${state.currentNickname}",
            fontSize = adaptiveSp(14f),
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(8f)))

        // 昵称编辑
        var editNickname by remember { mutableStateOf(state.currentNickname) }
        OutlinedTextField(
            value = editNickname,
            onValueChange = { editNickname = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("修改昵称", fontSize = adaptiveSp(14f)) },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(8f)))

        Button(
            onClick = { viewModel.setNickname(editNickname) },
            enabled = editNickname.isNotBlank() && editNickname != state.currentNickname,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存昵称", fontSize = adaptiveSp(14f))
        }

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // ---- 主题切换 ----
        Row(
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

        // ---- 后台新消息弹窗开关 ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(adaptiveDp(8f)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("弹窗通知", fontSize = adaptiveSp(14f))
                Text(
                    "后台收到新消息时弹窗提醒",
                    fontSize = adaptiveSp(11f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.showNotifications,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        // 先请求权限再开启（Android 13+ 需要）
                        requestNotificationPermissionIfNeeded()
                    }
                    viewModel.toggleShowNotifications(enabled)
                },
            )
        }

        Spacer(modifier = Modifier.height(adaptiveDp(16f)))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(adaptiveDp(16f)))

        // ---- 版本号 + 检查更新 ----
        Text(
            text = "版本 ${state.appVersion}",
            fontSize = adaptiveSp(14f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(adaptiveDp(12f)))

        // 检查更新按钮
        var isChecking by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf(0) }
        var updateResult by remember { mutableStateOf<String?>(null) }
        var pendingApkUrl by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        if (!isDownloading) {
            Button(
                onClick = {
                    if (isChecking) return@Button
                    isChecking = true
                    updateResult = null
                    scope.launch {
                        val info = com.ephemeral.chat.service.UpdateHelper.checkUpdate()
                        isChecking = false
                        if (info == null) {
                            updateResult = "检查更新失败，请检查网络后重试"
                        } else if (info.hasUpdate) {
                            updateResult = "发现新版本 v${info.latestVersion}"
                            pendingApkUrl = info.downloadUrl
                        } else {
                            updateResult = "当前已是最新版本"
                        }
                    }
                },
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth(0.6f),
            ) {
                Text(
                    text = if (isChecking) "正在检查..." else "检查更新",
                    fontSize = adaptiveSp(14f),
                )
            }
        } else {
            // 下载进度
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (downloadProgress >= 0) "正在下载 $downloadProgress%" else "下载失败",
                    fontSize = adaptiveSp(13f),
                    color = if (downloadProgress >= 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(adaptiveDp(4f)))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
            }
        }

        updateResult?.let { result ->
            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
            Text(
                text = result,
                fontSize = adaptiveSp(12f),
                color = if (result.startsWith("发现")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // 有新版本时显示下载按钮
            if (result.startsWith("发现") && pendingApkUrl != null && !isDownloading) {
                Spacer(modifier = Modifier.height(adaptiveDp(8f)))
                Button(
                    onClick = {
                        isDownloading = true
                        downloadProgress = 0
                        scope.launch {
                            val apkFile = com.ephemeral.chat.service.UpdateHelper.downloadApk(
                                context,
                                pendingApkUrl!!,
                            ) { progress ->
                                downloadProgress = progress
                                if (progress < 0) isDownloading = false
                            }
                            if (apkFile != null) {
                                isDownloading = false
                                com.ephemeral.chat.service.UpdateHelper.installApk(context, apkFile)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.6f),
                ) {
                    Text("下载并安装", fontSize = adaptiveSp(14f))
                }
            }
        }

        Spacer(modifier = Modifier.height(adaptiveDp(32f)))

        // ---- 关于 ----
        Text(
            text = "EphemeralChat - 临时群聊，在场即在场，离场即消散",
            fontSize = adaptiveSp(12f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
