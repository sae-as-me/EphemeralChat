package com.ephemeral.chat.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ephemeral.chat.MainActivity

/**
 * 新消息通知工具——后台收到群聊消息时以弹窗（Heads-up）形式通知。
 * 弹窗开关由"我的"页面控制，默认关闭。
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "ephemeral_chat_messages"

    /**
     * 创建通知渠道（Android 8.0+ 要求）。
     * IMPORTANCE_HIGH 使通知以弹窗（Heads-up）形式展示。
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "群聊消息",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "后台收到新群聊消息时弹窗提醒"
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 发送新消息弹窗通知。
     *
     * @param senderName 发送者昵称
     * @param content 消息内容
     */
    fun showMessageNotification(context: Context, senderName: String, content: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(senderName)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            // 用时间戳作为通知 ID，避免新消息覆盖旧消息
            manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "发送通知失败", e)
        }
    }

    /** Android 13+ 需要 POST_NOTIFICATIONS 运行时权限；更低版本无需 */
    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}