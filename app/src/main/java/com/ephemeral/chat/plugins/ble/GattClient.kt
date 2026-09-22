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
                Log.d(TAG, "收到通知, size=${characteristic.value?.size ?: 0}")
                onNotification?.invoke(characteristic.value ?: ByteArray(0))
            }
        }

        override fun onCharacteristicWrite(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(TAG, "写入完成: status=$status")
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

        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return currentGatt.writeCharacteristic(char)
    }

    /**
     * 断开 GATT 连接。
     */
    fun disconnect() {
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
