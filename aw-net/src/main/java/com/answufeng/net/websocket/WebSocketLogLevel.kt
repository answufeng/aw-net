package com.answufeng.net.websocket

/**
 * WebSocket 日志级别枚举，与 HTTP [com.answufeng.net.http.annotations.NetworkLogLevel] 独立控制。
 *
 * - [AUTO]：自动模式，沿用 [WebSocketManager.Config.enableDebugLog] 的行为向后兼容
 * - [NONE]：不输出任何日志（错误日志也会被屏蔽）
 * - [BASIC]：仅输出连接生命周期（连接/断开/重连/错误）
 * - [FULL]：输出全部日志（含消息收发内容、心跳、队列状态等）
 */
enum class WebSocketLogLevel {
    /** 自动模式：由 [WebSocketManager.Config.enableDebugLog] 决定 */
    AUTO,
    /** 不输出任何日志 */
    NONE,
    /** 仅连接生命周期：连接 / 断开 / 重连 / 错误 */
    BASIC,
    /** 完整日志：含消息内容、心跳、队列等 */
    FULL
}
