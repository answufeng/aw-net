package com.answufeng.net.websocket

import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket 多连接管理器，支持同时维护多条 WebSocket 连接。
 *
 * ### 核心能力
 * - **多连接管理**：通过 `connectionId` 标识和管理多条独立的 WebSocket 连接
 * - **自动重连**：断线后指数退避重连（可配置最大重连次数）
 * - **应用层心跳**：定时发送自定义心跳消息，保持长连接
 * - **离线消息队列**：断连期间缓存待发送消息，重连后自动补发
 * - **回调线程控制**：回调默认分发到主线程，可配置为调用线程
 *
 * ### 线程安全
 * - 连接池使用 [ConcurrentHashMap]，支持多线程并发操作
 * - 每条连接内部状态由 [WebSocketClientImpl] 管理，回调由 Handler 串行分发
 *
 * ### 基本用法
 * ```kotlin
 * val manager = WebSocketManager(okHttpClient)
 *
 * // 使用默认连接
 * manager.connectDefault(
 *     url = "wss://echo.websocket.org",
 *     config = WebSocketManager.Config(enableHeartbeat = true),
 *     listener = object : WebSocketManager.WebSocketListener {
 *         override fun onMessage(connectionId: String, text: String) {
 *             Log.d("WS", "收到消息: $text")
 *         }
 *         override fun onFailure(connectionId: String, throwable: Throwable) {
 *             Log.e("WS", "连接失败", throwable)
 *         }
 *     }
 * )
 *
 * // 发送消息
 * manager.sendText("Hello")
 *
 * // 断开
 * manager.disconnectDefault(permanent = true)
 * ```
 *
 * ### 多连接用法
 * ```kotlin
 * manager.connect("chat", chatUrl, chatConfig, chatListener)
 * manager.connect("push", pushUrl, pushConfig, pushListener)
 * manager.sendMessage("chat", "hello")
 * manager.disconnect("push", permanent = false)
 * ```
 *
 * @see Config 连接配置（超时、心跳、重连策略、消息队列）
 * @see WebSocketListener 事件回调接口
 * @since 1.0.0
 */class WebSocketManager(
    private val okHttpClient: OkHttpClient
) : IWebSocketManager {

    companion object {
        private const val DEFAULT_CONNECTION_ID = "default_ws"
    }

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private val connections = ConcurrentHashMap<String, WebSocketClientImpl>()

    override fun connect(
        connectionId: String,
        url: String,
        config: Config,
        listener: WebSocketListener
    ) {
        connections[connectionId]?.disconnect(permanent = true)
        val client = WebSocketClientImpl(okHttpClient, url, config, connectionId, listener)
        connections[connectionId] = client
        client.connect()
    }

    override fun disconnect(connectionId: String, permanent: Boolean) {
        connections[connectionId]?.disconnect(permanent)
        if (permanent) {
            connections.remove(connectionId)
        }
    }

    override fun disconnectAll() {
        connections.values.forEach { it.disconnect(true) }
        connections.clear()
    }

    override fun reconnect(connectionId: String): Boolean {
        return connections[connectionId]?.reconnect() ?: false
    }

    override fun sendMessage(connectionId: String, text: String): Boolean {
        return connections[connectionId]?.sendMessage(text) ?: false
    }

    override fun sendMessage(connectionId: String, bytes: ByteArray): Boolean {
        return connections[connectionId]?.sendMessage(bytes) ?: false
    }

    override fun isConnected(connectionId: String): Boolean {
        return connections[connectionId]?.isConnected() ?: false
    }

    override fun connectDefault(
        url: String,
        config: Config,
        listener: WebSocketListener
    ) {
        connect(DEFAULT_CONNECTION_ID, url, config, listener)
    }

    override fun disconnectDefault(permanent: Boolean) {
        disconnect(DEFAULT_CONNECTION_ID, permanent)
    }

    override fun reconnectDefault(): Boolean {
        return reconnect(DEFAULT_CONNECTION_ID)
    }

    override fun sendText(text: String): Boolean {
        return sendMessage(DEFAULT_CONNECTION_ID, text)
    }

    override fun sendBinary(bytes: ByteArray): Boolean {
        return sendMessage(DEFAULT_CONNECTION_ID, bytes)
    }

    override fun isConnected(): Boolean {
        return isConnected(DEFAULT_CONNECTION_ID)
    }

    // --- 配置类 ---
    data class Config(
        val connectTimeout: Long = 10,
        val readTimeout: Long = 60,
        val writeTimeout: Long = 60,
        val enableHeartbeat: Boolean = true,
        val heartbeatIntervalMs: Long = 30_000,
        val heartbeatTimeoutMs: Long = 0,
        val heartbeatMessage: String = "{\"type\":\"ping\"}",

        // 重连策略
        val reconnectBaseDelayMs: Long = 2_000,
        val reconnectMaxDelayMs: Long = 30_000,
        /** 最大重连次数，0 表示无限制 
        * @since 1.0.0
 */        val maxReconnectAttempts: Int = 0,

        // 离线消息补发
        val enableMessageReplay: Boolean = false,

        // 消息队列
        val messageQueueCapacity: Int = 100,
        val dropOldestWhenQueueFull: Boolean = true,

        // 回调线程
        val callbackOnMainThread: Boolean = true,

        // 日志级别（与 HTTP 日志完全独立）
        val wsLogLevel: WebSocketLogLevel = WebSocketLogLevel.AUTO,

        // 兼容旧 API：当 wsLogLevel == AUTO 时，由此字段决定
        @Deprecated("使用 wsLogLevel 替代", ReplaceWith("wsLogLevel"))
        val enableDebugLog: Boolean = true
    )

    // --- 回调接口 ---
    interface WebSocketListener {
        fun onStateChanged(connectionId: String, oldState: State, newState: State) {}
        fun onOpen(connectionId: String) {}
        fun onMessage(connectionId: String, text: String) {}
        fun onMessage(connectionId: String, bytes: ByteArray) {}
        fun onClosing(connectionId: String, code: Int, reason: String) {}
        fun onClosed(connectionId: String, code: Int, reason: String) {}
        fun onFailure(connectionId: String, throwable: Throwable) {}
        fun onReconnecting(connectionId: String, attempt: Int) {}
        fun onHeartbeatTimeout(connectionId: String) {}
    }
}