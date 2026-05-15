package com.answufeng.net.websocket

/**
 * 以构建器风格组装 [WebSocketManager.Config]，减少多参构造调用。
 *
 * ```kotlin
 * val config = webSocketConfig {
 *     heartbeatMessage = "ping"
 *     heartbeatResponseMessage = "pong"
 *     maxReconnectAttempts = 5
 * }
 * ```
 */
inline fun webSocketConfig(block: WebSocketConfigBuilder.() -> Unit): WebSocketManager.Config {
    val b = WebSocketConfigBuilder()
    b.block()
    return b.build()
}

/**
 * [WebSocketManager.Config] 的构建器，各属性含义与 [WebSocketManager.Config] 一致。
 */
class WebSocketConfigBuilder {
    var heartbeatIntervalMs: Long = 30_000L
    var heartbeatTimeoutMs: Long = 60_000L
    var heartbeatMessage: String = "ping"
    var enableHeartbeat: Boolean = true
    var wsLogLevel: WebSocketLogLevel = WebSocketLogLevel.BASIC
    var callbackOnMainThread: Boolean = true
    var messageQueueCapacity: Int = 100
    var dropOldestWhenQueueFull: Boolean = false
    var enableMessageReplay: Boolean = true
    var connectTimeout: Long = 10L
    var readTimeout: Long = 0L
    var writeTimeout: Long = 10L
    var maxReconnectAttempts: Int = 0
    var reconnectBaseDelayMs: Long = 1_000L
    var reconnectMaxDelayMs: Long = 30_000L
    var headers: Map<String, String> = emptyMap()
    var queryParameters: Map<String, String> = emptyMap()
    var heartbeatResponseMessage: String? = null
    var reconnectStrategy: ReconnectStrategy? = null

    fun build(): WebSocketManager.Config =
        WebSocketManager.Config(
            heartbeatIntervalMs = heartbeatIntervalMs,
            heartbeatTimeoutMs = heartbeatTimeoutMs,
            heartbeatMessage = heartbeatMessage,
            enableHeartbeat = enableHeartbeat,
            wsLogLevel = wsLogLevel,
            callbackOnMainThread = callbackOnMainThread,
            messageQueueCapacity = messageQueueCapacity,
            dropOldestWhenQueueFull = dropOldestWhenQueueFull,
            enableMessageReplay = enableMessageReplay,
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            writeTimeout = writeTimeout,
            maxReconnectAttempts = maxReconnectAttempts,
            reconnectBaseDelayMs = reconnectBaseDelayMs,
            reconnectMaxDelayMs = reconnectMaxDelayMs,
            headers = headers,
            queryParameters = queryParameters,
            heartbeatResponseMessage = heartbeatResponseMessage,
            reconnectStrategy = reconnectStrategy,
        )
}
