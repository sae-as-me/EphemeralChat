package com.ephemeral.chat.plugins.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

/**
 * BLE 广播管理器。
 * 职责：启动/停止 BLE 广播，广播内容包含 code_hash + group_id_short。
 */
class BleAdvertiser(private val context: Context) {

    private val TAG = "BleAdvertiser"
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var isAdvertising = false

    /**
     * 启动 BLE 广播。
     *
     * @param codeHash 4 字节邀请码哈希
     * @param groupIdShort 4 字节群组 ID 短码
     */
    fun start(codeHash: ByteArray, groupIdShort: ByteArray) {
        if (isAdvertising) {
            Log.w(TAG, "已在广播中，先停止旧广播")
            stop()
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "蓝牙未开启或设备不支持蓝牙")
            return
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            Log.e(TAG, "设备不支持 BLE 广播")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "无法获取 BluetoothLeAdvertiser")
            return
        }

        // 广播设置：低延迟模式（Android 默认间隔约 100ms）
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        // 广播数据：Service UUID + Service Data
        val payload = codeHash + groupIdShort
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid.fromString(BleConstants.ADVERTISE_SERVICE_UUID))
            .addServiceData(ParcelUuid.fromString(BleConstants.ADVERTISE_SERVICE_UUID), payload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "BLE 广播启动成功")
                isAdvertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE 广播启动失败，错误码: $errorCode")
                isAdvertising = false
            }
        }

        advertiseCallback = callback
        advertiser?.startAdvertising(settings, advertiseData, callback)
    }

    /**
     * 停止 BLE 广播。
     */
    fun stop() {
        if (advertiser != null && advertiseCallback != null) {
            advertiser?.stopAdvertising(advertiseCallback)
            Log.i(TAG, "BLE 广播已停止")
        }
        isAdvertising = false
        advertiseCallback = null
    }

    /**
     * 切换为低功耗模式（1s 间隔），群聊建立后调用。
     */
    fun switchToLowPower(codeHash: ByteArray, groupIdShort: ByteArray) {
        stop()

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return
        advertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)
            .build()

        val payload = codeHash + groupIdShort
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid.fromString(BleConstants.ADVERTISE_SERVICE_UUID))
            .addServiceData(ParcelUuid.fromString(BleConstants.ADVERTISE_SERVICE_UUID), payload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "BLE 低功耗广播启动成功")
                isAdvertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE 低功耗广播启动失败，错误码: $errorCode")
                isAdvertising = false
            }
        }

        advertiseCallback = callback
        advertiser?.startAdvertising(settings, advertiseData, callback)
    }

    /**
     * 是否正在广播。
     */
    fun isAdvertising(): Boolean = isAdvertising
}
