package com.ephemeral.chat.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用更新工具——从 GitHub Releases 检查新版本并下载安装。
 *
 * 流程：
 * 1. 调用 GitHub API 获取最新 Release 的 tag_name 和 APK 下载地址
 * 2. 与当前版本（BuildConfig.VERSION_NAME）比较
 * 3. 若有新版本，下载 APK 到缓存目录
 * 4. 通过 FileProvider 触发系统安装器（安装时系统会提示覆盖安装，数据保留）
 */
object UpdateHelper {

    private const val TAG = "UpdateHelper"
    private const val API_URL = "https://api.github.com/repos/sae-as-me/EphemeralChat/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,      // 如 "0.4.0"
        val downloadUrl: String,        // APK 下载地址
        val releaseNotes: String,       // 更新说明
        val hasUpdate: Boolean,         // 是否有新版本
    )

    /**
     * 检查更新（在 IO 线程调用）。
     */
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "GitHub API 请求失败: $responseCode")
                return@withContext null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v")

            // 找 APK asset 的下载地址
            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name", "").endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val releaseNotes = json.optString("body", "")

            val currentVersion = com.ephemeral.chat.BuildConfig.VERSION_NAME
            val hasUpdate = tagName.isNotEmpty() && compareVersions(tagName, currentVersion) > 0

            Log.i(TAG, "检查更新: 当前=$currentVersion, 最新=$tagName, 有更新=$hasUpdate")
            UpdateInfo(
                latestVersion = tagName,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes,
                hasUpdate = hasUpdate,
            )
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            null
        }
    }

    /**
     * 比较语义化版本号，返回 >0 表示 v1 更新。
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }

    /**
     * 下载 APK 到缓存目录（在 IO 线程调用）。
     *
     * @param onProgress 进度回调 (0-100)，-1 表示失败
     * @return 下载的 APK 文件，null 表示失败
     */
    suspend fun downloadApk(context: Context, downloadUrl: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 60000

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    Log.e(TAG, "下载失败: HTTP $responseCode")
                    onProgress(-1)
                    return@withContext null
                }

                val totalBytes = connection.contentLength.toLong()
                val apkFile = File(context.cacheDir, "ephemeral_update.apk")
                var downloadedBytes = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }

                Log.i(TAG, "APK 下载完成: ${apkFile.absolutePath}, ${downloadedBytes} bytes")
                withContext(Dispatchers.Main) { onProgress(100) }
                apkFile
            } catch (e: Exception) {
                Log.e(TAG, "下载 APK 失败", e)
                withContext(Dispatchers.Main) { onProgress(-1) }
                null
            }
        }

    /**
     * 触发系统安装器安装 APK。
     * 注意：由于 debug APK 与已安装版本签名不同，安装器会提示"卸载后安装"——这是正常的，
     * 系统覆盖安装时数据是否保留取决于签名一致性。
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile,
                    )
                } else {
                    Uri.fromFile(apkFile)
                }
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "已触发安装器")
        } catch (e: Exception) {
            Log.e(TAG, "触发安装失败", e)
        }
    }
}
