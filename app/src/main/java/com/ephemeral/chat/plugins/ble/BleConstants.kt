package com.ephemeral.chat.plugins.ble

/**
 * BLE 相关常量定义。
 * 包含 GATT UUID、MTU、广播间隔、最大连接数等。
 */
object BleConstants {
    /** GATT Service UUID（自定义，基于蓝牙基础 UUID） */
    const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"

    /** Command 特征值 UUID（Client→Hub Write） */
    const val COMMAND_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"

    /** Notification 特征值 UUID（Hub→All Notify） */
    const val NOTIFICATION_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

    /** 广播 Service UUID（用于扫描过滤） */
    const val ADVERTISE_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"

    /** 请求的 MTU 大小 */
    const val REQUESTED_MTU = 247

    /** 广播间隔（毫秒） */
    const val ADVERTISE_INTERVAL_MS = 250L

    /** 最大连接数 */
    const val MAX_CONNECTIONS = 7

    /** 距离阈值（米） */
    const val DISTANCE_THRESHOLD_M = 100.0

    /** 客户端特征值配置描述符 UUID */
    const val CLIENT_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
}
