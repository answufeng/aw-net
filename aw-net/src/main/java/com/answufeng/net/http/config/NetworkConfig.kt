package com.answufeng.net.http.config

import com.answufeng.net.http.model.ResponseFieldMapping
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

/**
 * 网络配置。
 *
 * 字段分为两类：
 * - **运行时可变**（[NetworkConfigProvider] 的 [com.answufeng.net.http.config.NetworkConfigProvider.current] 快照在拦截器里读取，改后立即影响新请求）：
 *   [baseUrl]（由 [com.answufeng.net.http.interceptor.DynamicBaseUrlInterceptor] 应用）、[networkLogLevel]、
 *   [extraHeaders]、[defaultSuccessCode]、[responseFieldMapping]（Gson 在解析时从 provider 取当前映射）、[sensitiveHeaders] / [sensitiveBodyFields]（日志脱敏）、[enableRequestTracking] 等。
 * - **仅构建 OkHttp/Retrofit 时读取一次、之后改 [updateConfig] 不生效，除非自管重建 Client/Retrofit**：
 *   [connectTimeout] / [readTimeout] / [writeTimeout]、[maxIdleConnections] / [keepAliveDurationSeconds]、
 *   [certificatePins]、[cacheDir]/[cacheSize]、[cookieJar]；以及 Hilt 里基于首次快照装配的
 *   [com.answufeng.net.http.interceptor.DynamicRetryInterceptor]（若 [enableRetryInterceptor]）、
 *   [com.answufeng.net.http.auth.TokenAuthenticator]、[com.answufeng.net.http.util.NetworkClientFactory] 的 Gson/Converter。
 * 单请求超时请使用 Retrofit 方法上的 [com.answufeng.net.http.annotations.Timeout]（[com.answufeng.net.http.interceptor.DynamicTimeoutInterceptor] 按请求覆盖 `OkHttpClient` 的 chain 超时，不依赖在运行时改全局 [connectTimeout]）。
 *
 * @param enableRequestTracking 为 false 时，库内请求/上传/下载不再通过 [com.answufeng.net.http.util.NetTracker] 发送起止事件，降低高 QPS 场景开销，不影响业务结果。
 *
 * [baseUrl] 会校验为可解析的 http(s) URL、禁止 **query** 与 **fragment**（`?` / `#`），与 Retrofit 的 baseUrl 约定一致；
 * [extraHeaders] 的键/值会按 OkHttp 规则校验。合法路径前缀（如 `https://api.com/v1/`）允许。
 * [tokenRefreshLockAcquireTimeoutMs] 在 Hilt 注入 [com.answufeng.net.http.auth.TokenRefreshCoordinator] 时传入；单例在进程内只构建一次，运行时再次 [NetworkConfigProvider.update] **不会**改变已创建协调器的超时，除非自行提供协调器实现。
 */
