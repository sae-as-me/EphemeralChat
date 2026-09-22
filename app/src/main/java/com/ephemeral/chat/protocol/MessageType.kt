package com.ephemeral.chat.protocol

/**
 * 消息类型枚举——对应设计方案 5.1 节。
 * 每种类型有固定的 JSON 字段集和传输方向。
 */
enum class MessageType(val code: String) {
    JOIN("join"),           // Client→Hub: 加入请求
    JOIN_ACK("join_ack"),   // Hub→Client: 加入确认
    MSG("msg"),             // Client→Hub→All: 文本消息
    SYS("sys"),             // Hub→All: 系统消息
    HB("hb"),               // 双向: 心跳
    LEAVE("leave"),         // Client→Hub: 退出
    NICK("nick"),           // Client→Hub→All: 改名
    SYNC_REQ("sync_req"),   // Client→Hub: 历史同步请求
    SYNC_RSP("sync_rsp"),   // Hub→Client: 历史同步响应
    DISSOLVE("dissolve");   // Hub→All: 解散通知

    companion object {
        fun fromCode(code: String): MessageType? = entries.find { it.code == code }
    }
}
