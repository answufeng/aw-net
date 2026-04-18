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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.EOFException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.random.Random

internal class WebSocketClientImpl(
    private val okHttpClient: OkHttpClient,
    private val url: String,
    private val config: WebSocketManager.Config,
    private val connectionId: String,
    private val listener: WebSocketManager.WebSocketListener,
    externalLogger: WebSocketLogger? = null
) {

    companion object {
        private const val CLOSE_NORMAL = 1000
        private const val CLOSE_ABNORMAL = 1006
        private const val MAX_BACKOFF_SHIFT = 10
        private const val MIN_RECONNECT_DELAY_MS = 1_000L
        private val UNRECOVERABLE_HTTP_CODES = setOf(401, 403, 404)
        private const val JITTER_BASE = 0.9
        private const val JITTER_RANGE = 0.2
    }

    internal data class WsState(
        val connectionState: WebSocketManager.State = WebSocketManager.State.DISCONNECTED,
        val isManualClose: Boolean = false,
        val isPermanentClose: Boolean = false,
        val reconnectAttempt: Int = 0
    )

    private val stateRef = AtomicReference(WsState())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var lastPongTime = 0L

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private val messageQueue = LinkedBlockingQueue<QueuedMessage>(config.messageQueueCapacity)

    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(config.connectTimeout, TimeUnit.SECONDS)
            .readTimeout(config.readTimeout, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeout, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.MILLISECONDS)
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

    private val wsLogger = WebSocketLogger(config.wsLogLevel).also {
        externalLogger?.let { logger -> it.setLogger(logger) }
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
        if (messageQueue.offer(message)) return true
        if (config.dropOldestWhenQueueFull) {
            messageQueue.poll()
            val enqueued = messageQueue.offer(message)
            if (!enqueued) {
                wsLogger.w(connectionId, "消息队列已满，丢弃消息（已尝试丢弃最旧消息）")
            }
            return enqueued
        }
        wsLogger.w(connectionId, "消息队列已满，丢弃消息")
        return false
    }

    private fun changeStateWithOld(newState: WebSocketManager.State) {
        var oldState: WebSocketManager.State? = null
        stateRef.updateAndGet { current ->
            if (current.connectionState == newState) current
            else {
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
        val current = stateRef.get()
        if (current.connectionState != WebSocketManager.State.DISCONNECTED) return false
        stateRef.updateAndGet { it.copy(isManualClose = false, isPermanentClose = false, reconnectAttempt = 0) }
        reconnectJob?.cancel()
        reconnectJob = null
        connectInternal(fromReconnect = true)
        return true
    }

    private fun connectInternal(fromReconnect: Boolean) {
        val s = stateRef.get()
        if (s.isPermanentClose || s.connectionState != WebSocketManager.State.DISCONNECTED) return
        if (fromReconnect && s.isManualClose) return
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
        val request = Request.Builder().url(url).build()
        webSocket = wsClient.newWebSocket(request, createListener())
    }

    fun disconnect(permanent: Boolean) {
        wsLogger.lifecycle(
            connectionId,
            "执行断开连接操作，是否永久断开：$permanent，当前连接状态：${stateRef.get().connectionState}"
        )

        stateRef.updateAndGet { it.copy(isManualClose = true, isPermanentClose = permanent) }

        reconnectJob?.cancel()
        reconnectJob = null

        if (!permanent) {
            stateRef.updateAndGet { it.copy(reconnectAttempt = 0) }
        }

        stopHeartbeat()

        val s = stateRef.get()
        if (s.connectionState == WebSocketManager.State.CONNECTING || s.connectionState == WebSocketManager.State.CONNECTED) {
            webSocket?.close(CLOSE_NORMAL, "Normal close")
            webSocket = null
        }

        changeStateWithOld(WebSocketManager.State.DISCONNECTED)

        if (permanent) {
            messageQueue.clear()
            scope.cancel()
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
                    wsLogger.d(connectionId, "当前未连接，文本消息已加入离线队列，入队${if (enqueued) "成功" else "失败"}，队列大小：${messageQueue.size}")
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
                    wsLogger.d(connectionId, "当前未连接，二进制消息已加入离线队列，入队${if (enqueued) "成功" else "失败"}，队列大小：${messageQueue.size}")
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
            override fun onOpen(webSocket: WebSocket, response: Response) {
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
                    "WebSocket连接成功，HTTP响应码：${response.code}，是否开启心跳：${config.enableHeartbeat}，待补发消息数：${messageQueue.size}"
                )
                dispatchCallback { listener.onOpen(connectionId) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastPongTime = System.currentTimeMillis()
                wsLogger.d(connectionId, "收到文本消息：$text")
                dispatchCallback { listener.onMessage(connectionId, text) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                lastPongTime = System.currentTimeMillis()
                wsLogger.d(connectionId, "收到二进制消息，大小：${bytes.size} bytes，内容(hex)：${bytes.hex()}")
                dispatchCallback { listener.onMessage(connectionId, bytes.toByteArray()) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                wsLogger.lifecycle(
                    connectionId,
                    "WebSocket连接正在关闭，关闭码：$code，关闭原因：$reason"
                )
                dispatchCallback { listener.onClosing(connectionId, code, reason) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@WebSocketClientImpl.webSocket = null
                stopHeartbeat()
                changeStateWithOld(WebSocketManager.State.DISCONNECTED)
                wsLogger.lifecycle(
                    connectionId,
                    "WebSocket连接已完全关闭，关闭码：$code，关闭原因：$reason"
                )
                dispatchCallback { listener.onClosed(connectionId, code, reason) }
                if (code != CLOSE_NORMAL) {
                    attemptReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val wasConnected = stateRef.get().connectionState == WebSocketManager.State.CONNECTED
                this@WebSocketClientImpl.webSocket = null
                stopHeartbeat()
                changeStateWithOld(WebSocketManager.State.DISCONNECTED)

                if (wasConnected && response == null && t is EOFException) {
                    val reason = "Remote peer closed connection without close frame"
                    wsLogger.w(
                        connectionId,
                        "WebSocket连接被远端异常关闭，按可恢复断开处理：$reason"
                    )
                    dispatchCallback { listener.onClosed(connectionId, CLOSE_ABNORMAL, reason) }
                    attemptReconnect()
                    return
                }

                val isUnrecoverable = (response?.code in UNRECOVERABLE_HTTP_CODES) ||
                        t is okio.ProtocolException

                if (isUnrecoverable) {
                    stateRef.updateAndGet { it.copy(isPermanentClose = true) }
                    messageQueue.clear()
                }
                wsLogger.e(
                    connectionId,
                    "WebSocket连接失败，HTTP响应码：${response?.code ?: -1}，是否为不可恢复异常：$isUnrecoverable，异常原因：${t.message}",
                    t
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

        if (config.maxReconnectAttempts > 0 && s.reconnectAttempt >= config.maxReconnectAttempts) {
            wsLogger.w(
                connectionId,
                "已达最大重连次数(${config.maxReconnectAttempts})，停止重连"
            )
            stateRef.updateAndGet { it.copy(isPermanentClose = true) }
            dispatchCallback { listener.onFailure(connectionId, IllegalStateException("已达最大重连次数(${config.maxReconnectAttempts})")) }
            return
        }

        stateRef.updateAndGet { it.copy(reconnectAttempt = it.reconnectAttempt + 1) }
        val attempt = stateRef.get().reconnectAttempt

        val baseDelay = config.reconnectBaseDelayMs * (1L shl min(attempt - 1, MAX_BACKOFF_SHIFT))
        val jitterFactor = JITTER_BASE + Random.nextDouble() * JITTER_RANGE
        val delayWithJitter = (baseDelay * jitterFactor).toLong()
        val finalDelay = delayWithJitter.coerceIn(MIN_RECONNECT_DELAY_MS, config.reconnectMaxDelayMs)
        wsLogger.lifecycle(
            connectionId,
            "触发WebSocket重连，第$attempt 次重连，重连延迟：${finalDelay}ms"
        )
        dispatchCallback { listener.onReconnecting(connectionId, attempt) }

        reconnectJob = scope.launch {
            delay(finalDelay)
            ensureActive()
            val current = stateRef.get()
            if (!current.isPermanentClose && current.connectionState == WebSocketManager.State.DISCONNECTED && !current.isManualClose) {
                connectInternal(fromReconnect = true)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(config.heartbeatIntervalMs)
                ensureActive()
                val s = stateRef.get()
                if (s.connectionState != WebSocketManager.State.CONNECTED) break

                if (config.heartbeatTimeoutMs > 0 && lastPongTime > 0) {
                    val elapsed = System.currentTimeMillis() - lastPongTime
                    if (elapsed > config.heartbeatTimeoutMs) {
                        wsLogger.w(connectionId, "心跳超时，距上次 pong 已过 ${elapsed}ms，阈值 ${config.heartbeatTimeoutMs}ms")
                        dispatchCallback { listener.onHeartbeatTimeout(connectionId) }
                        attemptReconnect()
                        break
                    }
                }
                sendHeartbeatOnce()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendHeartbeatOnce() {
        if (config.heartbeatMessage.isBlank()) return
        wsLogger.d(connectionId, "发送应用层心跳")
        sendMessage(config.heartbeatMessage)
    }

    private fun flushMessageQueue() {
        if (messageQueue.isEmpty()) return
        val pending = mutableListOf<QueuedMessage>()
        messageQueue.drainTo(pending)
        val failed = mutableListOf<QueuedMessage>()
        pending.forEach { message ->
            val sent = when (message) {
                is QueuedMessage.Text -> sendDirect(message.content)
                is QueuedMessage.Binary -> sendDirect(message.data)
            }
            if (!sent) {
                failed.add(message)
                wsLogger.w(connectionId, "离线消息补发失败，消息将重新入队")
            }
        }
        failed.reversed().forEach { message ->
            messageQueue.offer(message)
        }
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
