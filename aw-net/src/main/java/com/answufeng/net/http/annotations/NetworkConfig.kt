package com.answufeng.net.http.annotations

import com.answufeng.net.http.model.ResponseFieldMapping
import okhttp3.CookieJar
import java.io.File

/**
 * 网络配置。
 *
 * 字段分为两类：
 * - **运行时可变**：通过 [NetworkConfigProvider.updateConfig] 修改后立即生效（baseUrl、networkLogLevel、extraHeaders、defaultSuccessCode 等）
 * - **启动时固化**：OkHttpClient 创建后不可变（maxIdleConnections、keepAliveDurationSeconds、certificatePins、cacheDir/cacheSize）
 *
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
    val sensitiveBodyFields: Set<String> = DEFAULT_SENSITIVE_BODY_FIELDS
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
            sensitiveBodyFields = sensitiveBodyFields
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
        require(retryInitialBackoffMs >= 0) {
            "NetworkConfig.retryInitialBackoffMs must be >= 0"
        }
        require(maxIdleConnections > 0) {
            "NetworkConfig.maxIdleConnections must be > 0, actual: $maxIdleConnections"
        }
        require(keepAliveDurationSeconds > 0) {
            "NetworkConfig.keepAliveDurationSeconds must be > 0, actual: $keepAliveDurationSeconds"
        }
        require((cacheDir == null) == (cacheSize == null || cacheSize <= 0)) {
            "NetworkConfig: cacheDir and cacheSize must be provided together to enable HTTP cache."
        }
    }
}
