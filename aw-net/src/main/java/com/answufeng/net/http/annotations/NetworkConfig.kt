package com.answufeng.net.http.annotations

import com.answufeng.net.http.model.ResponseFieldMapping
import java.io.File

/**
 * 全局网络配置参数（项目层提供）
 * 优先级：单接口配置 > 本配置 > 基础库默认值
 *
 * 注意：
 * - 本配置在应用启动阶段即被创建，推荐遵循 Fail-Fast 原则，在这里做基础校验，
 *   避免运行时才因为 baseUrl / 超时配置错误导致难以排查的崩溃。
 *
 * @param baseUrl 必须由项目层提供，基础库不提供默认值，且必须以 http/https 开头
 * @param connectTimeout 连接超时（秒），不设则用基础库默认 15
 * @param readTimeout 读取超时（秒），不设则用基础库默认 15
 * @param writeTimeout 写入超时（秒），不设则用基础库默认 15
 * @param defaultSuccessCode 全局默认成功码，不设则用基础库默认 0
 * @param isLogEnabled 是否开启网络日志，不设则用基础库默认 false
 * @param networkLogLevel 网络日志级别；AUTO 表示沿用 isLogEnabled 兼容行为
 * @param extraHeaders 每次请求都会带上的公共请求头（如 X-App-Version、X-Version-Code），由项目层提供
 * @param cacheDir 可选：OkHttp 缓存目录（若提供且 cacheSize 也提供则启用 Cache）
 * @param cacheSize 可选：缓存大小（字节），与 cacheDir 配合使用
 * @param enableRetryInterceptor 如果为 true，NetworkModule 会在 OkHttpClient 中注册 RetryInterceptor
 * @param retryMaxAttempts RetryInterceptor 的最大重试次数（默认 2）
 * @param retryInitialBackoffMs Retry 初始退避毫秒数（默认 300ms）
 * @param responseFieldMapping 全局响应字段映射，默认按 {code,msg,data} 解析
 * @param maxIdleConnections 连接池最大空闲连接数（默认 5），按并发量调整
 * @param keepAliveDurationSeconds 连接池空闲连接存活时间（秒，默认 300），长连接场景可适当增大
 * @param certificatePins SSL 证书固定配置列表，用于防止中间人攻击。每项包含域名模式和对应的 SHA-256 pin
 */
