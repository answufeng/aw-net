package com.answufeng.net.websocket

import kotlinx.coroutines.flow.StateFlow

/**
 * WebSocket 管理器接口，支持多连接管理和默认单连接快捷操作。
 *
 * 通过 Hilt 注入此接口即可使用 WebSocket 功能。
 *
 * ### 资源释放
 * 本管理器在进程生命周期内随单例长期驻留。每个连接在内部使用协程与队列；若**不再使用**本管理器
 * 且可能脱离 Application 的存活周期（例如测试、动态特性模块），请调用 [disconnectAll] 或
 * [close]，以取消内部作用域、关闭连接并停止重连，避免协程与队列被长期持有。
 * 典型做法：在 `Activity.onDestroy` / `ViewModel.onCleared` 或应用退出时调用 [disconnectAll] 或 [close]。
 */
interface WebSocketManager : java.lang.AutoCloseable {

    /**
     * 连接状态枚举。
     */
    enum class State {
        /** 已断开连接 */
        DISCONNECTED,

        /** 正在连接中 */
        CONNECTING,

        /** 已建立连接 */
        CONNECTED,

        /** 断线重连中 */
        RECONNECTING
    }

    /**
     * 所有连接状态的 [StateFlow]，便于在 ViewModel 中以响应式方式监听连接状态变化。
     *
     * Key 为连接 ID，Value 为该连接的当前状态。
     *
     * 使用示例：
     * ```kotlin
     * wsManager.connectionStateFlow
     *     .map { it["main"] }
     *     .distinctUntilChanged()
     *     .collect { state -> updateUI(state) }
     * ```
     */
    val connectionStateFlow: StateFlow<Map<String, State>>

    /**
     * 建立 WebSocket 连接。
     *
     * **鉴权注意**：与共用的 [okhttp3.OkHttpClient] 的 HTTP/HTTPS [okhttp3.Authenticator] 不同，WebSocket 连接阶段若收到不可恢复的握手 HTTP 状态（如 401/403/404 等，见实现），库内**不会**自动与 [com.answufeng.net.http.auth.TokenRefreshCoordinator] 同步刷新，请在**建连前**确保证可访问的 URL/Token/Query 策略，并自行在业务层重试或重连。
     *
     * @param connectionId 连接唯一标识，用于区分不同连接
     * @param url WebSocket 服务器地址（wss:// 或 ws://）
     * @param config 连接配置，使用默认值即可满足大部分场景
     * @param listener 连接状态和数据接收回调
     */
    fun connect(
        connectionId: String,
        url: String,
        config: WebSocketManager.Config = WebSocketManager.Config(),
        listener: WebSocketManager.WebSocketListener
    )

    /**
     * 断开指定连接。
     *
     * @param connectionId 连接标识
     * @param permanent 是否永久断开（true=清除连接记录，false=保留配置便于重连）
     */
    fun disconnect(connectionId: String, permanent: Boolean = true)

    /**
     * 断开所有连接（永久移除连接记录、释放每连接上的协程作用域等）。
     */
    fun disconnectAll()

    /**
     * 与 [disconnectAll] 等价，实现 [java.lang.AutoCloseable]，便于在测试或 `use { }` 中显式释放资源。
     */
    override fun close() = disconnectAll()

    /**
     * 重新连接指定连接。
     *
     * @param connectionId 连接标识
     * @return 是否成功触发重连（仅在断开状态下有效）
     */
    fun reconnect(connectionId: String): Boolean

    /**
     * 发送文本消息。
     *
     * @param connectionId 连接标识
     * @param text 文本内容
     * @return 是否成功加入发送队列（离线时若开启补发则入队）
     */
    fun sendMessage(connectionId: String, text: String): Boolean

    /**
     * 发送二进制消息。
     *
     * @param connectionId 连接标识
     * @param bytes 二进制数据
     * @return 是否成功加入发送队列
     */
    fun sendMessage(connectionId: String, bytes: ByteArray): Boolean

    /**
     * 检查连接是否已建立。
     *
     * @param connectionId 连接标识
     * @return true=已连接
     */
    fun isConnected(connectionId: String): Boolean

    // ==================== 默认单连接快捷 API ====================
    // 适用于大多数场景只需一个 WebSocket 连接的情况

    /**
     * 使用默认连接 ID 建立连接。
     *
     * 等同于 `connect("default_ws", url, config, listener)`。
     */
    fun connectDefault(
        url: String,
        config: WebSocketManager.Config = WebSocketManager.Config(),
        listener: WebSocketManager.WebSocketListener
    )

    /**
     * 断开默认连接。
     *
     * 等同于 `disconnect("default_ws", permanent)`。
     */
    fun disconnectDefault(permanent: Boolean = true)

