package com.answufeng.net.http.annotations

/**
 * HTTP 日志级别枚举。
 *
 * - [AUTO]：自动模式，沿用 [NetworkConfig.isLogEnabled] 的行为向后兼容
 * - [NONE]：不输出任何日志
 * - [BASIC]：仅输出请求方法、URL 和响应码
 * - [HEADERS]：输出请求/响应头
 * - [BODY]：输出完整请求体和响应体
 * @since 1.0.0
 */enum class NetworkLogLevel {
    /** 自动模式：由 [NetworkConfig.isLogEnabled] 决定 
    * @since 1.0.0
 */    AUTO,
    /** 不输出日志 
    * @since 1.0.0
 */    NONE,
    /** 仅请求行和响应码 
    * @since 1.0.0
 */    BASIC,
    /** 请求行 + 请求/响应头 
    * @since 1.0.0
 */    HEADERS,
    /** 完整日志（含请求体和响应体） 
    * @since 1.0.0
 */    BODY
}
