package com.answufeng.net.websocket

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.EOFException
import okio.ProtocolException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * WebSocket 连接的内部实现，封装连接、断开、重连、心跳、离线消息队列等核心逻辑。
 *
 * 由 [WebSocketManager] 创建和管理，不对外暴露。
 *
 * @param okHttpClient 全局共享的 OkHttpClient，通过 newBuilder() 派生子客户端
 * @param url WebSocket 服务端地址
 * @param config 连接配置
 * @param connectionId 连接标识，用于日志和回调区分
 * @param listener 事件回调
 * @since 1.0.0
 */
internal class WebSocketClientImpl(
    private val okHttpClient: OkHttpClient,
    private val url: String,
    private val config: WebSocketManager.Config,
    private val connectionId: String,
    private val listener: WebSocketManager.WebSocketListener
) {

    companion object {
        /**
         * RFC 6455 正常关闭码
         * @since 1.0.0
       */
        private const val CLOSE_NORMAL = 1000

        /**
         * 远端异常关闭（未发送 close frame）
         * @since 1.0.0
       */
        private const val CLOSE_ABNORMAL = 1006

        /**
         * 指数退避最大位移量，防止溢出。
         * 1L shl 10 = 1024 倍基础延迟，已足够覆盖绝大多数场景。
         * @since 1.0.0
       */
        private const val MAX_BACKOFF_SHIFT = 10

        /**
         * 重连最小延迟（毫秒），避免过快重连消耗资源。
         * @since 1.0.0
       */
        private const val MIN_RECONNECT_DELAY_MS = 1_000L

        /**
         * 不可恢复的 HTTP 状态码集合，遇到这些状态码时停止重连。
         * @since 1.0.0
       */
        private val UNRECOVERABLE_HTTP_CODES = setOf(401, 403, 404)

        private const val JITTER_BASE = 0.9
        private const val JITTER_RANGE = 0.2
    }

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var isManualClose = false
    @Volatile private var isPermanentClose = false
    @Volatile private var reconnectAttempt = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var reconnectRunnable: Runnable? = null
    @Volatile private var lastPongTime = 0L

    @Volatile
    private var currentState = WebSocketManager.State.DISCONNECTED

    private val messageQueue = LinkedBlockingQueue<QueuedMessage>(config.messageQueueCapacity)

    private val pingSenderRunnable = object : Runnable {
        override fun run() {
            if (currentState == WebSocketManager.State.CONNECTED) {
                if (config.heartbeatTimeoutMs > 0 && lastPongTime > 0) {
                    val elapsed = System.currentTimeMillis() - lastPongTime
                    if (elapsed > config.heartbeatTimeoutMs) {
                        WebSocketLogger.w(connectionId, "心跳超时，距上次 pong 已过 ${elapsed}ms，阈值 ${config.heartbeatTimeoutMs}ms")
                        dispatchCallback { listener.onHeartbeatTimeout(connectionId) }
                        attemptReconnect()
                        return
                    }
                }
                sendHeartbeatOnce()
                mainHandler.postDelayed(this, config.heartbeatIntervalMs)
            }
        }
    }

    private fun sendHeartbeatOnce() {
        if (config.heartbeatMessage.isBlank()) return
        WebSocketLogger.d(connectionId, "发送应用层心跳，内容：${config.heartbeatMessage}")
        sendMessage(config.heartbeatMessage)
    }

    /**
     * 离线消息队列中的消息封装。
     * @since 1.0.0
$     */
    sealed class QueuedMessage {
        /**
         * 文本消息。
         * @since 1.0.0
$         */
        data class Text(val content: String) : QueuedMessage()

        /**
         * 二进制消息。
         * @since 1.0.0
$         */
        data class Binary(val data: ByteArray) : QueuedMessage() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Binary) return false
                return data.contentEquals(other.data)
            }

            override fun hashCode(): Int = data.contentHashCode()
        }
    }

    init {
        val resolved = resolveLogLevel(config)
        WebSocketLogger.setLevel(resolved)
    }

    @Suppress("DEPRECATION")
    private fun resolveLogLevel(config: WebSocketManager.Config): WebSocketLogLevel {
        return when (config.wsLogLevel) {
            WebSocketLogLevel.AUTO -> if (config.enableDebugLog) WebSocketLogLevel.FULL else WebSocketLogLevel.NONE
            else -> config.wsLogLevel
        }
    }

    private fun createClient(): OkHttpClient {
        return okHttpClient.newBuilder()
            .connectTimeout(config.connectTimeout, TimeUnit.SECONDS)
            .readTimeout(config.readTimeout, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeout, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private inline fun dispatchCallback(crossinline action: () -> Unit) {
        if (!config.callbackOnMainThread) {
            action()
            return
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }

    private fun enqueueMessage(message: QueuedMessage): Boolean {
        if (messageQueue.offer(message)) return true
        if (config.dropOldestWhenQueueFull) {
            messageQueue.poll()
            val enqueued = messageQueue.offer(message)
            if (!enqueued) {
                WebSocketLogger.w(connectionId, "消息队列已满，丢弃消息（已尝试丢弃最旧消息）")
            }
            return enqueued
        }
        WebSocketLogger.w(connectionId, "消息队列已满，丢弃消息")
        return false
    }

    private fun changeState(newState: WebSocketManager.State) {
        if (currentState == newState) return
        val oldState = currentState
        currentState = newState
        dispatchCallback { listener.onStateChanged(connectionId, oldState, newState) }
    }

    /**
     * 发起 WebSocket 连接。
     * @since 1.0.0
$     */
    fun connect() {
        connectInternal(fromReconnect = false)
    }

    /**
     * 手动触发重连。仅在 DISCONNECTED 状态下生效。
     * @return 是否成功触发重连
     * @since 1.0.0
$     */
    fun reconnect(): Boolean {
        if (currentState != WebSocketManager.State.DISCONNECTED) return false
        isManualClose = false
        isPermanentClose = false
        reconnectAttempt = 0
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
        connectInternal(fromReconnect = true)
        return true
    }

    private fun connectInternal(fromReconnect: Boolean) {
        if (isPermanentClose || currentState != WebSocketManager.State.DISCONNECTED) return
        if (fromReconnect && isManualClose) return
        if (url.isBlank()) {
            val error = IllegalArgumentException("WebSocket url 不能为空")
            WebSocketLogger.e(connectionId, "WebSocket连接失败：url为空", error)
            dispatchCallback { listener.onFailure(connectionId, error) }
            changeState(WebSocketManager.State.DISCONNECTED)
            return
        }
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            val error = IllegalArgumentException("WebSocket url 必须以 ws:// 或 wss:// 开头，当前：$url")
            WebSocketLogger.e(connectionId, "WebSocket连接失败：url格式无效", error)
            dispatchCallback { listener.onFailure(connectionId, error) }
            changeState(WebSocketManager.State.DISCONNECTED)
            return
        }

        changeState(WebSocketManager.State.CONNECTING)
        WebSocketLogger.lifecycle(connectionId, "开始建立WebSocket连接，目标URL：$url")
        val request = Request.Builder().url(url).build()
        webSocket = createClient().newWebSocket(request, createListener())
    }

    /**
     * 断开 WebSocket 连接。
     * @param permanent 是否永久断开。若为 true，将清除消息队列和所有待执行回调，不再自动重连。
     * @since 1.0.0
$     */
    fun disconnect(permanent: Boolean) {
        WebSocketLogger.lifecycle(
            connectionId,
            "执行断开连接操作，是否永久断开：$permanent，当前连接状态：$currentState"
        )
        isManualClose = true
        isPermanentClose = permanent

        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null

        if (!permanent) {
            reconnectAttempt = 0
        }

        stopHeartbeat()

        if (currentState == WebSocketManager.State.CONNECTING || currentState == WebSocketManager.State.CONNECTED) {
            webSocket?.close(CLOSE_NORMAL, "Normal close")
            webSocket = null
        }

        changeState(WebSocketManager.State.DISCONNECTED)

        if (permanent) {
            messageQueue.clear()
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    /**
     * 销毁客户端实例，释放所有资源。
     *
     * 调用后不应再使用此实例。通常在 Activity/Fragment 的 onDestroy 中调用。
     * @since 1.0.0
$     */
    fun destroy() {
        disconnect(permanent = true)
        webSocket = null
    }

    /**
     * 发送文本消息。若当前未连接且开启了离线补发，消息将加入队列。
     * @param text 文本内容
     * @return 是否发送成功（或成功入队）
     * @since 1.0.0
$     */
    fun sendMessage(text: String): Boolean {
        return when (currentState) {
            WebSocketManager.State.CONNECTED -> {
                val result = webSocket?.send(text) ?: false
                if (result) {
                    WebSocketLogger.d(connectionId, "发送文本消息：$text")
                } else {
                    WebSocketLogger.w(connectionId, "发送文本消息失败，WebSocket 已断开")
                }
                result
            }
            else -> {
                if (config.enableMessageReplay) {
                    val enqueued = enqueueMessage(QueuedMessage.Text(text))
                    WebSocketLogger.d(connectionId, "当前未连接，文本消息已加入离线队列，入队${if (enqueued) "成功" else "失败"}，队列大小：${messageQueue.size}")
                    enqueued
                } else {
                    WebSocketLogger.w(connectionId, "当前未连接，文本消息已丢弃（未开启离线补发）")
                    false
                }
            }
        }
    }

    /**
     * 发送二进制消息。若当前未连接且开启了离线补发，消息将加入队列。
     * @param bytes 二进制数据
     * @return 是否发送成功（或成功入队）
     * @since 1.0.0
$     */
    fun sendMessage(bytes: ByteArray): Boolean {
        return when (currentState) {
            WebSocketManager.State.CONNECTED -> {
                try {
                    val result = webSocket?.send(ByteString.of(*bytes)) ?: false
                    if (result) {
                        WebSocketLogger.d(connectionId, "发送二进制消息，大小：${bytes.size} bytes")
                    } else {
                        WebSocketLogger.w(connectionId, "发送二进制消息失败，WebSocket 已断开")
                    }
                    result
                } catch (e: Exception) {
                    WebSocketLogger.w(connectionId, "发送二进制消息异常：${e.message}", e)
                    false
                }
            }

            else -> {
                if (config.enableMessageReplay) {
                    val enqueued = enqueueMessage(QueuedMessage.Binary(bytes))
                    WebSocketLogger.d(connectionId, "当前未连接，二进制消息已加入离线队列，入队${if (enqueued) "成功" else "失败"}，队列大小：${messageQueue.size}")
                    enqueued
                } else {
                    WebSocketLogger.w(connectionId, "当前未连接，二进制消息已丢弃（未开启离线补发）")
                    false
                }
            }
        }
    }

    /**
     * 检查当前是否已连接。
     * @since 1.0.0
$     */
    fun isConnected(): Boolean = currentState == WebSocketManager.State.CONNECTED

    private fun createListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                isManualClose = false
                reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
                reconnectRunnable = null
                changeState(WebSocketManager.State.CONNECTED)
                if (config.enableHeartbeat) {
                    sendHeartbeatOnce()
                    startHeartbeat()
                }
                if (config.enableMessageReplay) flushMessageQueue()
                WebSocketLogger.lifecycle(
                    connectionId,
                    "WebSocket连接成功，HTTP响应码：${response.code}，是否开启心跳：${config.enableHeartbeat}，待补发消息数：${messageQueue.size}"
                )
                dispatchCallback { listener.onOpen(connectionId) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastPongTime = System.currentTimeMillis()
                WebSocketLogger.d(connectionId, "收到文本消息：$text")
                dispatchCallback { listener.onMessage(connectionId, text) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                lastPongTime = System.currentTimeMillis()
                WebSocketLogger.d(connectionId, "收到二进制消息，大小：${bytes.size} bytes，内容(hex)：${bytes.hex()}")
                dispatchCallback { listener.onMessage(connectionId, bytes.toByteArray()) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                WebSocketLogger.lifecycle(
                    connectionId,
                    "WebSocket连接正在关闭，关闭码：$code，关闭原因：$reason"
                )
                dispatchCallback { listener.onClosing(connectionId, code, reason) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@WebSocketClientImpl.webSocket = null
                stopHeartbeat()
                changeState(WebSocketManager.State.DISCONNECTED)
                WebSocketLogger.lifecycle(
                    connectionId,
                    "WebSocket连接已完全关闭，关闭码：$code，关闭原因：$reason"
                )
                dispatchCallback { listener.onClosed(connectionId, code, reason) }
                if (code != CLOSE_NORMAL) {
                    attemptReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val wasConnected = currentState == WebSocketManager.State.CONNECTED
                this@WebSocketClientImpl.webSocket = null
                stopHeartbeat()
                changeState(WebSocketManager.State.DISCONNECTED)

                if (wasConnected && response == null && t is EOFException) {
                    val reason = "Remote peer closed connection without close frame"
                    WebSocketLogger.w(
                        connectionId,
                        "WebSocket连接被远端异常关闭，按可恢复断开处理：$reason"
                    )
                    dispatchCallback { listener.onClosed(connectionId, CLOSE_ABNORMAL, reason) }
                    attemptReconnect()
                    return
                }

                val isUnrecoverable = (response?.code in UNRECOVERABLE_HTTP_CODES) ||
                        t is ProtocolException

                if (isUnrecoverable) {
                    isPermanentClose = true
                    messageQueue.clear()
                }
                WebSocketLogger.e(
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
        if (isManualClose || isPermanentClose || currentState != WebSocketManager.State.DISCONNECTED) return

        if (config.maxReconnectAttempts > 0 && reconnectAttempt >= config.maxReconnectAttempts) {
            WebSocketLogger.w(
                connectionId,
                "已达最大重连次数(${config.maxReconnectAttempts})，停止重连"
            )
            isPermanentClose = true
            dispatchCallback { listener.onFailure(connectionId, IllegalStateException("已达最大重连次数(${config.maxReconnectAttempts})")) }
            return
        }

        reconnectAttempt++

        val baseDelay = config.reconnectBaseDelayMs * (1L shl min(reconnectAttempt - 1, MAX_BACKOFF_SHIFT))
        val jitterFactor = JITTER_BASE + Random.nextDouble() * JITTER_RANGE
        val delayWithJitter = (baseDelay * jitterFactor).toLong()
        val finalDelay = delayWithJitter.coerceIn(MIN_RECONNECT_DELAY_MS, config.reconnectMaxDelayMs)
        WebSocketLogger.lifecycle(
            connectionId,
            "触发WebSocket重连，第$reconnectAttempt 次重连，重连延迟：${finalDelay}ms"
        )
        dispatchCallback { listener.onReconnecting(connectionId, reconnectAttempt) }

        val runnable = Runnable {
            if (!isPermanentClose && currentState == WebSocketManager.State.DISCONNECTED && !isManualClose) {
                connectInternal(fromReconnect = true)
            }
        }
        reconnectRunnable = runnable
        mainHandler.postDelayed(runnable, finalDelay)
    }

    private fun startHeartbeat() {
        mainHandler.removeCallbacks(pingSenderRunnable)
        mainHandler.postDelayed(pingSenderRunnable, config.heartbeatIntervalMs)
    }

    private fun stopHeartbeat() {
        mainHandler.removeCallbacks(pingSenderRunnable)
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
                WebSocketLogger.w(connectionId, "离线消息补发失败，消息将重新入队")
            }
        }
        failed.reversed().forEach { message ->
            messageQueue.offer(message)
        }
    }

    private fun sendDirect(text: String): Boolean {
        val result = webSocket?.send(text) ?: false
        if (result) {
            WebSocketLogger.d(connectionId, "补发文本消息：$text")
        }
        return result
    }

    private fun sendDirect(bytes: ByteArray): Boolean {
        return try {
            val result = webSocket?.send(ByteString.of(*bytes)) ?: false
            if (result) {
                WebSocketLogger.d(connectionId, "补发二进制消息，大小：${bytes.size} bytes")
            }
            result
        } catch (e: Exception) {
            WebSocketLogger.w(connectionId, "补发二进制消息异常：${e.message}", e)
            false
        }
    }
}
