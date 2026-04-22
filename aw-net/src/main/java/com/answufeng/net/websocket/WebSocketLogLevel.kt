package com.answufeng.net.websocket

/**
 * 控制 [com.answufeng.net.websocket.WebSocketManager] 中默认日志的详细程度，与 HTTP 侧的 [com.answufeng.net.http.config.NetworkLogLevel] 独立。
 */
enum class WebSocketLogLevel {
    /** 不输出。 */
    NONE,
    /** 建连/断连/重连等生命周期。 */
    BASIC,
    /** 在 BASIC 之外再输出**消息体**等更细内容，生产请慎用。 */
    FULL
}
