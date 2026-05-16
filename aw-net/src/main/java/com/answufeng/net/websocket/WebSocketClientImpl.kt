package com.answufeng.net.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.EOFException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class WebSocketClientImpl(
    private val okHttpClient: OkHttpClient,
    private val url: String,
    private val config: WebSocketManager.Config,
    private val connectionId: String,
    private val listener: WebSocketManager.WebSocketListener,
    externalLogger: WebSocketLogger? = null,
) {
    companion object {
        private const val CLOSE_NORMAL = 1000
        private const val CLOSE_ABNORMAL = 1006
        private val UNRECOVERABLE_HTTP_CODES = setOf(401, 403, 404)
    }

    private val reconnectStrategy: ReconnectStrategy by lazy {
        config.reconnectStrategy ?: ExponentialBackoffStrategy(
            maxReconnectAttempts = config.maxReconnectAttempts,
            baseDelayMs = config.reconnectBaseDelayMs,
            maxDelayMs = config.reconnectMaxDelayMs,
        )
    }

    internal data class WsState(
        val connectionState: WebSocketManager.State = WebSocketManager.State.DISCONNECTED,
        val isManualClose: Boolean = false,
        val isPermanentClose: Boolean = false,
        val reconnectAttempt: Int = 0,
    )

    private val stateRef = AtomicReference(WsState())

    private val scopeRef = AtomicReference(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val scopeLock = Any()

    private fun ensureScopeActive() {
        val current = scopeRef.get()
        if (current.coroutineContext[Job]?.isActive == true) return
        synchronized(scopeLock) {
            val check = scopeRef.get()
            if (check.coroutineContext[Job]?.isActive == true) return
            scopeRef.set(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
    }

    private val scope: CoroutineScope
        get() = scopeRef.get()

    @Volatile
    private var webSocket: WebSocket? = null

    private var reconnectJob: Job? = null

    private val wsLogger =
        DefaultWebSocketLogger(config.wsLogLevel).also {
            externalLogger?.let { logger -> it.setLogger(logger) }
        }

    private val messageQueueManager = MessageQueueManager(config, connectionId, wsLogger)

    private val heartbeatManager =
        HeartbeatManager(
            config = config,
            connectionId = connectionId,
            wsLogger = wsLogger,
            scopeProvider = { scope },
            isConnected = { stateRef.get().connectionState == WebSocketManager.State.CONNECTED },
            onTimeout = {
                dispatchCallback { listener.onHeartbeatTimeout(connectionId) }
                webSocket?.cancel()
                webSocket = null
                changeStateWithOld(WebSocketManager.State.DISCONNECTED)
                attemptReconnect()
            },
            sendHeartbeat = { sendHeartbeatOnce() },
        )

    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(config.connectTimeout, TimeUnit.SECONDS)
            .readTimeout(config.readTimeout, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeout, TimeUnit.SECONDS)
            .pingInterval(config.pingIntervalMs, TimeUnit.MILLISECONDS)
            .build()
    }

    sealed class QueuedMessage {
        data class Text(val content: String) : QueuedMessage()

        data class Binary(val data: ByteArray) : QueuedMessage() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Binary) return false
                return data.contentEquals(other.data)
            }

            override fun hashCode(): Int = data.contentHashCode()
        }
    }

    private inline fun dispatchCallback(crossinline action: () -> Unit) {
        if (!config.callbackOnMainThread) {
            action()
            return
        }
        scope.launch {
            withContext(Dispatchers.Main) { action() }
        }
    }

    private fun enqueueMessage(message: QueuedMessage): Boolean {
        val result = messageQueueManager.enqueue(message)
        return when (result) {
            is MessageQueueManager.EnqueueResult.Success -> true
            is MessageQueueManager.EnqueueResult.DroppedOldest -> true
            is MessageQueueManager.EnqueueResult.Dropped -> false
            is MessageQueueManager.EnqueueResult.QueueFull -> false
        }
    }

    private fun changeStateWithOld(newState: WebSocketManager.State) {
        var oldState: WebSocketManager.State? = null
        stateRef.updateAndGet { current ->
            if (current.connectionState == newState) {
                current
            } else {
                oldState = current.connectionState
                current.copy(connectionState = newState)
            }
        }
        if (oldState != null && oldState != newState) {
            dispatchCallback { listener.onStateChanged(connectionId, oldState!!, newState) }
        }
    }

    fun connect() {
        connectInternal(fromReconnect = false)
    }

    fun reconnect(): Boolean {
        ensureScopeActive()
        val current = stateRef.get()
        if (current.connectionState != WebSocketManager.State.DISCONNECTED) return false
        stateRef.updateAndGet { it.copy(isManualClose = false, isPermanentClose = false, reconnectAttempt = 0) }
        reconnectJob?.cancel()
        reconnectJob = null
        connectInternal(fromReconnect = true)
        return true
    }

    private fun connectInternal(fromReconnect: Boolean) {
        ensureScopeActive()
        val s = stateRef.get()
        if (s.isPermanentClose || s.connectionState != WebSocketManager.State.DISCONNECTED) return
        if (fromReconnect && s.isManualClose) return

        config.onBeforeConnect?.invoke()

        if (url.isBlank()) {
            val error = IllegalArgumentException("WebSocket url 不能为空")
            wsLogger.e(connectionId, "WebSocket连接失败：url为空", error)
            dispatchCallback { listener.onFailure(connectionId, error) }
            return
        }
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            val error = IllegalArgumentException("WebSocket url 必须以 ws:// 或 wss:// 开头，当前：$url")
            wsLogger.e(connectionId, "WebSocket连接失败：url格式无效", error)
            dispatchCallback { listener.onFailure(connectionId, error) }
            return
        }

        changeStateWithOld(WebSocketManager.State.CONNECTING)
        wsLogger.lifecycle(connectionId, "开始建立WebSocket连接，目标URL：$url")
        webSocket?.cancel()
        webSocket = null

        val httpUrl = url.toWebSocketHttpUrlOrNull()
        val finalUrl =
            if (httpUrl != null && config.queryParameters.isNotEmpty()) {
                httpUrl.newBuilder().apply {
                    config.queryParameters.forEach { (name, value) -> addQueryParameter(name, value) }
                }.build()
            } else {
                httpUrl
            }
        if (finalUrl == null) {
            val error = IllegalArgumentException("Invalid WebSocket url: $url")
            wsLogger.e(connectionId, "Invalid WebSocket url", error)
            changeStateWithOld(WebSocketManager.State.DISCONNECTED)
            dispatchCallback { listener.onFailure(connectionId, error) }
            return
        }

        val request =
            Request.Builder().url(finalUrl).apply {
                config.headers.forEach { (name, value) -> header(name, value) }
            }.build()
        webSocket = wsClient.newWebSocket(request, createListener())
    }

    fun disconnect(permanent: Boolean) {
        wsLogger.lifecycle(
            connectionId,
            "执行断开连接操作，是否永久断开：$permanent，当前连接状态：${stateRef.get().connectionState}",
        )

        var previousState: WebSocketManager.State? = null
        stateRef.updateAndGet { current ->
            previousState = current.connectionState
            current.copy(
                isManualClose = true,
                isPermanentClose = permanent,
                reconnectAttempt = if (permanent) current.reconnectAttempt else 0,
                connectionState = WebSocketManager.State.DISCONNECTED,
            )
        }

        reconnectJob?.cancel()
        reconnectJob = null

        stopHeartbeat()

        if (previousState == WebSocketManager.State.CONNECTING || previousState == WebSocketManager.State.CONNECTED) {
            webSocket?.close(CLOSE_NORMAL, "Normal close")
            webSocket = null
        }

        if (previousState != null && previousState != WebSocketManager.State.DISCONNECTED) {
            dispatchCallback { listener.onStateChanged(connectionId, previousState!!, WebSocketManager.State.DISCONNECTED) }
        }

        if (permanent) {
            messageQueueManager.clear()
            scopeRef.get().cancel()
        }
    }

    fun destroy() {
        disconnect(permanent = true)
        webSocket = null
    }

    fun sendMessage(text: String): Boolean {
        val s = stateRef.get()
        return when (s.connectionState) {
            WebSocketManager.State.CONNECTED -> {
                val result = webSocket?.send(text) ?: false
                if (result) {
                    wsLogger.d(connectionId, "发送文本消息：$text")
                } else {
                    wsLogger.w(connectionId, "发送文本消息失败，WebSocket 已断开")
                }
                result
            }
            else -> {
                if (config.enableMessageReplay) {
                    val enqueued = enqueueMessage(QueuedMessage.Text(text))
                    wsLogger.d(connectionId, "当前未连接，文本消息已加入离线队列，入队${if (enqueued) "成功" else "失败"}，队列大小：${messageQueueManager.size()}")
                    enqueued
                } else {
                    wsLogger.w(connectionId, "当前未连接，文本消息已丢弃（未开启离线补发）")
                    false
                }
            }
        }
    }

    fun sendMessage(bytes: ByteArray): Boolean {
        val s = stateRef.get()
        return when (s.connectionState) {
            WebSocketManager.State.CONNECTED -> {
                try {
                    val result = webSocket?.send(ByteString.of(*bytes)) ?: false
                    if (result) {
                        wsLogger.d(connectionId, "发送二进制消息，大小：${bytes.size} bytes")
                    } else {
                        wsLogger.w(connectionId, "发送二进制消息失败，WebSocket 已断开")
                    }
                    result
                } catch (e: Exception) {
                    wsLogger.w(connectionId, "发送二进制消息异常：${e.message}", e)
                    false
                }
            }

            else -> {
                if (config.enableMessageReplay) {
                    val enqueued = enqueueMessage(QueuedMessage.Binary(bytes.copyOf()))
                    wsLogger.d(connectionId, "当前未连接，二进制消息已加入离线队列，入队${if (enqueued) "成功" else "失败"}，队列大小：${messageQueueManager.size()}")
                    enqueued
                } else {
                    wsLogger.w(connectionId, "当前未连接，二进制消息已丢弃（未开启离线补发）")
                    false
                }
            }
        }
    }

    fun isConnected(): Boolean = stateRef.get().connectionState == WebSocketManager.State.CONNECTED

    fun getState(): WebSocketManager.State = stateRef.get().connectionState

    private fun createListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                heartbeatManager.onPongReceived()
                stateRef.updateAndGet { it.copy(reconnectAttempt = 0, isManualClose = false) }
                reconnectJob?.cancel()
                reconnectJob = null
                changeStateWithOld(WebSocketManager.State.CONNECTED)
                if (config.enableHeartbeat) {
                    sendHeartbeatOnce()
                    startHeartbeat()
                }
                if (config.enableMessageReplay) flushMessageQueue()
                wsLogger.lifecycle(
                    connectionId,
                    "WebSocket连接成功，HTTP响应码：${response.code}，是否开启心跳：${config.enableHeartbeat}，待补发消息数：${messageQueueManager.size()}",
                )
                dispatchCallback { listener.onOpen(connectionId) }
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                if (config.heartbeatResponseMessage == null || text == config.heartbeatResponseMessage) {
                    heartbeatManager.onPongReceived()
                }
                wsLogger.d(connectionId, "收到文本消息：$text")
                dispatchCallback { listener.onMessage(connectionId, text) }
            }

            override fun onMessage(
                webSocket: WebSocket,
                bytes: ByteString,
            ) {
                if (config.heartbeatResponseMessage == null) {
                    heartbeatManager.onPongReceived()
                }
                val preview = if (bytes.size <= 64) bytes.hex() else "${bytes.substring(0, 64).hex()}..."
                wsLogger.d(connectionId, "Received binary message, size=${bytes.size} bytes, preview=$preview")
                dispatchCallback { listener.onMessage(connectionId, bytes.toByteArray()) }
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                wsLogger.lifecycle(
                    connectionId,
                    "WebSocket连接正在关闭，关闭码：$code，关闭原因：$reason",
                )
                dispatchCallback { listener.onClosing(connectionId, code, reason) }
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                this@WebSocketClientImpl.webSocket = null
                stopHeartbeat()
                changeStateWithOld(WebSocketManager.State.DISCONNECTED)
                wsLogger.lifecycle(
                    connectionId,
                    "WebSocket连接已完全关闭，关闭码：$code，关闭原因：$reason",
                )
                dispatchCallback { listener.onClosed(connectionId, code, reason) }
                if (code != CLOSE_NORMAL) {
                    attemptReconnect()
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                val wasConnected = stateRef.get().connectionState == WebSocketManager.State.CONNECTED
                this@WebSocketClientImpl.webSocket = null
                stopHeartbeat()

                if (wasConnected && response == null && t is EOFException) {
                    changeStateWithOld(WebSocketManager.State.DISCONNECTED)
                    val reason = "Remote peer closed connection without close frame"
                    wsLogger.w(
                        connectionId,
                        "WebSocket连接被远端异常关闭，按可恢复断开处理：$reason",
                    )
                    dispatchCallback { listener.onClosed(connectionId, CLOSE_ABNORMAL, reason) }
                    attemptReconnect()
                    return
                }

                val isUnrecoverable =
                    (response?.code in UNRECOVERABLE_HTTP_CODES) ||
                        t is okio.ProtocolException

                if (isUnrecoverable) {
                    stateRef.updateAndGet { it.copy(isPermanentClose = true) }
                    messageQueueManager.clear()
                    changeStateWithOld(WebSocketManager.State.ERROR)
                } else {
                    changeStateWithOld(WebSocketManager.State.DISCONNECTED)
                }
                wsLogger.e(
                    connectionId,
                    "WebSocket连接失败，HTTP响应码：${response?.code ?: -1}，是否为不可恢复异常：$isUnrecoverable，异常原因：${t.message}",
                    t,
                )
                dispatchCallback { listener.onFailure(connectionId, t) }

                if (!isUnrecoverable) {
                    attemptReconnect()
                }
            }
        }
    }

    private fun attemptReconnect() {
        val s = stateRef.get()
        if (s.isManualClose || s.isPermanentClose || s.connectionState != WebSocketManager.State.DISCONNECTED) return

        val nextAttempt = s.reconnectAttempt + 1

        if (!reconnectStrategy.shouldRetry(nextAttempt)) {
            wsLogger.w(
                connectionId,
                "已达最大重连次数，停止重连",
            )
            stateRef.updateAndGet { it.copy(isPermanentClose = true) }
            dispatchCallback { listener.onFailure(connectionId, IllegalStateException("已达最大重连次数")) }
            return
        }

        stateRef.updateAndGet { it.copy(reconnectAttempt = nextAttempt) }

        val finalDelay = reconnectStrategy.computeDelayMs(nextAttempt)
        wsLogger.lifecycle(
            connectionId,
            "触发WebSocket重连，第$nextAttempt 次重连，重连延迟：${finalDelay}ms",
        )
        dispatchCallback { listener.onReconnecting(connectionId, nextAttempt) }

        reconnectJob =
            scope.launch {
                delay(finalDelay)
                ensureActive()
                val current = stateRef.get()
                if (!current.isPermanentClose && current.connectionState == WebSocketManager.State.DISCONNECTED && !current.isManualClose) {
                    connectInternal(fromReconnect = true)
                }
            }
    }

    private fun startHeartbeat() {
        heartbeatManager.startWithCheck()
    }

    private fun stopHeartbeat() {
        heartbeatManager.stop()
    }

    private fun sendHeartbeatOnce() {
        if (config.heartbeatMessage.isBlank()) return
        wsLogger.d(connectionId, "发送应用层心跳")
        sendMessage(config.heartbeatMessage)
    }

    private fun String.toWebSocketHttpUrlOrNull(): okhttp3.HttpUrl? {
        val normalized =
            when {
                startsWith("ws://") -> "http://${removePrefix("ws://")}"
                startsWith("wss://") -> "https://${removePrefix("wss://")}"
                else -> this
            }
        return normalized.toHttpUrlOrNull()
    }

    private fun flushMessageQueue() {
        if (messageQueueManager.isEmpty()) return
        val pending = mutableListOf<QueuedMessage>()
        messageQueueManager.drainTo(pending)
        val failed = mutableListOf<QueuedMessage>()
        pending.forEach { message ->
            val sent =
                when (message) {
                    is QueuedMessage.Text -> sendDirect(message.content)
                    is QueuedMessage.Binary -> sendDirect(message.data)
                }
            if (!sent) {
                failed.add(message)
                wsLogger.w(connectionId, "离线消息补发失败，消息将重新入队")
            }
        }
        messageQueueManager.reoffer(failed)
    }

    private fun sendDirect(text: String): Boolean {
        val result = webSocket?.send(text) ?: false
        if (result) {
            wsLogger.d(connectionId, "补发文本消息：$text")
        }
        return result
    }

    private fun sendDirect(bytes: ByteArray): Boolean {
        return try {
            val result = webSocket?.send(ByteString.of(*bytes)) ?: false
            if (result) {
                wsLogger.d(connectionId, "补发二进制消息，大小：${bytes.size} bytes")
            }
            result
        } catch (e: Exception) {
            wsLogger.w(connectionId, "补发二进制消息异常：${e.message}", e)
            false
        }
    }
}
