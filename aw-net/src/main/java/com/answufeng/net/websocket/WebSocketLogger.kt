package com.answufeng.net.websocket

class WebSocketLogger(private val level: WebSocketLogLevel = WebSocketLogLevel.NONE) {

    private var customLogger: IWebSocketLogger? = null

    fun setLogger(l: IWebSocketLogger?) {
        customLogger = l
    }

    fun lifecycle(connectionId: String, message: String) {
        if (level >= WebSocketLogLevel.BASIC) {
            customLogger?.d(composeTag(connectionId), message)
        }
    }

    fun d(connectionId: String, message: String) {
        if (level >= WebSocketLogLevel.FULL) {
            customLogger?.d(composeTag(connectionId), message)
        }
    }

    fun i(connectionId: String, message: String) {
        if (level >= WebSocketLogLevel.FULL) {
            customLogger?.i(composeTag(connectionId), message)
        }
    }

    fun w(connectionId: String, message: String, throwable: Throwable? = null) {
        if (level >= WebSocketLogLevel.FULL) {
            customLogger?.w(composeTag(connectionId), message, throwable)
        }
    }

    fun e(connectionId: String, message: String, throwable: Throwable? = null) {
        if (level >= WebSocketLogLevel.BASIC) {
            customLogger?.e(composeTag(connectionId), message, throwable)
        }
    }

    companion object {
        private const val BASE_TAG = "WSClient"
        private const val MAX_TAG_LENGTH = 23
        private const val CONNECTION_ID_SUFFIX_LENGTH = 10

        fun composeTag(connectionId: String): String {
            val fullTag = "$BASE_TAG[$connectionId]"
            return if (fullTag.length > MAX_TAG_LENGTH) {
                "$BASE_TAG[..${connectionId.takeLast(CONNECTION_ID_SUFFIX_LENGTH)}]"
            } else {
                fullTag
            }
        }
    }
}
