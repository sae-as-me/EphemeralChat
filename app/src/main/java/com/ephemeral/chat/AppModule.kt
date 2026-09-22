package com.ephemeral.chat

import com.ephemeral.chat.core.eventbus.EventBus
import com.ephemeral.chat.core.registry.PluginRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块——提供全局单例。
 * 将 Application 中创建的 EventBus 和 PluginRegistry 通过 Hilt 暴露给 ViewModel。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEventBus(): EventBus = EventBus()

    @Provides
    @Singleton
    fun providePluginRegistry(
        eventBus: EventBus,
        @ApplicationContext context: android.content.Context,
    ): PluginRegistry = PluginRegistry(eventBus, context)
}
