package com.ephemeral.chat.protocol

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 消息分片协议——超过 MTU 的消息自动分片。
 * 分片头 3 bytes: msg_id(2) + seq_flag(1) [1 bit is_last | 7 bits seq 0-127]
 *
 * @param mtu MTU 大小，默认 247
 */
class MessageFragmenter(private val mtu: Int = 247) {

    private val TAG = "MessageFragmenter"

    /** 每片最大 payload = MTU - 3(头) */
    private val maxPayload = mtu - 3

    /** 递增的 msg_id，溢出回绕 */
    private var nextMsgId = 0

    /** 重组缓冲区：Map<msgId, Map<seq, ByteArray>> */
    private val reassemblyBuffer = ConcurrentHashMap<Int, MutableMap<Int, ByteArray>>()

    /** 重组元数据：Map<msgId, isLastReceived> */
    private val isLastReceived = ConcurrentHashMap<Int, Boolean>()

    /**
     * 将数据分片。
     *
     * @param data 原始数据
     * @return 分片列表，每片包含 3 字节头
     */
    fun fragment(data: ByteArray): List<ByteArray> {
        if (data.size <= maxPayload) {
            // 无需分片，单片发送
            val msgId = nextMsgId
            nextMsgId = (nextMsgId + 1) and 0xFFFF
            val header = byteArrayOf(
                ((msgId shr 8) and 0xFF).toByte(),
                (msgId and 0xFF).toByte(),
                (0 or 0x80).toByte(), // is_last=1, seq=0
            )
            return listOf(header + data)
        }

        val fragments = mutableListOf<ByteArray>()
        val msgId = nextMsgId
        nextMsgId = (nextMsgId + 1) and 0xFFFF

        var offset = 0
        var seq = 0

        while (offset < data.size) {
            val chunkSize = minOf(maxPayload, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + chunkSize)

            val isLast = offset + chunkSize >= data.size
            val seqFlag = ((seq and 0x7F) or if (isLast) 0x80 else 0x00).toByte()

            val header = byteArrayOf(
                ((msgId shr 8) and 0xFF).toByte(),
                (msgId and 0xFF).toByte(),
                seqFlag,
            )

            fragments.add(header + chunk)

            offset += chunkSize
            seq++
        }

        Log.d(TAG, "数据 ${data.size} 字节分片为 ${fragments.size} 片, msgId=$msgId")
        return fragments
    }

    /**
     * 重组分片数据。
     * 收到所有分片（包括 is_last=1 的分片）后返回完整数据。
     * 缺片则返回 null。
     *
     * @param fragment 单个分片（含 3 字节头）
     * @return 完整数据（如果重组完成），否则 null
     */
    fun reassemble(fragment: ByteArray): ByteArray? {
        if (fragment.size < 3) return null

        val msgId = ((fragment[0].toInt() and 0xFF) shl 8) or (fragment[1].toInt() and 0xFF)
        val seqFlag = fragment[2].toInt() and 0xFF
        val isLast = (seqFlag and 0x80) != 0
        val seq = seqFlag and 0x7F

        val payload = fragment.copyOfRange(3, fragment.size)

        val buffer = reassemblyBuffer.getOrPut(msgId) { ConcurrentHashMap() }
        buffer[seq] = payload

        if (isLast) {
            isLastReceived[msgId] = true
        }

        // 检查是否所有分片都已到达
        if (isLastReceived[msgId] == true) {
            val totalFragments = seq + 1 // 最后一片的 seq + 1 = 总片数
            if (buffer.size >= totalFragments) {
                // 重组
                val result = ByteArrayOutputStream()
                for (i in 0 until totalFragments) {
                    val chunk = buffer[i]
                    if (chunk == null) {
                        Log.w(TAG, "msgId=$msgId 缺少分片 seq=$i，丢弃")
                        reassemblyBuffer.remove(msgId)
                        isLastReceived.remove(msgId)
                        return null
                    }
                    result.write(chunk)
                }

                reassemblyBuffer.remove(msgId)
                isLastReceived.remove(msgId)
                Log.d(TAG, "msgId=$msgId 重组完成，${result.size()} 字节")
                return result.toByteArray()
            }
        }

        return null
    }

    /**
     * 清除所有重组缓冲区。
     */
    fun clear() {
        reassemblyBuffer.clear()
        isLastReceived.clear()
    }
}

// 简易 ByteArrayOutputStream，避免引入额外 import
private class ByteArrayOutputStream {
    private val buffer = java.io.ByteArrayOutputStream()
    fun write(data: ByteArray) = buffer.write(data)
    fun size(): Int = buffer.size()
    fun toByteArray(): ByteArray = buffer.toByteArray()
}
