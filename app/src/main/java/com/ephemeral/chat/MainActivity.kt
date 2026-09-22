package com.ephemeral.chat

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.core.ui.theme.EphemeralChatTheme
import com.ephemeral.chat.navigation.BottomNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主 Activity——应用唯一入口。
 * 负责运行时权限请求，权限通过后进入 BottomNavHost。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EphemeralChatTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PermissionGate()
                }
            }
        }
    }
}

/**
 * 权限网关——请求所有必要权限，通过后显示 BottomNavHost。
 * 根据 Android 版本分组请求不同权限。
 */
@Composable
private fun PermissionGate() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 根据 Android 版本确定需要请求的权限
    val requiredPermissions: List<String> = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    var allGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var showDeniedDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        allGranted = result.values.all { it }
        if (!allGranted) {
            showDeniedDialog = true
        }
    }

    if (allGranted) {
        BottomNavHost()
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = "EphemeralChat 需要蓝牙和定位权限才能创建和加入群聊",
                    fontSize = adaptiveSp(14f),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Button(onClick = { launcher.launch(requiredPermissions.toTypedArray()) }) {
                    Text("授予权限", fontSize = adaptiveSp(14f))
                }
            }
        }
    }

    if (showDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showDeniedDialog = false },
            title = { Text("权限被拒绝", fontSize = adaptiveSp(16f)) },
            text = {
                Text(
                    "EphemeralChat 需要蓝牙和定位权限才能正常工作。\n请前往设置 → 应用 → EphemeralChat → 权限，手动开启。",
                    fontSize = adaptiveSp(14f),
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDeniedDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("去设置", fontSize = adaptiveSp(14f))
                }
            },
            dismissButton = {
                Button(onClick = {
                    showDeniedDialog = false
                    // 重新检查权限状态
                    allGranted = requiredPermissions.all {
                        context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                }) {
                    Text("关闭", fontSize = adaptiveSp(14f))
                }
            },
        )
    }
}
