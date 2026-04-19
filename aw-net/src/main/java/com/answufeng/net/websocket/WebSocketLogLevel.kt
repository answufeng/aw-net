package com.answufeng.net.websocket

/** WebSocket 日志级别，控制 WebSocket 通信日志输出的详细程度 */
enum class WebSocketLogLevel {
    /** 不输出任何日志 */
    NONE,
    /** 输出连接、断开等基本事件日志 */
    BASIC,
    /** 输出完整消息内容，包括帧数据和文本 */
    FULL
}
