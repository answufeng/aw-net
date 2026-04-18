package com.answufeng.net.websocket.di

import com.answufeng.net.websocket.WebSocketLogger
import com.answufeng.net.websocket.WebSocketManager
import com.answufeng.net.websocket.WebSocketManager
import com.answufeng.net.websocket.annotation.WebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * WebSocket 模块配置
 * 支持自定义 OkHttpClient 和日志实现
 * @since 1.0.0
 */
@Module
@InstallIn(SingletonComponent::class)
object WebSocketModule {

    private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10L
    private const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
    private const val DEFAULT_WRITE_TIMEOUT_SECONDS = 60L
    private const val DEFAULT_PING_INTERVAL_SECONDS = 30L

    /**
     * 提供 WebSocketManager 实例
     * @param okHttpClient 可选的自定义 OkHttpClient
     * @param logger 可选的自定义日志实现
     * @since 1.0.0
 */
    @Provides
    @Singleton
    fun provideWebSocketManager(
        @WebSocketClient okHttpClient: Optional<OkHttpClient>,
        logger: Optional<WebSocketLogger>
    ): WebSocketManager {
        val client = okHttpClient.orElseGet {
            OkHttpClient.Builder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pingInterval(DEFAULT_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .build()
        }
        val externalLogger = logger.orElse(null)
        return WebSocketManager(client, externalLogger)
    }
}