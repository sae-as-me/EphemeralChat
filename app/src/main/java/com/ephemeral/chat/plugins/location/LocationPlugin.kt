package com.ephemeral.chat.plugins.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.ephemeral.chat.core.eventbus.AppEvent
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.core.registry.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 定位服务插件。
 * 职责：
 * 1. 获取当前 GPS 位置（单次）
 * 2. 监测定位开关状态（每 15s 轮询）
 * 3. 定位状态变化时发布 LocationStateChanged 事件
 * 4. 依赖：无（底层插件）
 */
class LocationPlugin : IPlugin {
    override val pluginId = "location"
    override val dependencies = emptyList<String>()

    private val TAG = "LocationPlugin"

    private lateinit var context: Context
    private lateinit var eventBus: EventBus
    private lateinit var locationManager: LocationManager

    /** 协程作用域，绑定到插件生命周期 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 定位状态监测任务 */
    private var monitorJob: Job? = null

    /** 上一次定位状态，用于检测变化 */
    private var lastLocationEnabled: Boolean = false

    override fun onInit(registry: PluginRegistry, eventBus: EventBus) {
        this.context = registry.applicationContext
        this.eventBus = eventBus
        this.locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        Log.i(TAG, "定位插件初始化完成")
    }

    override fun onStart() {
        startLocationMonitoring()
        Log.i(TAG, "定位插件已启动")
    }

    override fun onStop() {
        monitorJob?.cancel()
        monitorJob = null
        Log.i(TAG, "定位插件已停止")
    }

    override fun onDestroy() {
        scope.cancel()
        Log.i(TAG, "定位插件已销毁")
    }

    /**
     * 启动定位状态监测协程（15s 间隔轮询）。
     * 状态变化时通过 EventBus 发布事件。
     */
    private fun startLocationMonitoring() {
        lastLocationEnabled = isLocationEnabled()
        monitorJob = scope.launch {
            while (true) {
                delay(15_000L)
                val currentEnabled = isLocationEnabled()
                if (currentEnabled != lastLocationEnabled) {
                    Log.i(TAG, "定位状态变化: $lastLocationEnabled → $currentEnabled")
                    lastLocationEnabled = currentEnabled
                    eventBus.publish("location_state", AppEvent.LocationStateChanged(currentEnabled))
                }
            }
        }
    }

    /**
     * 获取当前 GPS 位置（单次）。
     * 优先用 getLastKnownLocation(GPS_PROVIDER)，为 null 则 requestSingleUpdate。
     * 回调在主线程。
     *
     * @param callback 回调函数，参数为经纬度
     */
    fun getCurrentLocation(callback: (Double, Double) -> Unit) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "无定位权限，无法获取位置")
            return
        }

        try {
            // 优先使用 GPS_PROVIDER 的最后已知位置
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnown != null) {
                Log.d(TAG, "使用最后已知位置: ${lastKnown.latitude}, ${lastKnown.longitude}")
                callback(lastKnown.latitude, lastKnown.longitude)
                return
            }

            // 回退到 NETWORK_PROVIDER
            val networkKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (networkKnown != null) {
                Log.d(TAG, "使用网络位置: ${networkKnown.latitude}, ${networkKnown.longitude}")
                callback(networkKnown.latitude, networkKnown.longitude)
                return
            }

            // 最后手段：请求单次更新
            requestSingleUpdate(callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "获取位置时权限被拒绝", e)
        }
    }

    /**
     * 请求单次位置更新。超时 10s 后回退到 NETWORK_PROVIDER。
     */
    private fun requestSingleUpdate(callback: (Double, Double) -> Unit) {
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    Log.d(TAG, "单次定位成功: ${location.latitude}, ${location.longitude}")
                    callback(location.latitude, location.longitude)
                    locationManager.removeUpdates(this)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
            }

            // 10s 超时移除
            scope.launch {
                delay(10_000L)
                locationManager.removeUpdates(listener)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "请求单次定位权限被拒绝", e)
        }
    }

    /**
     * 判断定位是否开启。
     * GPS_PROVIDER 或 NETWORK_PROVIDER 任一可用即 true。
     */
    fun isLocationEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "检查定位状态失败", e)
            false
        }
    }

    /**
     * 检查是否拥有定位权限。
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
