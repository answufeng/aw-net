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
 */
class WebSocketManager(
    private val okHttpClient: OkHttpClient,
    private val externalLogger: IWebSocketLogger? = null
) : IWebSocketManager {

    companion object {
        private const val DEFAULT_CONNECTION_ID = "default_ws"
    }

    /**
     * WebSocket 连接状态。
     *
     * 状态转换：`DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTED`
     * - [DISCONNECTED]：未连接或已断开，可调用 [connect] 发起新连接
     * - [CONNECTING]：正在建立 TCP + WebSocket 握手
     * - [CONNECTED]：连接就绪，可收发消息
     * @since 1.0.0
     */
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
        val client = WebSocketClientImpl(okHttpClient, url, config, connectionId, listener, externalLogger)
        connections[connectionId] = client
        client.connect()
    }

    override fun disconnect(connectionId: String, permanent: Boolean) {
        if (permanent) {
            connections.remove(connectionId)?.destroy()
        } else {
            connections[connectionId]?.disconnect(permanent)
        }
    }

    override fun disconnectAll() {
        connections.values.forEach { it.destroy() }
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

    /**
     * WebSocket 连接配置。
     *
     * 包含超时、心跳、重连策略、离线消息队列、回调线程和日志级别等配置项。
     * @since 1.0.0
     */
    data class Config(
        val connectTimeout: Long = 10,
        val readTimeout: Long = 60,
        val writeTimeout: Long = 60,
        val enableHeartbeat: Boolean = true,
        val heartbeatIntervalMs: Long = 30_000,
        val heartbeatTimeoutMs: Long = 60_000,
        val heartbeatMessage: String = "{\"type\":\"ping\"}",

        val reconnectBaseDelayMs: Long = 2_000,
        val reconnectMaxDelayMs: Long = 30_000,

        /**
         * 最大重连次数，0 表示无限制
         * @since 1.0.0
         */
        val maxReconnectAttempts: Int = 0,

        val enableMessageReplay: Boolean = false,

        val messageQueueCapacity: Int = 100,
        val dropOldestWhenQueueFull: Boolean = true,

        val callbackOnMainThread: Boolean = true,

        val wsLogLevel: WebSocketLogLevel = WebSocketLogLevel.NONE
    )

    /**
     * WebSocket 事件回调接口。所有方法都有默认空实现，按需覆写即可。
     * @since 1.0.0
     */
    interface WebSocketListener {
        /**
         * 连接状态变更回调。
         * @since 1.0.0
         */
        fun onStateChanged(connectionId: String, oldState: State, newState: State) {}

        /**
         * 连接成功回调。
         * @since 1.0.0
         */
        fun onOpen(connectionId: String) {}

        /**
         * 收到文本消息回调。
         * @since 1.0.0
         */
        fun onMessage(connectionId: String, text: String) {}

        /**
         * 收到二进制消息回调。
         * @since 1.0.0
         */
        fun onMessage(connectionId: String, bytes: ByteArray) {}

        /**
         * 连接正在关闭回调。
         * @since 1.0.0
         */
        fun onClosing(connectionId: String, code: Int, reason: String) {}

        /**
         * 连接已关闭回调。
         * @since 1.0.0
         */
        fun onClosed(connectionId: String, code: Int, reason: String) {}

        /**
         * 连接失败回调。
         * @since 1.0.0
         */
        fun onFailure(connectionId: String, throwable: Throwable) {}

        /**
         * 正在重连回调。
         * @since 1.0.0
         */
        fun onReconnecting(connectionId: String, attempt: Int) {}

        /**
         * 心跳超时回调。
         * @since 1.0.0
         */
        fun onHeartbeatTimeout(connectionId: String) {}
    }
}