data class NetworkConfig(
    val baseUrl: String,
    val connectTimeout: Long = 15L,
    val readTimeout: Long = 15L,
    val writeTimeout: Long = 15L,
    val defaultSuccessCode: Int = 0,
    val networkLogLevel: NetworkLogLevel = NetworkLogLevel.NONE,
    val extraHeaders: Map<String, String> = emptyMap(),
    val cacheDir: File? = null,
    val cacheSize: Long? = null,
    val enableRetryInterceptor: Boolean = false,
    val retryMaxAttempts: Int = 2,
    val retryInitialBackoffMs: Long = 300L,
    val responseFieldMapping: ResponseFieldMapping = ResponseFieldMapping(),
    val maxIdleConnections: Int = 5,
    val keepAliveDurationSeconds: Long = 300L,
    val certificatePins: List<CertificatePin> = emptyList(),
    val cookieJar: CookieJar? = null,
    val sensitiveHeaders: Set<String> = DEFAULT_SENSITIVE_HEADERS,
    val sensitiveBodyFields: Set<String> = DEFAULT_SENSITIVE_BODY_FIELDS,
    val enableRequestTracking: Boolean = true,
    val tokenRefreshLockAcquireTimeoutMs: Long = 60_000L
) {

    companion object {
        val DEFAULT_SENSITIVE_HEADERS: Set<String> = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-auth-token",
            "token",
            "x-api-key",
            "x-token"
        )

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

        fun builder(baseUrl: String): Builder = Builder(baseUrl)
    }

    fun toBuilder(): Builder = Builder(baseUrl).apply {
        connectTimeout = this@NetworkConfig.connectTimeout
        readTimeout = this@NetworkConfig.readTimeout
        writeTimeout = this@NetworkConfig.writeTimeout
        defaultSuccessCode = this@NetworkConfig.defaultSuccessCode
        networkLogLevel = this@NetworkConfig.networkLogLevel
        extraHeaders = this@NetworkConfig.extraHeaders
        cacheDir = this@NetworkConfig.cacheDir
        cacheSize = this@NetworkConfig.cacheSize
        enableRetryInterceptor = this@NetworkConfig.enableRetryInterceptor
        retryMaxAttempts = this@NetworkConfig.retryMaxAttempts
        retryInitialBackoffMs = this@NetworkConfig.retryInitialBackoffMs
        responseFieldMapping = this@NetworkConfig.responseFieldMapping
        maxIdleConnections = this@NetworkConfig.maxIdleConnections
        keepAliveDurationSeconds = this@NetworkConfig.keepAliveDurationSeconds
        certificatePins = this@NetworkConfig.certificatePins
        cookieJar = this@NetworkConfig.cookieJar
        sensitiveHeaders = this@NetworkConfig.sensitiveHeaders
        sensitiveBodyFields = this@NetworkConfig.sensitiveBodyFields
        enableRequestTracking = this@NetworkConfig.enableRequestTracking
        tokenRefreshLockAcquireTimeoutMs = this@NetworkConfig.tokenRefreshLockAcquireTimeoutMs
    }

    class Builder(private val baseUrl: String) {
        var connectTimeout: Long = 15L
        var readTimeout: Long = 15L
        var writeTimeout: Long = 15L
        var defaultSuccessCode: Int = 0
        var networkLogLevel: NetworkLogLevel = NetworkLogLevel.NONE
        var extraHeaders: Map<String, String> = emptyMap()
        var cacheDir: File? = null
        var cacheSize: Long? = null
        var enableRetryInterceptor: Boolean = false
        var retryMaxAttempts: Int = 2
        var retryInitialBackoffMs: Long = 300L
        var responseFieldMapping: ResponseFieldMapping = ResponseFieldMapping()
        var maxIdleConnections: Int = 5
        var keepAliveDurationSeconds: Long = 300L
        var certificatePins: List<CertificatePin> = emptyList()
        var cookieJar: CookieJar? = null
        var sensitiveHeaders: Set<String> = DEFAULT_SENSITIVE_HEADERS
        var sensitiveBodyFields: Set<String> = DEFAULT_SENSITIVE_BODY_FIELDS
        var enableRequestTracking: Boolean = true
        var tokenRefreshLockAcquireTimeoutMs: Long = 60_000L

        fun build(): NetworkConfig = NetworkConfig(
            baseUrl = baseUrl,
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            writeTimeout = writeTimeout,
            defaultSuccessCode = defaultSuccessCode,
            networkLogLevel = networkLogLevel,
            extraHeaders = extraHeaders,
            cacheDir = cacheDir,
            cacheSize = cacheSize,
            enableRetryInterceptor = enableRetryInterceptor,
            retryMaxAttempts = retryMaxAttempts,
            retryInitialBackoffMs = retryInitialBackoffMs,
            responseFieldMapping = responseFieldMapping,
            maxIdleConnections = maxIdleConnections,
            keepAliveDurationSeconds = keepAliveDurationSeconds,
            certificatePins = certificatePins,
            cookieJar = cookieJar,
            sensitiveHeaders = sensitiveHeaders,
            sensitiveBodyFields = sensitiveBodyFields,
            enableRequestTracking = enableRequestTracking,
            tokenRefreshLockAcquireTimeoutMs = tokenRefreshLockAcquireTimeoutMs
        )
    }

    init {
        require(baseUrl.isNotBlank()) {
            "NetworkConfig.baseUrl must not be blank."
        }
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "NetworkConfig.baseUrl must start with http:// or https://, actual: $baseUrl"
        }
        val parsedBase = baseUrl.toHttpUrlOrNull()
        require(parsedBase != null) {
            "NetworkConfig.baseUrl could not be parsed as HttpUrl, actual: $baseUrl"
        }
        require(parsedBase.encodedQuery.isNullOrEmpty()) {
            "NetworkConfig.baseUrl must not include a query string, actual: $baseUrl"
        }
        require(parsedBase.encodedFragment.isNullOrEmpty()) {
            "NetworkConfig.baseUrl must not include a fragment (#), actual: $baseUrl"
        }
        require(baseUrl.endsWith('/')) {
            "NetworkConfig.baseUrl must end with '/'. Retrofit baseUrl requires trailing slash, actual: $baseUrl"
        }
        require(connectTimeout in 1..300) {
            "NetworkConfig.connectTimeout should be between 1 and 300 seconds, actual: $connectTimeout"
        }
        require(readTimeout in 1..300) {
            "NetworkConfig.readTimeout should be between 1 and 300 seconds, actual: $readTimeout"
        }
        require(writeTimeout in 1..300) {
            "NetworkConfig.writeTimeout should be between 1 and 300 seconds, actual: $writeTimeout"
        }
        require(retryMaxAttempts >= 0) {
            "NetworkConfig.retryMaxAttempts must be >= 0"
        }
        require(maxIdleConnections > 0) {
            "NetworkConfig.maxIdleConnections must be > 0, actual: $maxIdleConnections"
        }
        require(keepAliveDurationSeconds > 0) {
            "NetworkConfig.keepAliveDurationSeconds must be > 0, actual: $keepAliveDurationSeconds"
        }
        require(retryInitialBackoffMs >= 1L) {
            "NetworkConfig.retryInitialBackoffMs must be >= 1 ms to avoid hot-loop retries and server load, actual: $retryInitialBackoffMs"
        }
        require(tokenRefreshLockAcquireTimeoutMs in 0L..300_000L) {
            "NetworkConfig.tokenRefreshLockAcquireTimeoutMs must be 0 (non-blocking try only) or 1..300000, actual: $tokenRefreshLockAcquireTimeoutMs"
        }
        require((cacheDir == null) == (cacheSize == null || cacheSize <= 0)) {
            "NetworkConfig: cacheDir and cacheSize must be provided together to enable HTTP cache."
        }
        extraHeaders.forEach { (name, value) ->
            runCatching {
                Headers.Builder().add(name, value).build()
            }.getOrElse { ex ->
                throw IllegalArgumentException(
                    "NetworkConfig.extraHeaders has invalid header name or value (OkHttp rules): " +
                        "name=[$name]",
                    ex
                )
            }
        }
    }
}
