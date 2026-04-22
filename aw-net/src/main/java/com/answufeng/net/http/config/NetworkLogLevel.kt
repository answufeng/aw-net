package com.answufeng.net.http.config

/** 网络日志级别，控制 OkHttp 日志输出的详细程度（由 [com.answufeng.net.http.interceptor.DynamicLoggingInterceptor] 映射到 [okhttp3.logging.HttpLoggingInterceptor.Level]）。 */
enum class NetworkLogLevel {
    /** 不输出任何日志 */
    NONE,
    /** 仅输出请求行和响应行 */
    BASIC,
    /**
     * 输出请求行、响应行及 Header。
     * 注意生产环境大流量时仍可能带敏感元数据，[com.answufeng.net.http.interceptor.PrettyNetLogger] 仅对可识别的 Header/JSON 行脱敏，无法保证所有路径安全。
     */
    HEADERS,
    /**
     * 输出含请求体/响应体在内的全部细节。
     * **生产环境慎用**：大 Body、文件、二进制、未模型化的敏感字段可能进入日志与 logcat 缓冲区，即使开启 JSON 与 Header 的脱敏亦无法覆盖所有格式。
     */
    BODY
}
