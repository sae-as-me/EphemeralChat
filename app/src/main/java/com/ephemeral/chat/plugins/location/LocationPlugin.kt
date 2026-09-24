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
     * 重要：本方法保证一定会回调 callback（无论是否拿到精确定位）：
     * - 有最后已知位置 → 立即回调
     * - 单次定位 10s 超时 → 回退到最后已知位置
     * - 无任何位置 → 回调默认坐标 (0,0) 并打日志
     * 这样可确保创建/加入群聊流程不被定位阻塞（部分国产 ROM 无 lastKnown 且室内无 GPS fix）。
     *
     * @param callback 回调函数，参数为经纬度
     */
    fun getCurrentLocation(callback: (Double, Double) -> Unit) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "无定位权限，使用兜底位置 (0,0)")
            callback(0.0, 0.0)
            return
        }

        try {
            val lastKnown = getLastKnownLocationOrNull()
            if (lastKnown != null) {
                Log.d(TAG, "使用最后已知位置: ${lastKnown.latitude}, ${lastKnown.longitude}")
                callback(lastKnown.latitude, lastKnown.longitude)
                return
            }

            // 无缓存位置：请求单次更新，超时后兜底回调
            requestSingleLocationWithFallback(callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "获取位置时权限被拒绝，使用兜底位置 (0,0)", e)
            callback(0.0, 0.0)
        }
    }

    /**
     * 获取最后已知位置（GPS 优先，回退 NETWORK）。可能为 null。
     */
    private fun getLastKnownLocationOrNull(): Location? {
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            Log.e(TAG, "读取最后已知位置权限被拒绝", e)
            null
        }
    }

    /**
     * 请求单次位置更新，超时 10s 后兜底回调（保证不会永久阻塞）。
     */
    private fun requestSingleLocationWithFallback(callback: (Double, Double) -> Unit) {
        try {
            var done = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (done) return
                    done = true
                    Log.d(TAG, "单次定位成功: ${location.latitude}, ${location.longitude}")
                    callback(location.latitude, location.longitude)
                    try { locationManager.removeUpdates(this) } catch (e: Exception) {}
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (gpsEnabled) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
            } else if (networkEnabled) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
            } else {
                // 定位开关关闭：立即兜底，不等 10s
                Log.w(TAG, "定位服务未开启，使用兜底位置 (0,0)")
                done = true
                callback(0.0, 0.0)
                return
            }

            // 10s 超时兜底：未定位成功则回退最后已知位置，再没有则默认坐标
            scope.launch {
                delay(10_000L)
                if (done) return@launch
                done = true
                try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                val fallback = getLastKnownLocationOrNull()
                if (fallback != null) {
                    Log.w(TAG, "单次定位超时，回退最后已知位置: ${fallback.latitude}, ${fallback.longitude}")
                    callback(fallback.latitude, fallback.longitude)
                } else {
                    Log.w(TAG, "单次定位超时且无缓存位置，使用兜底坐标 (0,0)")
                    callback(0.0, 0.0)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "请求单次定位权限被拒绝，使用兜底位置 (0,0)", e)
            callback(0.0, 0.0)
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
