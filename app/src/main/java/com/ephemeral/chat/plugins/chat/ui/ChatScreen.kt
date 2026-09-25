package com.ephemeral.chat.plugins.chat.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ephemeral.chat.core.ui.adaptive.adaptiveDp
import com.ephemeral.chat.core.ui.adaptive.adaptiveSp
import com.ephemeral.chat.plugins.chat.ChatViewModel
import com.ephemeral.chat.plugins.chat.FileTransferHelper
import com.ephemeral.chat.plugins.storage.entity.MessageEntity
import com.ephemeral.chat.plugins.storage.entity.MessageType
import org.json.JSONObject

/**
 * 聊天主界面。
 * 右上角为"更多"菜单：
 * - 返回但不退出：回到首页，群聊保留在列表
 * - 退出当前群聊：二次确认后退出
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showFileSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 消息列表状态：新消息到达时自动滚动到底部
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size - 1)
        }
    }

    // 图片选择器（PhotoPicker）
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                it.moveToFirst()
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val fileName = if (nameIndex >= 0) it.getString(nameIndex) else "image.jpg"
                val tempFile = java.io.File.createTempFile("img_", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                viewModel.sendImage(tempFile.absolutePath, fileName)
                // 修复：tempFile 由 sendImage 在 IO 线程读完后再删除，不能在主线程立即删
            }
        }
    }

    // 拍照
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val tempFile = java.io.File.createTempFile("cam_", ".jpg", context.cacheDir)
            java.io.FileOutputStream(tempFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            viewModel.sendImage(tempFile.absolutePath, "camera.jpg")
            // tempFile 由 sendImage 在 IO 线程读完后再删除
        }
    }

    // 文件选择器
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                it.moveToFirst()
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                val fileName = if (nameIndex >= 0) it.getString(nameIndex) else "file"
                val fileSize = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val tempFile = java.io.File.createTempFile("file_", ".bin", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                viewModel.sendFile(tempFile.absolutePath, fileName, fileSize, mimeType)
                // tempFile 由 sendFile 在 IO 线程读完后再删除
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "邀请码: ${state.inviteCode}",
                            fontSize = adaptiveSp(16f),
                        )
                        Spacer(modifier = Modifier.width(adaptiveDp(8f)))
                        Text(
                            text = "(${state.members.size}人)",
                            fontSize = adaptiveSp(14f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // 成员列表
                    IconButton(onClick = { viewModel.showMembers() }) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = "成员列表",
                            modifier = Modifier.size(adaptiveDp(24f)),
                        )
                    }
                    // 更多菜单（竖排省略号）
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "更多",
                                modifier = Modifier.size(adaptiveDp(24f)),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("返回但不退出", fontSize = adaptiveSp(14f)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.backToHomeKeepGroup()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(adaptiveDp(20f)),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("退出当前群聊", fontSize = adaptiveSp(14f)) },
                                onClick = {
                                    menuExpanded = false
                                    showExitDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(adaptiveDp(20f)),
                                    )
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 错误提示（如广播失败）显示在最上方
            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    fontSize = adaptiveSp(12f),
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(adaptiveDp(4f)),
                )
            }

            // 文件传输进度条
            if (state.fileTransferProgress >= 0) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(adaptiveDp(8f)),
                ) {
                    Text(
                        text = state.fileTransferStatus,
                        fontSize = adaptiveSp(12f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { state.fileTransferProgress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = adaptiveDp(4f)),
                    )
                }
            }

            // 消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(adaptiveDp(8f)),
                verticalArrangement = Arrangement.spacedBy(adaptiveDp(4f)),
            ) {
                items(state.messages) { msg ->
                    MessageBubble(msg, msg.senderUuid == state.myUuidShort, viewModel)
                }
            }

            // 底部输入栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(adaptiveDp(8f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var inputText by remember { mutableStateOf("") }
                // "+" 按钮：打开文件选择底部弹窗
                IconButton(onClick = { showFileSheet = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "发送文件",
                        modifier = Modifier.size(adaptiveDp(28f)),
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息", fontSize = adaptiveSp(14f)) },
                )
                Spacer(modifier = Modifier.width(adaptiveDp(8f)))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        modifier = Modifier.size(adaptiveDp(24f)),
                    )
                }
            }
        }
    }

    // 退出群聊二次确认对话框
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出当前群聊", fontSize = adaptiveSp(16f)) },
            text = {
                Text(
                    "确定要退出当前群聊吗？退出后将清除本地聊天记录，如需再次加入需重新输入邀请码。",
                    fontSize = adaptiveSp(14f),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.leaveGroup()
                }) {
                    Text("退出", fontSize = adaptiveSp(14f), color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("取消", fontSize = adaptiveSp(14f))
                }
            },
        )
    }

    // 文件选择底部弹窗
    if (showFileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFileSheet = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(adaptiveDp(16f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("发送文件", fontSize = adaptiveSp(16f), color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(adaptiveDp(16f)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // 相册
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showFileSheet = false
                            imagePicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "相册", modifier = Modifier.size(adaptiveDp(40f)), tint = MaterialTheme.colorScheme.primary)
                        Text("相册", fontSize = adaptiveSp(12f))
                    }
                    // 拍照
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showFileSheet = false
                            cameraLauncher.launch(null)
                        },
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "拍照", modifier = Modifier.size(adaptiveDp(40f)), tint = MaterialTheme.colorScheme.primary)
                        Text("拍照", fontSize = adaptiveSp(12f))
                    }
                    // 文件
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showFileSheet = false
                            filePicker.launch("*/*")
                        },
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "文件", modifier = Modifier.size(adaptiveDp(40f)), tint = MaterialTheme.colorScheme.primary)
                        Text("文件", fontSize = adaptiveSp(12f))
                    }
                }
                Spacer(modifier = Modifier.height(adaptiveDp(24f)))
            }
        }
    }

    // 全屏图片预览
    state.previewImagePath?.let { path ->
        ImagePreviewScreen(viewModel = viewModel, imagePath = path)
    }
}

