package com.ephemeral.chat.plugins.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 加密工具——ECDH 密钥协商 + AES-256-GCM 加解密。
 * 所有方法为纯函数，不持有状态。
 */
object CryptoUtils {

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    /**
     * 生成 ECDH 密钥对（secp256r1 / NIST P-256）。
     */
    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    /**
     * ECDH 共享密钥计算。
     * 用自己的私钥和对方的公钥计算共享密钥。
     */
    fun computeSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        val secret = keyAgreement.generateSecret()
        // 取前 32 字节作为 AES-256 密钥
        return SecretKeySpec(secret, 0, 32, "AES")
    }

    /**
     * AES-256-GCM 加密。
     * 返回 IV(12 bytes) + 密文 + GCM tag。
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
        val ciphertext = cipher.doFinal(plaintext)

        // 拼接 IV + 密文
        return iv + ciphertext
    }

    /**
     * AES-256-GCM 解密。
     * 输入为 IV(12 bytes) + 密文 + GCM tag。
     */
    fun decrypt(ciphertext: ByteArray, key: SecretKey): ByteArray {
        if (ciphertext.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("密文长度不足，至少需要 ${GCM_IV_LENGTH} 字节 IV")
        }

        val iv = ciphertext.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = ciphertext.copyOfRange(GCM_IV_LENGTH, ciphertext.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
        return cipher.doFinal(encrypted)
    }

    /**
     * SHA-256 哈希。
     */
    fun sha256(input: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
    }

    /**
     * Base64 编码。
     */
    fun base64Encode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /**
     * Base64 解码。
     */
    fun base64Decode(data: String): ByteArray {
        return Base64.decode(data, Base64.NO_WRAP)
    }

    /**
     * 随机邀请码生成（4位数字 "0000"-"9999"）。
     */
    fun generateInviteCode(): String {
        return Random.nextInt(0, 10000).toString().padStart(4, '0')
    }

    /**
     * 将公钥编码为 Base64 字符串（用于传输）。
     */
    fun encodePublicKey(publicKey: PublicKey): String {
        return base64Encode(publicKey.encoded)
    }

    /**
     * 从 Base64 字符串解码公钥。
     */
    fun decodePublicKey(encoded: String): PublicKey {
        val decoded = base64Decode(encoded)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(X509EncodedKeySpec(decoded))
    }
}
