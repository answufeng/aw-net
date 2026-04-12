package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.Retry
import com.answufeng.net.http.util.DefaultRetryStrategy
import com.answufeng.net.http.util.RetryStrategy
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException
import kotlin.math.pow

/**
 * 动态重试拦截器，支持通过 [Retry] 注解实现按接口重试配置。
 *
 * 优先级：`@Retry` 注解 > 全局 [fallbackStrategy]
 *
 * 工作流程：
 * 1. 从 Retrofit [Invocation] tag 读取方法上的 `@Retry` 注解
 * 2. 如果 `@Retry(maxAttempts = 0)` — 禁止重试，直接执行
 * 3. 如果有 `@Retry` 且 maxAttempts > 0 — 使用注解中的配置构建策略
 * 4. 如果无注解 — 使用 [fallbackStrategy]（全局配置）
 *
 * 注意：当 `@Retry(retryOnPost = true)` 时，允许对 POST 请求重试。
 * 默认情况下仅对幂等方法（GET/HEAD/PUT/DELETE/OPTIONS）重试。
 * @since 1.0.0
 */
class DynamicRetryInterceptor(
    private val fallbackStrategy: RetryStrategy = DefaultRetryStrategy()
) : Interceptor {

    private companion object {
        const val DEFAULT_INITIAL_BACKOFF_MS = 300L
        const val DEFAULT_MAX_BACKOFF_MS = 5_000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val strategy = resolveStrategy(request)
            ?: return chain.proceed(request) // @Retry(maxAttempts=0) → 禁止重试

        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (!strategy.shouldRetry(request, response, null, attempt)) {
                    return response
                }
                response.close()
            } catch (ioe: IOException) {
                if (!strategy.shouldRetry(request, null, ioe, attempt)) {
                    throw ioe
                }
            }

            val backoff = strategy.nextDelayMillis(attempt)
            try {
                Thread.sleep(backoff)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("retry interrupted")
            }
            attempt++
        }
    }

    /**
     * 解析请求的重试策略。
     * @return 策略实例；若返回 null 表示该请求禁止重试
     * @since 1.0.0
 */
    private fun resolveStrategy(request: Request): RetryStrategy? {
        val invocation = request.tag(Invocation::class.java)
        val retry = invocation?.method()?.getAnnotation(Retry::class.java)

        if (retry != null) {
            // @Retry(maxAttempts = 0) 明确禁止重试
            if (retry.maxAttempts == 0) return null

            // @Retry 有自定义配置（maxAttempts > 0）
            if (retry.maxAttempts > 0) {
                return AnnotationRetryStrategy(
                    maxRetries = retry.maxAttempts,
                    initialBackoffMillis = if (retry.initialBackoffMs > 0) retry.initialBackoffMs else DEFAULT_INITIAL_BACKOFF_MS,
                    maxBackoffMillis = if (retry.maxBackoffMs > 0) retry.maxBackoffMs else DEFAULT_MAX_BACKOFF_MS,
                    retryOnPost = retry.retryOnPost
                )
            }
            // @Retry(maxAttempts = -1) 表示使用全局配置，继续往下
        }

        return fallbackStrategy
    }

    /**
     * 基于 @Retry 注解参数构建的重试策略。
     * 与 [DefaultRetryStrategy] 类似，但支持 [retryOnPost] 控制 POST 请求是否可重试。
     * @since 1.0.0
 */
    private class AnnotationRetryStrategy(
        private val maxRetries: Int,
        private val initialBackoffMillis: Long,
        private val maxBackoffMillis: Long,
        private val retryOnPost: Boolean,
        private val factor: Double = 2.0
    ) : RetryStrategy {

        private val idempotentMethods = setOf("GET", "HEAD", "PUT", "DELETE", "OPTIONS")

        override fun shouldRetry(request: Request, response: Response?, error: IOException?, attempt: Int): Boolean {
            if (attempt >= maxRetries) return false
            val method = request.method.uppercase()
            if (!idempotentMethods.contains(method) && !(retryOnPost && method == "POST")) return false
            if (error != null) return true
            val code = response?.code ?: return false
            return RetryStrategy.isRetryableHttpCode(code)
        }

        override fun nextDelayMillis(attempt: Int): Long {
            val raw = initialBackoffMillis * factor.pow(attempt.toDouble())
            return raw.toLong().coerceAtMost(maxBackoffMillis)
        }
    }
}
