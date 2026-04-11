package com.answufeng.net.http.util

import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.math.pow

/**
 * 重试策略接口，决定是否重试以及重试等待时间。
 */
interface RetryStrategy {
    /**
     * 判断是否应该重试。
     *
     * @param request 当前请求
     * @param response 响应（如果收到），可能为 null
     * @param error IO 异常（如果发生），可能为 null
     * @param attempt 当前尝试次数（0 开始）
     * @return 是否重试
     */
    fun shouldRetry(request: Request, response: Response?, error: IOException?, attempt: Int): Boolean

    /**
     * 计算下次重试前的等待时间。
     *
     * @param attempt 当前尝试次数
     * @return 等待时间（毫秒）
     */
    fun nextDelayMillis(attempt: Int): Long
}

/**
 * 默认重试策略：仅对幂等方法（GET/HEAD/PUT/DELETE/OPTIONS）在 IO 错误、5xx 或 429 (Too Many Requests) 响应时重试。
 *
 * @param maxRetries 最大重试次数
 * @param initialBackoffMillis 初始退避延迟（毫秒）
 * @param maxBackoffMillis 最大退避延迟（毫秒）
 * @param factor 退避倍数
 */
class DefaultRetryStrategy(
    private val maxRetries: Int = 2,
    private val initialBackoffMillis: Long = 300,
    private val maxBackoffMillis: Long = 5_000,
    private val factor: Double = 2.0
) : RetryStrategy {

    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0, got $maxRetries" }
        require(initialBackoffMillis > 0) { "initialBackoffMillis must be > 0, got $initialBackoffMillis" }
        require(maxBackoffMillis >= initialBackoffMillis) { "maxBackoffMillis must be >= initialBackoffMillis" }
        require(factor >= 1.0) { "factor must be >= 1.0, got $factor" }
    }

    private val idempotentMethods = setOf("GET", "HEAD", "PUT", "DELETE", "OPTIONS")

    override fun shouldRetry(request: Request, response: Response?, error: IOException?, attempt: Int): Boolean {
        if (attempt >= maxRetries) return false
        if (!idempotentMethods.contains(request.method.uppercase())) return false
        if (error != null) return true
        val code = response?.code ?: return false
        return code in 500..599 || code == 429
    }

    override fun nextDelayMillis(attempt: Int): Long {
        val raw = initialBackoffMillis * factor.pow(attempt.toDouble())
        return raw.toLong().coerceAtMost(maxBackoffMillis)
    }
}
