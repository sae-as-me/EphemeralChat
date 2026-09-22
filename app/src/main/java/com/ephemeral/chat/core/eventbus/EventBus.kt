package com.ephemeral.chat.core.eventbus

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 事件总线——基于 SharedFlow 的发布/订阅通信。
 * 插件间零耦合通信通道，所有事件通过 channel 名分区。
 * 每个通道独立配置：replay=0, extraBufferCapacity=64, DROP_OLDEST。
 */
class EventBus {

    private val channels = mutableMapOf<String, MutableSharedFlow<AppEvent>>()
    private val mutex = Mutex()

    /**
     * 向指定频道发布事件。
     * suspend 函数，调用方须在协程上下文中调用。
     */
    suspend fun publish(channel: String, event: AppEvent) {
        getOrCreateChannel(channel).emit(event)
    }

    /**
     * 订阅指定频道的事件流。
     * 返回 Flow，调用方用 collect 消费事件。
     */
    fun subscribe(channel: String): Flow<AppEvent> {
        return getOrCreateChannel(channel).asSharedFlow()
    }

    /**
     * 清除所有频道（用于解散时清理）。
     */
    suspend fun clearAll() {
        mutex.withLock {
            channels.clear()
        }
    }

    /**
     * 获取或创建指定频道的 SharedFlow。
     * 线程安全，使用 Mutex 保护 channels Map。
     */
    private fun getOrCreateChannel(channel: String): MutableSharedFlow<AppEvent> {
        return synchronized(channels) {
            channels.getOrPut(channel) {
                MutableSharedFlow(
                    replay = 0,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }
        }
    }
}
