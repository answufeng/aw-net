package com.answufeng.net.http.annotations

/** 网络日志级别，控制 OkHttp 日志输出的详细程度 */
enum class NetworkLogLevel {
    /** 不输出任何日志 */
    NONE,
    /** 仅输出请求行和响应行 */
    BASIC,
    /** 输出请求行、响应行及其头部 */
    HEADERS,
    /** 输出请求行、响应行、头部及请求体/响应体 */
    BODY
}