data class NetworkConfig(
    val baseUrl: String,
    val connectTimeout: Long = 15L,
    val readTimeout: Long = 15L,
    val writeTimeout: Long = 15L,
    val defaultSuccessCode: Int = 0,
    val isLogEnabled: Boolean = false,
    val networkLogLevel: NetworkLogLevel = NetworkLogLevel.AUTO,
    val extraHeaders: Map<String, String> = emptyMap(),
    val cacheDir: File? = null,
    val cacheSize: Long? = null,
    val enableRetryInterceptor: Boolean = false,
    val retryMaxAttempts: Int = 2,
    val retryInitialBackoffMs: Long = 300L,
    val responseFieldMapping: ResponseFieldMapping = ResponseFieldMapping(),
    /**
     * 连接池最大空闲连接数。
     *
     * OkHttp 默认保持 5 个空闲连接。对于高并发场景可适当增大，
     * 低频场景可减小以节省资源：
     *
     * ```kotlin
     * NetworkConfig(
     *     baseUrl = "...",
     *     maxIdleConnections = 10  // 高并发场景
     * )
     * ```
     */
    val maxIdleConnections: Int = 5,
    /**
     * 连接池空闲连接存活时间（秒）。
     *
     * OkHttp 默认 5 分钟（300 秒）。长连接场景可适当增大：
     *
     * ```kotlin
     * NetworkConfig(
     *     baseUrl = "...",
     *     keepAliveDurationSeconds = 600  // 10 分钟
     * )
     * ```
     */
    val keepAliveDurationSeconds: Long = 300L,
    /**
     * SSL 证书固定（Certificate Pinning）配置。
     *
     * 用于防止中间人攻击，将域名绑定到特定的证书公钥哈希。
     * 每项为 [CertificatePin]，包含域名模式和 SHA-256 pin 值。
     *
     * ```kotlin
     * NetworkConfig(
     *     baseUrl = "https://api.example.com/",
     *     certificatePins = listOf(
     *         CertificatePin(
     *             pattern = "api.example.com",
     *             pins = listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
     *         )
     *     )
     * )
     * ```
     *
     * 注意：证书固定需要在证书轮换前更新 pin 值，否则会导致连接失败。
     * 建议同时配置当前证书和备用证书的 pin。
     */
    val certificatePins: List<CertificatePin> = emptyList(),
    /**
     * 日志脱敏 Header 名称集合（比较时忽略大小写）。
     *
     * 当网络日志输出请求/响应头时，匹配到的 Header 值会被替换为 `****(masked)`。
     * 默认包含常见鉴权相关 Header，可按需扩展：
     *
     * ```kotlin
     * NetworkConfig(
     *     baseUrl = "...",
     *     sensitiveHeaders = NetworkConfig.DEFAULT_SENSITIVE_HEADERS + setOf("x-custom-secret")
     * )
     * ```
     */
    val sensitiveHeaders: Set<String> = DEFAULT_SENSITIVE_HEADERS,
    /**
     * 日志脱敏 Body 字段名集合（比较时忽略大小写）。
     *
     * 当网络日志输出 JSON Body 时，匹配到的字段值会被替换为 `"****(masked)"`。
     * 默认包含常见敏感字段，可按需扩展：
     *
     * ```kotlin
     * NetworkConfig(
     *     baseUrl = "...",
     *     sensitiveBodyFields = NetworkConfig.DEFAULT_SENSITIVE_BODY_FIELDS + setOf("id_card")
     * )
     * ```
     */
    val sensitiveBodyFields: Set<String> = DEFAULT_SENSITIVE_BODY_FIELDS
) {

    companion object {
        /** 默认脱敏 Header 集合 */
        val DEFAULT_SENSITIVE_HEADERS: Set<String> = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-auth-token",
            "token",
            "x-api-key",
            "x-token"
        )

        /** 默认脱敏 Body 字段集合 */
        val DEFAULT_SENSITIVE_BODY_FIELDS: Set<String> = setOf(
            "password",
            "pwd",
            "secret",
            "credit_card",
            "creditCard",
            "card_number",
            "cardNumber",
            "cvv",
            "ssn",
            "id_card",
            "idCard"
        )
    }

    init {
        require(baseUrl.isNotBlank()) {
            "NetworkConfig.baseUrl must not be blank."
        }
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "NetworkConfig.baseUrl must start with http:// or https://, actual: $baseUrl"
        }
        require(baseUrl.endsWith('/')) {
            "NetworkConfig.baseUrl must end with '/'. Retrofit baseUrl requires trailing slash, actual: $baseUrl"
        }
        require(connectTimeout > 0) {
            "NetworkConfig.connectTimeout must be > 0 seconds, actual: $connectTimeout"
        }
        require(readTimeout > 0) {
            "NetworkConfig.readTimeout must be > 0 seconds, actual: $readTimeout"
        }
        require(writeTimeout > 0) {
            "NetworkConfig.writeTimeout must be > 0 seconds, actual: $writeTimeout"
        }
        require(retryMaxAttempts >= 0) {
            "NetworkConfig.retryMaxAttempts must be >= 0"
        }
        require(retryInitialBackoffMs >= 0) {
            "NetworkConfig.retryInitialBackoffMs must be >= 0"
        }
        require(maxIdleConnections > 0) {
            "NetworkConfig.maxIdleConnections must be > 0, actual: $maxIdleConnections"
        }
        require(keepAliveDurationSeconds > 0) {
            "NetworkConfig.keepAliveDurationSeconds must be > 0, actual: $keepAliveDurationSeconds"
        }
    }
}
