package com.answufeng.net.websocket

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocketManager 的默认实现。
 *
 * 支持多连接管理，每个连接通过唯一的 connectionId 标识。
 * 提供默认连接的快捷 API。
 */
internal class WebSocketManagerImpl(
    private val okHttpClient: OkHttpClient,
    private val externalLogger: WebSocketLogger? = null
) : WebSocketManager {

    private val clients = ConcurrentHashMap<String, WebSocketClientImpl>()
    private val connectionConfigs = ConcurrentHashMap<String, ConnectionConfig>()

    private val _connectionStateFlow = MutableStateFlow<Map<String, WebSocketManager.State>>(emptyMap())
    override val connectionStateFlow: StateFlow<Map<String, WebSocketManager.State>> = _connectionStateFlow

    private data class ConnectionConfig(
        val url: String,
        val config: WebSocketManager.Config,
        val listener: WebSocketManager.WebSocketListener
    )

    private inner class StateChangeListener(
        private val connectionId: String,
        private val originalListener: WebSocketManager.WebSocketListener
    ) : WebSocketManager.WebSocketListener by originalListener {
        override fun onStateChanged(connectionId: String, oldState: WebSocketManager.State, newState: WebSocketManager.State) {
            originalListener.onStateChanged(connectionId, oldState, newState)
            updateConnectionStateFlow()
        }
    }

    override fun connect(
        connectionId: String,
        url: String,
        config: WebSocketManager.Config,
        listener: WebSocketManager.WebSocketListener
    ) {
        // 如果已存在连接，先断开
        clients[connectionId]?.destroy()

        val wrappedListener = StateChangeListener(connectionId, listener)
        val client = WebSocketClientImpl(
            okHttpClient = okHttpClient,
            url = url,
            config = config,
            connectionId = connectionId,
            listener = wrappedListener,
            externalLogger = externalLogger
        )

        clients[connectionId] = client
        connectionConfigs[connectionId] = ConnectionConfig(url, config, listener)
        client.connect()
        updateConnectionStateFlow()
    }

    override fun disconnect(connectionId: String, permanent: Boolean) {
        clients[connectionId]?.disconnect(permanent)
        if (permanent) {
            clients.remove(connectionId)
            connectionConfigs.remove(connectionId)
        }
        updateConnectionStateFlow()
    }

    override fun disconnectAll() {
        clients.keys.toList().forEach { connectionId ->
            disconnect(connectionId, permanent = true)
        }
    }

    override fun reconnect(connectionId: String): Boolean {
        return clients[connectionId]?.reconnect() ?: false
    }

    override fun sendMessage(connectionId: String, text: String): Boolean {
        return clients[connectionId]?.sendMessage(text) ?: false
    }

    override fun sendMessage(connectionId: String, bytes: ByteArray): Boolean {
        return clients[connectionId]?.sendMessage(bytes) ?: false
    }

    override fun isConnected(connectionId: String): Boolean {
        return clients[connectionId]?.isConnected() ?: false
    }

    // ==================== 默认单连接快捷 API ====================

    companion object {
        private const val DEFAULT_CONNECTION_ID = "default_ws"
    }

    override fun connectDefault(
        url: String,
        config: WebSocketManager.Config,
        listener: WebSocketManager.WebSocketListener
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

    private fun updateConnectionStateFlow() {
        val stateMap = clients.mapValues { (_, client) ->
            client.getState()
        }
        _connectionStateFlow.value = stateMap
    }
}
