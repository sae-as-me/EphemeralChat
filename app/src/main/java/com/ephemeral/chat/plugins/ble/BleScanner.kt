package com.ephemeral.chat.plugins.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

/**
 * BLE 扫描管理器。
 * 职责：扫描 BLE 广播，过滤匹配的 Service UUID，回调匹配结果。
 */
class BleScanner(private val context: Context) {

    private val TAG = "BleScanner"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private var scanTimeoutRunnable: Runnable? = null

    /**
     * 启动 BLE 扫描。
     *
     * @param targetHashes 目标邀请码哈希列表（9 邻域）
     * @param onMatched 匹配成功回调，参数为设备地址和 groupIdShort
     */
    fun start(
        targetHashes: List<ByteArray>,
        onMatched: (deviceAddress: String, groupIdShort: ByteArray) -> Unit,
    ) {
        if (isScanning) {
            Log.w(TAG, "已在扫描中，先停止旧扫描")
            stop()
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "蓝牙未开启或设备不支持蓝牙")
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "无法获取 BluetoothLeScanner")
            return
        }

        // 扫描过滤器：匹配 Service UUID
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(BleConstants.ADVERTISE_SERVICE_UUID))
            .build()

        // 扫描设置：低延迟模式
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val scanRecord = result.scanRecord ?: return
                val serviceData = scanRecord.getServiceData(
                    ParcelUuid.fromString(BleConstants.ADVERTISE_SERVICE_UUID)
                ) ?: return

                if (serviceData.size < 8) return

                // 前 4 字节为 codeHash
                val codeHash = serviceData.copyOfRange(0, 4)
                // 后 4 字节为 groupIdShort
                val groupIdShort = serviceData.copyOfRange(4, 8)

                // 匹配目标哈希
                for (target in targetHashes) {
                    if (codeHash.contentEquals(target)) {
                        Log.i(TAG, "匹配到目标设备: ${result.device.address}")
                        onMatched(result.device.address, groupIdShort)
                        return
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE 扫描失败，错误码: $errorCode")
                isScanning = false
            }
        }

        scanCallback = callback
        scanner.startScan(listOf(filter), settings, callback)
        isScanning = true
        Log.i(TAG, "BLE 扫描已启动，目标哈希数: ${targetHashes.size}")

        // 30s 超时自动停止
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        scanTimeoutRunnable = Runnable {
            if (isScanning) {
                Log.i(TAG, "BLE 扫描超时（30s），自动停止")
                stop()
            }
        }
        handler.postDelayed(scanTimeoutRunnable!!, 30_000L)
    }

    /**
     * 停止 BLE 扫描。
     */
    fun stop() {
        if (isScanning && bluetoothAdapter?.isEnabled == true) {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            scanCallback?.let { scanner?.stopScan(it) }
        }
        isScanning = false
        scanCallback = null

        // 移除超时任务
        scanTimeoutRunnable?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        scanTimeoutRunnable = null

        Log.i(TAG, "BLE 扫描已停止")
    }

    /**
     * 是否正在扫描。
     */
    fun isScanning(): Boolean = isScanning
}
