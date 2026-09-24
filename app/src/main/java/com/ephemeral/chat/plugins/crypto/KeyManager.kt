package com.ephemeral.chat.plugins.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 密钥管理器——管理群组密钥的生命周期。
 * 密钥仅存内存，不持久化，解散时销毁。
 * 数据库密钥由 Android Keystore 生成和管理。
 */
class KeyManager {

    private val TAG = "KeyManager"

    private val keystore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** 数据库密钥别名 */
    private val dbKeyAlias = "ephemeral_db_key"

    /**
     * 固定 IV（12 字节）。
     * 用于派生稳定的数据库 passphrase。
     * 注意：此处不用随机 IV——因为 Android Keystore 无法导出密钥原始字节，
     * 只能用"固定明文 + 固定 IV 加密"得到稳定密文作为 SQLCipher 密码。
     * 该密码本身已足够随机（AES-GCM 密文），泄露风险与数据库文件同在。
     */
    private val fixedIv = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C)

    /** 群组密钥存储：Map<groupId, SecretKey>，线程安全 */
    private val groupKeys = ConcurrentHashMap<String, SecretKey>()

    /**
     * 获取数据库密钥（用于 SQLCipher）。
     * 从 Keystore 获取或生成 AES-256 密钥。
     * 返回固定 32 字节密文（同一 Keystore 密钥 + 固定 IV → 每次结果相同）。
     * 修复：GCM 随机 IV 导致每次调用返回不同密码 → SQLCipher 打不开已建库 → 闪退。
     */
    fun getDatabaseKey(): ByteArray {
        if (!keystore.containsAlias(dbKeyAlias)) {
            // 生成新密钥
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                dbKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
            Log.i(TAG, "已生成新的数据库密钥")
        }

        // 从 Keystore 取出密钥，转为 raw bytes 供 SQLCipher 使用
        val key = keystore.getKey(dbKeyAlias, null) as SecretKey
        // SQLCipher 需要 raw bytes，从 Keystore 密钥中导出
        // 注意：Android Keystore 不允许直接导出密钥字节
        // 解决方案：用固定 IV 加密固定明文，取密文前 32 字节作为 passphrase（稳定且随机）
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, fixedIv))
        val fixedPlaintext = "ephemeral_chat_db_passphrase".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(fixedPlaintext)
        // 用加密结果作为 SQLCipher 密码（固定 32 字节）
        return encrypted.copyOfRange(0, 32)
    }

    /**
     * 设置群组密钥。
     */
    fun setGroupKey(groupId: String, key: SecretKey) {
        groupKeys[groupId] = key
    }

    /**
     * 获取群组密钥。
     */
    fun getGroupKey(groupId: String): SecretKey? {
        return groupKeys[groupId]
    }

    /**
     * 移除群组密钥。
     */
    fun removeGroupKey(groupId: String) {
        groupKeys.remove(groupId)
    }

    /**
     * 清除所有群组密钥。
     */
    fun clearAllKeys() {
        groupKeys.clear()
        Log.i(TAG, "已清除所有群组密钥")
    }
}
