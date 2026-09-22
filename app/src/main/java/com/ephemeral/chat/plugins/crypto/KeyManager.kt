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

    /** 群组密钥存储：Map<groupId, SecretKey>，线程安全 */
    private val groupKeys = ConcurrentHashMap<String, SecretKey>()

    /**
     * 获取数据库密钥（用于 SQLCipher）。
     * 从 Keystore 获取或生成 AES-256 密钥。
     * 返回密钥的字节数组（32 字节）。
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
        // 解决方案：用一个固定 IV 加密固定明文，取密文作为 passphrase
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val fixedPlaintext = "ephemeral_chat_db_passphrase".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(fixedPlaintext)
        // 用加密结果作为 SQLCipher 密码（32 字节）
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