    /**
     * 重连默认连接。
     *
     * 等同于 `reconnect("default_ws")`。
     */
    fun reconnectDefault(): Boolean

    /**
     * 向默认连接发送文本消息。
     *
     * 等同于 `sendMessage("default_ws", text)`。
     */
    fun sendText(text: String): Boolean

    /**
     * 向默认连接发送二进制数据。
     *
     * 等同于 `sendMessage("default_ws", bytes)`。
     */
    fun sendBinary(bytes: ByteArray): Boolean

    /**
     * 检查默认连接是否已建立。
     *
     * 等同于 `isConnected("default_ws")`。
     */
    fun isConnected(): Boolean

    /**
     * WebSocket 连接配置。
     *
     * @param heartbeatIntervalMs 心跳间隔毫秒数，默认 30000（30秒），0 表示不发送心跳
     * @param heartbeatTimeoutMs 心跳超时毫秒数，默认 60000（60秒），超时后触发断线重连
     * @param heartbeatMessage 心跳消息内容，默认 "ping"
     * @param enableHeartbeat 是否启用心跳检测，默认 true
     * @param wsLogLevel 日志级别，默认 [WebSocketLogLevel.BASIC]
     * @param callbackOnMainThread 回调是否在主线程执行，默认 true
     * @param messageQueueCapacity 离线消息队列容量，默认 100
     * @param dropOldestWhenQueueFull 队列满时是否丢弃最旧消息，默认 false
     * @param enableMessageReplay 是否启用离线消息补发，默认 true
     * @param connectTimeout 连接超时秒数，默认 10
     * @param readTimeout 读超时秒数，默认 0（无限）
     * @param writeTimeout 写超时秒数，默认 10
     * @param maxReconnectAttempts 最大重连次数，0 表示无限次，默认 0
     * @param reconnectBaseDelayMs 重连基础延迟毫秒数，默认 1000
     * @param reconnectMaxDelayMs 重连最大延迟毫秒数，默认 30000
     */
    data class Config(
        val heartbeatIntervalMs: Long = 30_000L,
        val heartbeatTimeoutMs: Long = 60_000L,
        val heartbeatMessage: String = "ping",
        val enableHeartbeat: Boolean = true,
        val wsLogLevel: WebSocketLogLevel = WebSocketLogLevel.BASIC,
        val callbackOnMainThread: Boolean = true,
        val messageQueueCapacity: Int = 100,
        val dropOldestWhenQueueFull: Boolean = false,
        val enableMessageReplay: Boolean = true,
        val connectTimeout: Long = 10L,
        val readTimeout: Long = 0L,
        val writeTimeout: Long = 10L,
        val maxReconnectAttempts: Int = 0,
        val reconnectBaseDelayMs: Long = 1_000L,
        val reconnectMaxDelayMs: Long = 30_000L
    )

    /**
     * WebSocket 事件回调接口。
     */
    interface WebSocketListener {
        /**
         * 连接已建立。
         *
         * @param connectionId 连接标识
         */
        fun onOpen(connectionId: String)

        /**
         * 收到文本消息。
         *
         * @param connectionId 连接标识
         * @param text 消息内容
         */
        fun onMessage(connectionId: String, text: String)

        /**
         * 收到二进制消息。
         *
         * @param connectionId 连接标识
         * @param bytes 消息内容
         */
        fun onMessage(connectionId: String, bytes: ByteArray)

        /**
         * 连接正在关闭。
         *
         * @param connectionId 连接标识
         * @param code 关闭状态码
         * @param reason 关闭原因
         */
        fun onClosing(connectionId: String, code: Int, reason: String)

        /**
         * 连接已关闭。
         *
         * @param connectionId 连接标识
         * @param code 关闭状态码
         * @param reason 关闭原因
         */
        fun onClosed(connectionId: String, code: Int, reason: String)

        /**
         * 连接发生错误。
         *
         * @param connectionId 连接标识
         * @param t 异常对象
         */
        fun onFailure(connectionId: String, t: Throwable)

        /**
         * 心跳超时回调。
         *
         * @param connectionId 连接标识
         */
        fun onHeartbeatTimeout(connectionId: String)

        /**
         * 连接状态变化回调。
         *
         * @param connectionId 连接标识
         * @param oldState 旧状态
         * @param newState 新状态
         */
        fun onStateChanged(connectionId: String, oldState: State, newState: State)

        /**
         * 正在重连回调。
         *
         * @param connectionId 连接标识
         * @param attempt 当前重连尝试次数
         */
        fun onReconnecting(connectionId: String, attempt: Int)
    }
}
