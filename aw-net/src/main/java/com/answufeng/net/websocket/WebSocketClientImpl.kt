package com.answufeng.net.websocket

import android.os.Handler
import android.os.Looper
import com.answufeng.net.http.model.NetCode
import okhttp3.*
import okio.ByteString
import java.io.EOFException
import okio.ProtocolException
import java.net.UnknownHostException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.collections.isNotEmpty
import kotlin.math.min
import kotlin.random.Random

internal class WebSocketClientImpl(
    private val okHttpClient: OkHttpClient,
    private val url: String,
    private val config: WebSocketManager.Config,
    private val connectionId: String,
    private val listener: WebSocketManager.WebSocketListener
) {

    companion object {
        /** RFC 6455 正常关闭码 */
        private const val CLOSE_NORMAL = 1000
        /** 远端异常关闭（未发送 close frame） */
        private const val CLOSE_ABNORMAL = 1006
    }

    private var webSocket: WebSocket? = null
    @Volatile private var isManualClose = false
    @Volatile private var isPermanentClose = false
    @Volatile private var reconnectAttempt = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    @Volatile
    private var currentState = WebSocketManager.State.DISCONNECTED

    private val messageQueue = LinkedBlockingQueue<QueuedMessage>(config.messageQueueCapacity)

    private val pingSenderRunnable = object : Runnable {
        override fun run() {
            if (currentState == WebSocketManager.State.CONNECTED) {
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

    // 初始化日志级别：优先使用 wsLogLevel，AUTO 模式下回退到 enableDebugLog
    init {
        val resolved = when (config.wsLogLevel) {
            WebSocketLogLevel.AUTO -> if (config.enableDebugLog) WebSocketLogLevel.FULL else WebSocketLogLevel.NONE
            else -> config.wsLogLevel
        }
        WebSocketLogger.setLevel(resolved)
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

    fun connect() {
        connectInternal(fromReconnect = false)
    }

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
            // 清除所有 pending 回调，防止已 post 的 dispatchCallback 继续执行
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

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
                    // 首次连接成功后立即发送一次心跳，避免部分服务端短空闲回收连接。
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
                WebSocketLogger.d(connectionId, "收到文本消息：$text")
                dispatchCallback { listener.onMessage(connectionId, text) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
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
                attemptReconnect()
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

                val isUnrecoverable = (response?.code in setOf(NetCode.Biz.UNAUTHORIZED, NetCode.Biz.FORBIDDEN, NetCode.Biz.NOT_FOUND)) ||
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

        // 到达最大重连次数后停止，防止无限重连耗尽电量
        if (config.maxReconnectAttempts > 0 && reconnectAttempt >= config.maxReconnectAttempts) {
            WebSocketLogger.w(
                connectionId,
                "已达最大重连次数(${config.maxReconnectAttempts})，停止重连"
            )
            isPermanentClose = true
            dispatchCallback { listener.onFailure(connectionId, IllegalStateException("Max reconnect attempts (${config.maxReconnectAttempts}) reached")) }
            return
        }

        reconnectAttempt++

        val baseDelay = config.reconnectBaseDelayMs * (1L shl min(reconnectAttempt - 1, 10))
        val delayWithJitter = (baseDelay * (0.9 + Random.nextDouble() * 0.2)).toLong()
        val finalDelay = delayWithJitter.coerceIn(1_000L, config.reconnectMaxDelayMs)
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
        pending.forEach { message ->
            when (message) {
                is QueuedMessage.Text -> sendMessage(message.content)
                is QueuedMessage.Binary -> sendMessage(message.data)
            }
        }
    }
}