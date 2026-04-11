package com.answufeng.net.websocket

/**
 * WebSocket 内部日志工具。
 *
 * 支持 [WebSocketLogLevel] 级别控制，与 HTTP 日志开关完全独立。
 *
 * | 级别 | 生命周期日志 | 消息内容日志 | 错误日志 |
 * |------|:-----------:|:-----------:|:-------:|
 * | NONE | ✗ | ✗ | ✗ |
 * | BASIC| ✓ | ✗ | ✓ |
 * | FULL | ✓ | ✓ | ✓ |
 */
object WebSocketLogger {
    private const val BASE_TAG = "WSClient"

    @Volatile
    private var logLevel: WebSocketLogLevel = WebSocketLogLevel.NONE

    private var customLogger: IWebSocketLogger? = null

    /** 设置自定义日志实现 */
    fun setLogger(l: IWebSocketLogger?) {
        customLogger = l
    }

    /**
     * 设置日志级别。
     *
     * @see WebSocketLogLevel
     */
    fun setLevel(level: WebSocketLogLevel) {
        logLevel = level
    }

    /**
     * 兼容旧 API：通过布尔值设置日志开关。
     * - true → [WebSocketLogLevel.FULL]
     * - false → [WebSocketLogLevel.NONE]
     */
    fun setLogEnabled(enabled: Boolean) {
        logLevel = if (enabled) WebSocketLogLevel.FULL else WebSocketLogLevel.NONE
    }

    /** 当前生效的日志级别 */
    fun currentLevel(): WebSocketLogLevel = logLevel

    // ==================== 生命周期日志（BASIC 及以上） ====================

    /**
     * 输出生命周期日志（连接/断开/重连），[WebSocketLogLevel.BASIC] 及以上输出。
     */
    fun lifecycle(connectionId: String, message: String) {
        if (logLevel >= WebSocketLogLevel.BASIC) {
            customLogger?.d(composeTag(connectionId), message)
        }
    }

    // ==================== 详细日志（仅 FULL） ====================

    /**
     * 输出调试日志（消息收发、心跳、队列），仅 [WebSocketLogLevel.FULL] 输出。
     */
    fun d(connectionId: String, message: String) {
        if (logLevel >= WebSocketLogLevel.FULL) {
            customLogger?.d(composeTag(connectionId), message)
        }
    }

    /**
     * 输出信息日志，仅 [WebSocketLogLevel.FULL] 输出。
     */
    fun i(connectionId: String, message: String) {
        if (logLevel >= WebSocketLogLevel.FULL) {
            customLogger?.d(composeTag(connectionId), message)
        }
    }

    /**
     * 输出警告日志，仅 [WebSocketLogLevel.FULL] 输出。
     */
    fun w(connectionId: String, message: String, throwable: Throwable? = null) {
        if (logLevel >= WebSocketLogLevel.FULL) {
            customLogger?.e(composeTag(connectionId), message, throwable)
        }
    }

    // ==================== 错误日志（BASIC 及以上） ====================

    /**
     * 输出错误日志，[WebSocketLogLevel.BASIC] 及以上输出。
     * 仅 [WebSocketLogLevel.NONE] 时被完全屏蔽。
     */
    fun e(connectionId: String, message: String, throwable: Throwable? = null) {
        if (logLevel >= WebSocketLogLevel.BASIC) {
            customLogger?.e(composeTag(connectionId), message, throwable)
        }
    }

    /** 组合日志标签，处理超长标签 */
    private fun composeTag(connectionId: String): String {
        val fullTag = "$BASE_TAG[$connectionId]"
        return if (fullTag.length > 23) {
            "$BASE_TAG[..${connectionId.takeLast(10)}]"
        } else {
            fullTag
        }
    }
}