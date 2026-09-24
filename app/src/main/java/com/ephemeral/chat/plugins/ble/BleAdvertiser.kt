package com.ephemeral.chat.plugins.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BLE 广播管理器。
 * 职责：启动/停止 BLE 广播，广播内容包含 code_hash + group_id_short。
 * 修复：增加权限检查与错误回调，广播失败时通知 UI。
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
     * @param onError 错误回调（蓝牙未开启 / 无权限 / 广播失败）
     */
    fun start(codeHash: ByteArray, groupIdShort: ByteArray, onError: (String) -> Unit) {
        if (isAdvertising) {
            Log.w(TAG, "已在广播中，先停止旧广播")
            stop()
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "蓝牙未开启或设备不支持蓝牙")
            onError("请先开启蓝牙")
            return
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            Log.e(TAG, "设备不支持 BLE 广播")
            onError("设备不支持 BLE 广播")
            return
        }

        // 检查广播权限（Android 12+ 需要 BLUETOOTH_ADVERTISE）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "缺少广播权限 BLUETOOTH_ADVERTISE")
                onError("缺少蓝牙广播权限，请在系统设置中开启")
                return
            }
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "无法获取 BluetoothLeAdvertiser")
            onError("无法获取蓝牙广播器")
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
                val msg = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "广播已在运行"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "广播数据过大"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "设备不支持广播"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "广播内部错误"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播器数量超限"
                    else -> "广播失败（错误码 $errorCode）"
                }
                onError(msg)
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
     * 是否正在广播。
     */
    fun isAdvertising(): Boolean = isAdvertising
}