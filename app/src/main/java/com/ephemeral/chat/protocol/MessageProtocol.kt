package com.ephemeral.chat.protocol

import org.json.JSONObject

/**
 * 消息协议——紧凑 JSON 格式。
 * 字段缩写以节省 BLE 带宽。
 *
 * @property t 消息类型（type）
 * @property u 发送者 UUID 截短 4 位
 * @property n 发送者昵称（可为 null）
 * @property c 消息内容（加密后的 Base64，可为 null）
 * @property g 群组 ID 截短 4 位
 * @property ts 时间戳
 * @property mid 消息 ID（可为 null）
 */
data class ChatMessage(
    val t: String,
    val u: String,
    val n: String? = null,
    val c: String? = null,
    val g: String,
    val ts: Long,
    val mid: String? = null,
) {
    /**
     * 序列化为 JSON 字符串。
     * null 字段不输出以节省带宽。
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("t", t)
        json.put("u", u)
        n?.let { json.put("n", it) }
        c?.let { json.put("c", it) }
        json.put("g", g)
        json.put("ts", ts)
        mid?.let { json.put("mid", it) }
        return json.toString()
    }

    companion object {
        /**
         * 从 JSON 字符串反序列化。
         * 须处理缺失字段的默认值。
         */
        fun fromJson(json: String): ChatMessage {
            val obj = JSONObject(json)
            return ChatMessage(
                t = obj.optString("t", ""),
                u = obj.optString("u", ""),
                n = if (obj.has("n") && !obj.isNull("n")) obj.getString("n") else null,
                c = if (obj.has("c") && !obj.isNull("c")) obj.getString("c") else null,
                g = obj.optString("g", ""),
                ts = obj.optLong("ts", 0L),
                mid = if (obj.has("mid") && !obj.isNull("mid")) obj.getString("mid") else null,
            )
        }
    }
}
