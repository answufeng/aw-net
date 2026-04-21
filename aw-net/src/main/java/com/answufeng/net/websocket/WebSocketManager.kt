package com.answufeng.net.websocket

import kotlinx.coroutines.flow.StateFlow

/**
 * WebSocket 管理器接口
 * 支持多连接管理和默认单连接快捷操作
 */
interface WebSocketManager {

    /**
     * 连接状态枚举
     */
    enum class State {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
    }

    /**
     * 所有连接状态的 [StateFlow]，便于在 ViewModel 中以响应式方式监听连接状态变化。
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
     * 建立 WebSocket 连接
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
     * 断开指定连接
     * @param connectionId 连接标识
     * @param permanent 是否永久断开（true=清除连接记录，false=保留配置便于重连）
 */
    fun disconnect(connectionId: String, permanent: Boolean = true)

    /**
     * 断开所有连接
 */
    fun disconnectAll()

    /**
     * 重新连接指定连接
     * @param connectionId 连接标识
     * @return 是否成功触发重连（仅在断开状态下有效）
 */
    fun reconnect(connectionId: String): Boolean

    /**
     * 发送文本消息
     * @param connectionId 连接标识
     * @param text 文本内容
     * @return 是否成功加入发送队列（离线时若开启补发则入队）
 */
    fun sendMessage(connectionId: String, text: String): Boolean

    /**
     * 发送二进制消息
     * @param connectionId 连接标识
     * @param bytes 二进制数据
     * @return 是否成功加入发送队列
 */
    fun sendMessage(connectionId: String, bytes: ByteArray): Boolean

    /**
     * 检查连接是否已建立
     * @param connectionId 连接标识
     * @return true=已连接
 */
    fun isConnected(connectionId: String): Boolean

    // ==================== 默认单连接快捷 API ====================
    // 适用于大多数场景只需一个 WebSocket 连接的情况

    /**
     * 使用默认连接ID建立连接
     * 等同于 connect("default_ws", url, config, listener)
 */
    fun connectDefault(
        url: String,
        config: WebSocketManager.Config = WebSocketManager.Config(),
        listener: WebSocketManager.WebSocketListener
    )

    /**
     * 断开默认连接
     * 等同于 disconnect("default_ws", permanent)
 */
    fun disconnectDefault(permanent: Boolean = true)

    /**
     * 重连默认连接
     * 等同于 reconnect("default_ws")
 */
    fun reconnectDefault(): Boolean

    /**
     * 向默认连接发送文本
     * 等同于 sendMessage("default_ws", text)
 */
    fun sendText(text: String): Boolean

    /**
     * 向默认连接发送二进制数据
     * 等同于 sendMessage("default_ws", bytes)
 */
    fun sendBinary(bytes: ByteArray): Boolean

    /**
     * 检查默认连接是否已建立
     * 等同于 isConnected("default_ws")
 */
    fun isConnected(): Boolean
}