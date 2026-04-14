package com.answufeng.net.websocket

object WebSocketLogger {
    private const val BASE_TAG = "WSClient"
    private const val MAX_TAG_LENGTH = 23
    private const val CONNECTION_ID_SUFFIX_LENGTH = 10

    @Volatile
    private var logLevel: WebSocketLogLevel = WebSocketLogLevel.NONE

    @Volatile
    private var customLogger: IWebSocketLogger? = null

    fun setLogger(l: IWebSocketLogger?) {
        customLogger = l
    }

    fun setLevel(level: WebSocketLogLevel) {
        logLevel = level
    }

    fun currentLevel(): WebSocketLogLevel = logLevel

    fun lifecycle(connectionId: String, message: String) {
        if (logLevel >= WebSocketLogLevel.BASIC) {
            customLogger?.d(composeTag(connectionId), message)
        }
    }

    fun d(connectionId: String, message: String) {
        if (logLevel >= WebSocketLogLevel.FULL) {
            customLogger?.d(composeTag(connectionId), message)
        }
    }

    fun i(connectionId: String, message: String) {
        if (logLevel >= WebSocketLogLevel.FULL) {
            customLogger?.d(composeTag(connectionId), message)
        }
    }

    fun w(connectionId: String, message: String, throwable: Throwable? = null) {
        if (logLevel >= WebSocketLogLevel.FULL) {
            customLogger?.e(composeTag(connectionId), message, throwable)
        }
    }

    fun e(connectionId: String, message: String, throwable: Throwable? = null) {
        if (logLevel >= WebSocketLogLevel.BASIC) {
            customLogger?.e(composeTag(connectionId), message, throwable)
        }
    }

    private fun composeTag(connectionId: String): String {
        val fullTag = "$BASE_TAG[$connectionId]"
        return if (fullTag.length > MAX_TAG_LENGTH) {
            "$BASE_TAG[..${connectionId.takeLast(CONNECTION_ID_SUFFIX_LENGTH)}]"
        } else {
            fullTag
        }
    }
}
