package com.ephemeral.chat.plugins.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.ephemeral.chat.protocol.MessageFragmenter
import java.util.UUID

/**
 * GATT Client 端（Client 角色）。
 * 职责：连接 Hub 的 GATT Server，写入指令，接收通知。
 */
class GattClient(private val context: Context) {

    private val TAG = "GattClient"
    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var notificationChar: BluetoothGattCharacteristic? = null

    /** 通知回调 */
    var onNotification: ((ByteArray) -> Unit)? = null

    /** 连接状态回调 */
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    /**
     * 分片器：每片固定 20 字节（含 3 字节头），保证在任意 MTU（即使协商失败降为 23）下
     * 消息都能完整送达 Server。Server 端按 msgId+seq 重组。
     */
    private val fragmenter = MessageFragmenter(20)
    private val writeQueue = ArrayDeque<ByteArray>()
    private var busy = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(bluetoothGatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT 连接成功，开始发现服务")
                    bluetoothGatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT 连接断开")
                    onDisconnected?.invoke()
                    gatt = null
                }
            }
        }

        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "服务发现失败: $status")
                return
            }

            val service = bluetoothGatt.getService(UUID.fromString(BleConstants.SERVICE_UUID))
            if (service == null) {
                Log.e(TAG, "未找到目标 Service")
                return
            }

            commandChar = service.getCharacteristic(UUID.fromString(BleConstants.COMMAND_UUID))
            notificationChar = service.getCharacteristic(UUID.fromString(BleConstants.NOTIFICATION_UUID))

            // 请求 MTU 247
            bluetoothGatt.requestMtu(BleConstants.REQUESTED_MTU)
        }

        override fun onMtuChanged(bluetoothGatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU 协商完成: $mtu, status=$status")

            // 启用通知
            notificationChar?.let { char ->
                val descriptor = char.getDescriptor(UUID.fromString(BleConstants.CLIENT_CONFIG_UUID))
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt.writeDescriptor(descriptor)
                    bluetoothGatt.setCharacteristicNotification(char, true)
                }
            }

            onConnected?.invoke()
        }

        override fun onCharacteristicChanged(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == UUID.fromString(BleConstants.NOTIFICATION_UUID)) {
                val fragment = characteristic.value ?: return
                Log.d(TAG, "收到通知分片, size=${fragment.size}")
                // Server 端分片发送，此处重组完整消息后回调
                val completed = fragmenter.reassemble(fragment)
                if (completed != null) {
                    onNotification?.invoke(completed)
                }
            }
        }

        override fun onCharacteristicWrite(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(TAG, "写入完成: status=$status")
            // 发送队列中下一片（分片消息必须串行发送）
            writeQueue.removeFirstOrNull()
            busy = false
            sendNextQueued()
        }
    }

    /**
     * 连接 Hub 的 GATT Server。
     *
     * @param deviceAddress 目标设备 MAC 地址
     */
    fun connect(deviceAddress: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "蓝牙未开启")
            return
        }

        val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
        gatt = device.connectGatt(context, false, gattCallback)
        Log.i(TAG, "正在连接设备: $deviceAddress")
    }

    /**
     * 写入数据到 Hub 的 command 特征值。
     * 数据先经 MessageFragmenter 分片（每片 ≤ 20 字节），逐片串行写入，
     * 保证在低 MTU 设备上也能完整送达。
     *
     * @param data 要写入的数据
     * @return true 表示写入请求已提交
     */
    fun write(data: ByteArray): Boolean {
        val currentGatt = gatt
        val char = commandChar

        if (currentGatt == null || char == null) {
            Log.w(TAG, "GATT 未连接或特征值未就绪")
            return false
        }

        val fragments = fragmenter.fragment(data)
        writeQueue.clear()
        writeQueue.addAll(fragments)
        sendNextQueued()
        return true
    }

    /** 串行发送队列中的下一片 */
    private fun sendNextQueued() {
        if (busy) return
        val currentGatt = gatt ?: return
        val char = commandChar ?: return
        val next = writeQueue.firstOrNull() ?: return
        busy = true
        try {
            char.value = next
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            currentGatt.writeCharacteristic(char)
        } catch (e: Exception) {
            Log.e(TAG, "写入分片异常", e)
            writeQueue.removeFirstOrNull()
            busy = false
            sendNextQueued()
        }
    }

    /**
     * 断开 GATT 连接。
     */
    fun disconnect() {
        writeQueue.clear()
        busy = false
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        commandChar = null
        notificationChar = null
        Log.i(TAG, "GATT Client 已断开")
    }

    /**
     * 是否已连接。
     */
    fun isConnected(): Boolean = gatt != null
}
