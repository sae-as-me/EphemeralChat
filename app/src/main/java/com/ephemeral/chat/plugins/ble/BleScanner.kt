package com.ephemeral.chat.plugins.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BLE 扫描管理器。
 * 职责：扫描 BLE 广播，过滤匹配的 Service UUID，回调匹配结果。
 * 修复：增加权限/蓝牙状态检查与错误回调；扫描需在主线程执行；匹配后自动停止。
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
     * @param onMatched 匹配成功回调（设备地址 + groupIdShort），匹配后自动停止扫描
     * @param onError 错误回调（蓝牙未开启 / 无权限 / 扫描失败 / 超时）
     */
    fun start(
        targetHashes: List<ByteArray>,
        onMatched: (deviceAddress: String, groupIdShort: ByteArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (isScanning) {
            Log.w(TAG, "已在扫描中，先停止旧扫描")
            stop()
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        // 检查蓝牙是否开启
        if (bluetoothAdapter == null) {
            Log.e(TAG, "设备不支持蓝牙")
            onError("设备不支持蓝牙")
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "蓝牙未开启")
            onError("请先开启蓝牙")
            return
        }

        // 检查扫描权限（Android 12+ 需要 BLUETOOTH_SCAN，旧版本需要 ACCESS_FINE_LOCATION）
        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(context, scanPermission) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "缺少扫描权限: $scanPermission")
            onError("缺少蓝牙扫描权限，请在系统设置中开启")
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "无法获取 BluetoothLeScanner")
            onError("无法获取蓝牙扫描器")
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
                        stop()
                        onMatched(result.device.address, groupIdShort)
                        return
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE 扫描失败，错误码: $errorCode")
                isScanning = false
                onError("蓝牙扫描失败（错误码 $errorCode）")
            }
        }

        scanCallback = callback
        // startScan 必须在主线程调用
        Handler(Looper.getMainLooper()).post {
            try {
                scanner.startScan(listOf(filter), settings, callback)
                isScanning = true
                Log.i(TAG, "BLE 扫描已启动，目标哈希数: ${targetHashes.size}")

                // 30s 超时自动停止并通知
                scanTimeoutRunnable = Runnable {
                    if (isScanning) {
                        Log.i(TAG, "BLE 扫描超时（30s），自动停止")
                        stop()
                        onError("未发现附近群聊，请确认对方已创建群聊且蓝牙已开启")
                    }
                }
                Handler(Looper.getMainLooper()).postDelayed(scanTimeoutRunnable!!, 30_000L)
            } catch (e: SecurityException) {
                Log.e(TAG, "启动扫描权限被拒绝", e)
                onError("缺少蓝牙扫描权限，请在权限设置中开启")
            } catch (e: Exception) {
                Log.e(TAG, "启动扫描异常", e)
                onError("蓝牙扫描启动失败: ${e.message}")
            }
        }
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
            Handler(Looper.getMainLooper()).removeCallbacks(it)
        }
        scanTimeoutRunnable = null

        Log.i(TAG, "BLE 扫描已停止")
    }

    /**
     * 是否正在扫描。
     */
    fun isScanning(): Boolean = isScanning
}