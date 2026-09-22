package com.ephemeral.chat.plugins.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * GATT Server 端（Hub 角色）。
 * 职责：提供 GATT Service 供 Client 连接，接收 Client 写入，向 Client 发送通知。
 */
class GattServer(private val context: Context) {

    private val TAG = "GattServer"
    private var server: BluetoothGattServer? = null
    private var service: BluetoothGattService? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var notificationCharacteristic: BluetoothGattCharacteristic? = null

    /** 已连接设备列表，线程安全 */
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    /** 消息接收回调 */
    var onMessageReceived: ((ByteArray) -> Unit)? = null

    /**
     * 启动 GATT Server。
     * 添加 GATT Service（command + notification 两个特征值），设置回调。
     */
    fun start() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Log.e(TAG, "无法获取 BluetoothManager")
            return
        }

        server = bluetoothManager.openGattServer(context, gattCallback)
        if (server == null) {
            Log.e(TAG, "无法打开 GATT Server")
            return
        }

        // 创建 command 特征值（Write）
        commandCharacteristic = BluetoothGattCharacteristic(
            UUID.fromString(BleConstants.COMMAND_UUID),
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        ).apply {
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        // 创建 notification 特征值（Notify + Read）
        notificationCharacteristic = BluetoothGattCharacteristic(
            UUID.fromString(BleConstants.NOTIFICATION_UUID),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )

        // 为 notification 添加客户端配置描述符
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString(BleConstants.CLIENT_CONFIG_UUID),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        ).apply {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        notificationCharacteristic?.addDescriptor(descriptor)

        // 创建 Service
        service = BluetoothGattService(
            UUID.fromString(BleConstants.SERVICE_UUID),
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        ).apply {
            addCharacteristic(commandCharacteristic)
            addCharacteristic(notificationCharacteristic)
        }

        server?.addService(service)
        Log.i(TAG, "GATT Server 已启动")
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices[device.address] = device
                    Log.i(TAG, "设备连接: ${device.address}, 当前连接数: ${connectedDevices.size}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device.address)
                    Log.i(TAG, "设备断开: ${device.address}, 当前连接数: ${connectedDevices.size}")
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            Log.d(TAG, "收到写入请求: ${device.address}, offset=$offset, size=${value.size}")

            if (offset > 0 && offset + value.size > characteristic.value?.size ?: 0) {
                // 分片写入场景：拼接已有数据
                val existing = characteristic.value ?: ByteArray(0)
                val combined = ByteArray(existing.size + value.size)
                System.arraycopy(existing, 0, combined, 0, existing.size)
                System.arraycopy(value, 0, combined, existing.size, value.size)
                characteristic.value = combined
            } else {
                characteristic.value = value
            }

            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0))
            }

            // 回调消息
            onMessageReceived?.invoke(value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            Log.d(TAG, "描述符写入: ${device.address}")
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0))
            }
        }
    }

    /**
     * 向所有已连接 Client 发送通知。
     *
     * @param data 要广播的数据
     */
    fun broadcast(data: ByteArray) {
        val notifChar = notificationCharacteristic ?: return
        notifChar.value = data

        for ((_, device) in connectedDevices) {
            server?.notifyCharacteristicChanged(device, notifChar, false)
            Log.d(TAG, "已向 ${device.address} 发送通知")
        }
    }

    /**
     * 关闭 GATT Server，清除所有连接。
     */
    fun stop() {
        connectedDevices.clear()
        server?.clearServices()
        server?.close()
        server = null
        Log.i(TAG, "GATT Server 已关闭")
    }

    /**
     * 获取已连接设备数量。
     */
    fun getConnectedCount(): Int = connectedDevices.size
}
