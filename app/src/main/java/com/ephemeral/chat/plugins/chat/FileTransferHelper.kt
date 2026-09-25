package com.ephemeral.chat.plugins.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.json.JSONObject

/**
 * 文件传输工具——图片压缩、文件读写、另存为相册/下载。
 * 所有文件临时存于应用私有目录，群聊解散时自动清除。
 */
object FileTransferHelper {

    private const val TAG = "FileTransferHelper"
    private const val MAX_IMAGE_DIM = 1280
    private const val JPEG_QUALITY = 85
    private const val MAX_IMAGE_BYTES = 200_000
    private const val MAX_FILE_BYTES = 10_000_000
    private const val FILE_DIR = "ephemeral_files"

    /**
     * 图片元信息+base64数据 的 JSON 包装。
     * 存入 MessageEntity.content，UI 解析后渲染图片气泡。
     */
    data class FileMetaData(
        val fileName: String,
        val mimeType: String,
        val fileSize: Long,
        val isImage: Boolean,
        val localPath: String? = null,
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("fn", fileName)
                put("fm", mimeType)
                put("fs", fileSize)
                put("im", isImage)
                localPath?.let { put("lp", it) }
            }.toString()
        }

        companion object {
            fun fromJson(json: String): FileMetaData {
                val obj = JSONObject(json)
                return FileMetaData(
                    fileName = obj.optString("fn", "file"),
                    mimeType = obj.optString("fm", "application/octet-stream"),
                    fileSize = obj.optLong("fs", 0),
                    isImage = obj.optBoolean("im", false),
                    localPath = if (obj.has("lp") && !obj.isNull("lp")) obj.getString("lp") else null,
                )
            }
        }
    }

    /**
     * 获取群聊文件存储目录。
     */
    fun getGroupFileDir(context: Context, groupId: String): File {
        val dir = File(context.filesDir, "$FILE_DIR/$groupId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 删除群聊的所有临时文件。
     */
    fun clearGroupFiles(context: Context, groupId: String) {
        try {
            val dir = File(context.filesDir, "$FILE_DIR/$groupId")
            if (dir.exists()) {
                dir.deleteRecursively()
                Log.i(TAG, "已清除群聊文件: $groupId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除群聊文件失败", e)
        }
    }

    /**
     * 删除所有临时文件（解散时调用）。
     */
    fun clearAllFiles(context: Context) {
        try {
            val dir = File(context.filesDir, FILE_DIR)
            if (dir.exists()) {
                dir.deleteRecursively()
                Log.i(TAG, "已清除所有临时文件")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除所有文件失败", e)
        }
    }

    /**
     * 压缩图片为 JPEG，返回 base64 编码字节数据。
     * 压缩策略：长边 1280px、质量 85%，循环降质直到 ≤ 200KB。
     *
     * @param inputStream 图片输入流
     * @return (base64 字符串, 压缩后字节数) 或 null 表示失败
     */
    fun compressImageToBase64(inputStream: InputStream): Pair<String, Int>? {
        return try {
            val bitmap = BitmapFactory.decodeStream(inputStream)
                ?: return null
            compressBitmapToBase64(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "图片压缩失败", e)
            null
        }
    }

    /**
     * 从文件路径压缩图片。
     */
    fun compressImageFileToBase64(filePath: String): Pair<String, Int>? {
        return try {
            val bitmap = BitmapFactory.decodeFile(filePath)
                ?: return null
            compressBitmapToBase64(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "图片文件压缩失败", e)
            null
        }
    }

    private fun compressBitmapToBase64(bitmap: Bitmap): Pair<String, Int> {
        // 缩放长边到 MAX_IMAGE_DIM
        val scaledBitmap = scaleBitmap(bitmap, MAX_IMAGE_DIM)
        val isDifferentBitmap = scaledBitmap !== bitmap

        var quality = JPEG_QUALITY
        var bytes: ByteArray

        do {
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            bytes = baos.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_IMAGE_BYTES && quality > 20)

        // 修复：回收缩放后产生的新 Bitmap，防止内存泄漏
        if (isDifferentBitmap) {
            scaledBitmap.recycle()
        }

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Log.d(TAG, "图片压缩完成: ${bytes.size} bytes, quality=$quality")
        return Pair(base64, bytes.size)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = maxOf(width, height)

        if (maxSide <= maxDim) return bitmap

        val scale = maxDim.toFloat() / maxSide
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 读取文件为 base64 字符串。
     */
    fun readFileToBase64(filePath: String): Pair<String, Int>? {
        return try {
            val file = File(filePath)
            if (!file.exists() || file.length() > MAX_FILE_BYTES) return null
            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Pair(base64, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "读取文件失败", e)
            null
        }
    }

    /**
     * 将 base64 数据解码并保存为临时文件。
     *
     * @return 保存后的文件路径，或 null 表示失败
     */
    fun saveBase64ToFile(context: Context, groupId: String, fileId: String, base64Data: String, fileName: String): String? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val dir = getGroupFileDir(context, groupId)
            // 修复：正确提取扩展名，处理无扩展名和空扩展名的边界情况
            val ext = if (fileName.contains(".") && !fileName.endsWith(".")) {
                fileName.substringAfterLast(".")
            } else {
                "bin"
            }
            val file = File(dir, "$fileId.$ext")
            FileOutputStream(file).use { it.write(bytes) }
            Log.d(TAG, "文件已保存: ${file.absolutePath}, ${bytes.size} bytes")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "保存文件失败", e)
            null
        }
    }

    /**
     * 另存图片到系统相册（MediaStore）。
     *
     * @return 保存后的 Uri，或 null 表示失败
     */
    fun saveImageToGallery(context: Context, localPath: String, fileName: String): Uri? {
        return try {
            val sourceFile = File(localPath)
            if (!sourceFile.exists()) return null

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EphemeralChat")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, contentValues) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Log.i(TAG, "图片已保存到相册: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "保存图片到相册失败", e)
            null
        }
    }

    /**
     * 另存文件到下载目录（MediaStore Downloads）。
     *
     * @return 保存后的 Uri，或 null 表示失败
     */
    fun saveFileToDownloads(context: Context, localPath: String, fileName: String, mimeType: String): Uri? {
        return try {
            val sourceFile = File(localPath)
            if (!sourceFile.exists()) return null

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EphemeralChat")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = resolver.insert(collection, contentValues) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Log.i(TAG, "文件已保存到下载目录: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "保存文件到下载失败", e)
            null
        }
    }

    /**
     * 检查文件大小是否超限。
     */
    fun isFileSizeValid(fileSize: Long): Boolean = fileSize <= MAX_FILE_BYTES
}
