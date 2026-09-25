package com.ephemeral.chat.plugins.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.ephemeral.chat.core.eventbus.AppEvent
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * BLE 插件——整合 Advertiser + Scanner + GattServer/GattClient。
 * 依赖：LocationPlugin（定位关闭时须停止 BLE）
 */
class BlePlugin : IPlugin {
    override val pluginId = "ble"
    override val dependencies = listOf("location")

    private val TAG = "BlePlugin"
    private lateinit var context: Context
    private lateinit var eventBus: EventBus
    private var adapter: BluetoothAdapter? = null

    private var advertiser: BleAdvertiser? = null
    private var scanner: BleScanner? = null
    private var gattServer: GattServer? = null
    private var gattClient: GattClient? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        this.context = registry.applicationContext
        this.eventBus = eventBus
        this.adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

        advertiser = BleAdvertiser(context)
        scanner = BleScanner(context)

        // 订阅定位状态，定位关闭时停止 BLE
        scope.launch {
            eventBus.subscribe("location_state").collect { event ->
                if (event is AppEvent.LocationStateChanged && !event.enabled) {
                    Log.i(TAG, "定位已关闭，停止所有 BLE 活动")
                    stopAdvertising()
                    stopScanning()
                }
            }
        }

        Log.i(TAG, "BLE 插件初始化完成")
    }

    override fun onStart() {
        Log.i(TAG, "BLE 插件已启动")
    }

    override fun onStop() {
        stopAdvertising()
        stopScanning()
        stopGattServer()
        disconnectClient()
        Log.i(TAG, "BLE 插件已停止")
    }

    override fun onDestroy() {
        scope.cancel()
        Log.i(TAG, "BLE 插件已销毁")
    }

    // ---- 广播 ----

    fun startAdvertising(codeHash: ByteArray, codeOnlyHash: ByteArray, groupIdShort: ByteArray, onError: (String) -> Unit) {
        advertiser?.start(codeHash, codeOnlyHash, groupIdShort, onError)
    }

    fun stopAdvertising() {
        advertiser?.stop()
    }

    // ---- 扫描 ----

    fun startScanning(
        targetHashes: List<ByteArray>,
        codeOnlyHash: ByteArray,
        onMatched: (deviceAddress: String, groupIdShort: ByteArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        scanner?.start(targetHashes, codeOnlyHash, onMatched, onError)
    }

    fun stopScanning() {
        scanner?.stop()
    }

    // ---- GATT Server（Hub 角色）----

    fun startGattServer(onMessage: (ByteArray) -> Unit) {
        stopGattServer()
        gattServer = GattServer(context)
        gattServer?.onMessageReceived = onMessage
        gattServer?.start()
    }

    fun stopGattServer() {
        gattServer?.stop()
        gattServer = null
    }

    fun broadcastToClients(data: ByteArray) {
        gattServer?.broadcast(data)
    }

    // ---- GATT Client（Client 角色）----

    fun connectAsClient(deviceAddress: String, onNotification: (ByteArray) -> Unit) {
        disconnectClient()
        gattClient = GattClient(context)
        gattClient?.onNotification = onNotification
        gattClient?.connect(deviceAddress)
    }

    fun disconnectClient() {
        gattClient?.disconnect()
        gattClient = null
    }

    fun writeToHub(data: ByteArray): Boolean {
        return gattClient?.write(data) ?: false
    }

    // ---- 工具方法 ----

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Client 是否已就绪（GATT 连接 + 命令特征 + 通知订阅全部就绪）。
     */
    fun isClientReady(): Boolean = gattClient?.isReady() == true

    /**
     * 设备是否支持 BLE 广播（创建群聊前预检）。
     * 注意：蓝牙未开启时 isMultipleAdvertisementSupported 返回值不确定，
     * 部分设备会返回 false 导致误判。因此蓝牙未开启时跳过预检返回 true，
     * 让后续广播启动流程自然处理（启动失败时会报具体错误）。
     */
    fun canAdvertise(): Boolean {
        val adapter = this.adapter ?: return true // adapter 为 null 时不阻断，让后续流程报错
        if (!adapter.isEnabled) return true // 蓝牙未开启时不预检，避免误判
        return adapter.isMultipleAdvertisementSupported
    }
}