/**
 * 消息气泡组件——支持文本、图片、文件三种类型。
 */
@Composable
fun MessageBubble(message: MessageEntity, isMine: Boolean, viewModel: ChatViewModel) {
    val bgColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    when (message.type) {
        MessageType.SYSTEM -> {
            Text(
                text = message.content,
                fontSize = adaptiveSp(12f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(adaptiveDp(4f)),
            )
        }
        MessageType.IMAGE -> {
            // 图片气泡
            val meta = remember(message.content) {
                runCatching { FileTransferHelper.FileMetaData.fromJson(message.content) }.getOrNull()
            }
            // 修复：异步解码图片，避免在主线程做 BitmapFactory.decodeFile 导致 ANR
            var bitmap by remember(meta?.localPath) {
                mutableStateOf<android.graphics.Bitmap?>(null)
            }
            LaunchedEffect(meta?.localPath) {
                meta?.localPath?.let { path ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                    }?.let { bitmap = it }
                }
            }
            if (meta == null) {
                Text("图片解析失败", fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.error)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(adaptiveDp(12f)))
                            .width(adaptiveDp(200f)),
                    ) {
                        if (bitmap != null) {
                            val bmp = bitmap!!
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(adaptiveDp(200f))
                                    .clickable { meta.localPath?.let { viewModel.openImagePreview(it) } },
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(adaptiveDp(200f)).background(bgColor),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("图片加载中...", fontSize = adaptiveSp(12f), color = textColor)
                            }
                        }
                        if (!isMine && meta.localPath != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(adaptiveDp(4f)),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(
                                    onClick = { viewModel.saveImageToGallery(meta.localPath, meta.fileName) },
                                    modifier = Modifier.size(adaptiveDp(24f)),
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "保存", modifier = Modifier.size(adaptiveDp(20f)), tint = textColor)
                                }
                            }
                        }
                    }
                }
            }
        }
        MessageType.FILE -> {
            // 文件气泡
            val meta = remember(message.content) {
                runCatching { FileTransferHelper.FileMetaData.fromJson(message.content) }.getOrNull()
            }
            if (meta == null) {
                Text("文件解析失败", fontSize = adaptiveSp(12f), color = MaterialTheme.colorScheme.error)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(adaptiveDp(12f)))
                            .background(bgColor)
                            .padding(adaptiveDp(12f))
                            .width(adaptiveDp(240f)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(adaptiveDp(20f)), tint = textColor)
                            Spacer(modifier = Modifier.width(adaptiveDp(8f)))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.fileName,
                                    fontSize = adaptiveSp(14f),
                                    color = textColor,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "${meta.fileSize / 1024} KB",
                                    fontSize = adaptiveSp(12f),
                                    color = textColor.copy(alpha = 0.7f),
                                )
                            }
                        }
                        if (!isMine && meta.localPath != null) {
                            Spacer(modifier = Modifier.height(adaptiveDp(8f)))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = { viewModel.saveFileToDownloads(meta.localPath, meta.fileName, meta.mimeType) },
                                ) {
                                    Text("保存", fontSize = adaptiveSp(12f), color = textColor)
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> {
            // 文本消息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(adaptiveDp(12f)))
                        .background(bgColor)
                        .padding(adaptiveDp(12f))
                        .width(adaptiveDp(240f)),
                ) {
                    if (!isMine) {
                        Text(
                            text = message.senderName,
                            fontSize = adaptiveSp(12f),
                            color = textColor.copy(alpha = 0.7f),
                        )
                        Spacer(modifier = Modifier.height(adaptiveDp(2f)))
                    }
                    Text(
                        text = message.content,
                        fontSize = adaptiveSp(14f),
                        color = textColor,
                    )
                }
            }
        }
    }
}