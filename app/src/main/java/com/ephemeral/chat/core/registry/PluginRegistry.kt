package com.ephemeral.chat.core.registry

import android.content.Context
import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.plugin.IPlugin
import com.ephemeral.chat.navigation.NavDestination
import java.util.LinkedList

/**
 * 插件注册中心——管理所有插件的生命周期与依赖关系。
 * 负责按拓扑排序启动插件，确保依赖先于被依赖者启动。
 * 所有公开方法线程安全（synchronized）。
 *
 * @param eventBus 事件总线实例
 * @param applicationContext 应用上下文，供插件获取系统服务
 */
class PluginRegistry(
    private val eventBus: EventBus,
    val applicationContext: Context,
) {
    /** 插件存储 */
    private val plugins = mutableMapOf<String, IPlugin>()

    /** 导航项存储 */
    private val navDestinations = mutableListOf<NavDestination>()

    /** 导航项 content 映射（route → Composable） */
    private val navContents = mutableMapOf<String, @androidx.compose.runtime.Composable () -> Unit>()

    /** 拓扑排序结果缓存 */
    private var sortedIds: List<String>? = null

    private val lock = Any()

    /**
     * 注册插件。
     * 注册前检查 dependencies 列表中所有依赖是否已注册，未注册则抛出 IllegalStateException。
     * 注册成功后立即调用 plugin.onInit()。
     */
    fun register(plugin: IPlugin) {
        synchronized(lock) {
            // 检查依赖
            for (dep in plugin.dependencies) {
                if (dep !in plugins) {
                    throw IllegalStateException(
                        "插件 ${plugin.pluginId} 依赖未注册的插件 $dep，请确保 $dep 先注册"
                    )
                }
            }
            // 检查重复注册
            if (plugin.pluginId in plugins) {
                throw IllegalStateException("插件 ${plugin.pluginId} 已注册，不可重复注册")
            }
            plugins[plugin.pluginId] = plugin
            plugin.onInit(this, eventBus)
            sortedIds = null // 清除排序缓存
        }
    }

    /**
     * 启动所有已注册插件。
     * 按拓扑排序（Kahn 算法）依次调用 onStart()。
     */
    fun startAll() {
        synchronized(lock) {
            val sorted = getSortedIds()
            for (id in sorted) {
                plugins[id]?.onStart()
            }
        }
    }

    /**
     * 停止所有插件。逆拓扑排序依次调用 onStop()。
     */
    fun stopAll() {
        synchronized(lock) {
            val sorted = getSortedIds()
            for (id in sorted.reversed()) {
                plugins[id]?.onStop()
            }
        }
    }

    /**
     * 销毁所有插件。逆拓扑排序依次调用 onDestroy()。
     */
    fun destroyAll() {
        synchronized(lock) {
            val sorted = getSortedIds()
            for (id in sorted.reversed()) {
                plugins[id]?.onDestroy()
            }
            plugins.clear()
            navDestinations.clear()
            navContents.clear()
            sortedIds = null
        }
    }

    /**
     * 注册导航项，同时绑定 content Composable。
     */
    fun registerNavDestination(
        dest: NavDestination,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        synchronized(lock) {
            navDestinations.add(dest)
            navContents[dest.route] = content
        }
    }

    /**
     * 获取所有已注册的导航项列表。
     */
    fun getNavDestinations(): List<NavDestination> {
        synchronized(lock) {
            return navDestinations.toList()
        }
    }

    /**
     * 获取导航项对应的 content Composable。
     */
    fun getNavContent(route: String): (@androidx.compose.runtime.Composable () -> Unit)? {
        synchronized(lock) {
            return navContents[route]
        }
    }

    /**
     * 泛型方法，获取指定 ID 的插件实例。
     * 用 as? 安全转型，类型不匹配返回 null。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : IPlugin> getPlugin(id: String): T? {
        synchronized(lock) {
            return plugins[id] as? T
        }
    }

    /**
     * 对已注册插件做拓扑排序（Kahn 算法）。
     * 检测到循环依赖则抛出 IllegalStateException。
     */
    private fun getSortedIds(): List<String> {
        sortedIds?.let { return it }

        val inDegree = mutableMapOf<String, Int>()
        val adjacency = mutableMapOf<String, MutableList<String>>()

        for ((id, plugin) in plugins) {
            inDegree[id] = plugin.dependencies.count { it in plugins }
            for (dep in plugin.dependencies) {
                if (dep in plugins) {
                    adjacency.getOrPut(dep) { mutableListOf() }.add(id)
                }
            }
        }

        val queue = LinkedList<String>()
        for ((id, degree) in inDegree) {
            if (degree == 0) queue.add(id)
        }

        val result = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val current: String = queue.pollFirst() ?: break
            result.add(current)
            for (neighbor in adjacency[current] ?: emptyList()) {
                inDegree[neighbor] = (inDegree[neighbor] ?: 0) - 1
                if (inDegree[neighbor] == 0) {
                    queue.add(neighbor)
                }
            }
        }

        if (result.size != plugins.size) {
            val unresolved = plugins.keys - result.toSet()
            throw IllegalStateException("检测到循环依赖，涉及插件: $unresolved")
        }

        sortedIds = result
        return result
    }
}
