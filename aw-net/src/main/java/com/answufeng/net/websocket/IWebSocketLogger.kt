package com.answufeng.net.websocket

/**
 * WebSocket 日志接口，与 HTTP 日志完全独立，可分别配置。
 *
 * 通过 [WebSocketLogger.setLogger] 注入自定义实现。
 * @since 1.0.0
 */
interface IWebSocketLogger {
    /**
     * 输出调试级别日志。
     * @param tag 日志标签
     * @param msg 日志消息
     * @since 1.0.0
$     */
    fun d(tag: String, msg: String)

    /**
     * 输出错误级别日志。
     * @param tag 日志标签
     * @param msg 日志消息
     * @param throwable 可选的异常对象
     * @since 1.0.0
$     */
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}
