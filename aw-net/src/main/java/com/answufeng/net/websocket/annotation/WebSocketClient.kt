package com.answufeng.net.websocket.annotation

import javax.inject.Qualifier

/**
 * Hilt 限定符注解，用于标记 WebSocket 专用的 [okhttp3.OkHttpClient] 依赖。
 *
 * 当项目层需要为 WebSocket 使用不同于 HTTP 请求的 OkHttpClient 配置时，
 * 可使用此注解提供自定义实例。若未提供，将使用默认的 OkHttpClient。
 *
 * 示例：
 * ```kotlin
 * @Provides @WebSocketClient
 * fun provideWebSocketOkHttpClient(): OkHttpClient {
 *     return OkHttpClient.Builder()
 *         .pingInterval(30, TimeUnit.SECONDS)
 *         .build()
 * }
 * ```
 * @since 1.0.0
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WebSocketClient
