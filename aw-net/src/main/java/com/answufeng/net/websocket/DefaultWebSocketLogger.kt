package com.answufeng.net.websocket

/**
 * WebSocket 日志记录器实现。
 *
 * 根据 [WebSocketLogLevel] 控制日志输出级别，支持注入自定义的 [WebSocketLogger] 实现。
 */
internal class DefaultWebSocketLogger(
    private val logLevel: WebSocketLogLevel,
    private var customLogger: WebSocketLogger? = null
) : WebSocketLogger {

    companion object {
        private const val TAG_PREFIX = "WS"
    }

    override fun d(tag: String, msg: String) {
        if (logLevel == WebSocketLogLevel.NONE) return
        customLogger?.d("$TAG_PREFIX-$tag", msg)
    }

    override fun i(tag: String, msg: String) {
        if (logLevel == WebSocketLogLevel.NONE) return
        customLogger?.i("$TAG_PREFIX-$tag", msg)
    }

    override fun w(tag: String, msg: String, throwable: Throwable?) {
        if (logLevel == WebSocketLogLevel.NONE) return
        customLogger?.w("$TAG_PREFIX-$tag", msg, throwable)
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        if (logLevel == WebSocketLogLevel.NONE) return
        customLogger?.e("$TAG_PREFIX-$tag", msg, throwable)
    }

    /**
     * 输出生命周期相关日志（连接、断开等）。
     */
    fun lifecycle(tag: String, msg: String) {
        if (logLevel == WebSocketLogLevel.NONE) return
        customLogger?.i("$TAG_PREFIX-LIFECYCLE-$tag", msg)
    }

    /**
     * 设置自定义日志实现。
     */
    fun setLogger(logger: WebSocketLogger) {
        this.customLogger = logger
    }
}
