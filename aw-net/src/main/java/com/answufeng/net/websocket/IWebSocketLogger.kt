package com.answufeng.net.websocket

/**
 * WebSocket 日志接口
 * 与 HTTP 日志完全独立，可分别配置
 * @since 1.0.0
 */interface IWebSocketLogger {
    fun d(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}
