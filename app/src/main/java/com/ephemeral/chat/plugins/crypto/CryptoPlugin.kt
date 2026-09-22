package com.ephemeral.chat.plugins.crypto

import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey

/**
 * 加密插件——提供 ECDH 密钥协商和 AES-256-GCM 加解密能力。
 * 依赖：无（底层插件）
 */
class CryptoPlugin : IPlugin {
    override val pluginId = "crypto"
    override val dependencies = emptyList<String>()

    private lateinit var keyManager: KeyManager

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        keyManager = KeyManager()
    }

    override fun onStart() {}
    override fun onStop() {}
    override fun onDestroy() {
        keyManager.clearAllKeys()
    }

    // ---- 代理到 KeyManager ----

    fun getDatabaseKey(): ByteArray = keyManager.getDatabaseKey()

    fun setGroupKey(groupId: String, key: SecretKey) = keyManager.setGroupKey(groupId, key)
    fun getGroupKey(groupId: String): SecretKey? = keyManager.getGroupKey(groupId)
    fun removeGroupKey(groupId: String) = keyManager.removeGroupKey(groupId)
    fun clearAllKeys() = keyManager.clearAllKeys()

    // ---- 代理到 CryptoUtils ----

    fun generateKeyPair(): KeyPair = CryptoUtils.generateKeyPair()
    fun computeSharedSecret(priv: PrivateKey, pub: PublicKey): SecretKey =
        CryptoUtils.computeSharedSecret(priv, pub)
    fun encrypt(data: ByteArray, key: SecretKey): ByteArray = CryptoUtils.encrypt(data, key)
    fun decrypt(data: ByteArray, key: SecretKey): ByteArray = CryptoUtils.decrypt(data, key)
    fun sha256(input: String): ByteArray = CryptoUtils.sha256(input)
    fun generateInviteCode(): String = CryptoUtils.generateInviteCode()
    fun base64Encode(data: ByteArray): String = CryptoUtils.base64Encode(data)
    fun base64Decode(data: String): ByteArray = CryptoUtils.base64Decode(data)
    fun encodePublicKey(publicKey: PublicKey): String = CryptoUtils.encodePublicKey(publicKey)
    fun decodePublicKey(encoded: String): PublicKey = CryptoUtils.decodePublicKey(encoded)
}
