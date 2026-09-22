package com.ephemeral.chat.core.plugin

import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.registry.PluginRegistry

/**
 * 插件接口——所有功能模块的统一抽象。
 * 生命周期顺序：onInit → onStart → onStop → onDestroy。
 * onInit 在注册时立即调用，onStart/onStop/onDestroy 由 PluginRegistry 统一调度。
 */
interface IPlugin {
    /** 插件唯一标识符 */
    val pluginId: String

    /** 依赖的插件 ID 列表，注册前会检查这些依赖是否已注册 */
    val dependencies: List<String>
        get() = emptyList()

    /**
     * 初始化——注册时立即调用。
     * 可获取 registry 中的其他插件实例和 eventBus 进行事件订阅。
     * @param registry 插件注册中心
     * @param eventBus 事件总线
     */
    fun onInit(registry: PluginRegistry, eventBus: EventBus)

    /**
     * 启动——拓扑排序后按依赖顺序调用。
     * 可在此启动定时任务、BLE 广播等。
     */
    fun onStart()

    /**
     * 停止——逆拓扑排序调用。
     * 停止定时任务、BLE 活动等。
     */
    fun onStop()

    /**
     * 销毁——逆拓扑排序调用。
     * 释放所有资源，清空状态。
     */
    fun onDestroy()
}
