package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.Retry
import com.answufeng.net.http.util.DefaultRetryStrategy
import com.answufeng.net.http.util.RetryStrategy
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class DynamicRetryInterceptor(
    private val fallbackStrategy: RetryStrategy = DefaultRetryStrategy()
) : Interceptor {

    private companion object {
        const val DEFAULT_INITIAL_BACKOFF_MS = 300L
        const val DEFAULT_MAX_BACKOFF_MS = 5_000L
        const val JITTER_BASE = 0.9
        const val JITTER_RANGE = 0.2
        const val MIN_BACKOFF_MS = 50L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val strategy = resolveStrategy(request)
            ?: return chain.proceed(request)

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

            val backoff = computeBackoffWithJitter(strategy.nextDelayMillis(attempt))
            try {
                Thread.sleep(backoff)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("retry interrupted")
            }
            attempt++
        }
    }

    private fun computeBackoffWithJitter(baseDelay: Long): Long {
        val jitterFactor = JITTER_BASE + Random.nextDouble() * JITTER_RANGE
        return (baseDelay * jitterFactor).toLong().coerceAtLeast(MIN_BACKOFF_MS)
    }

    private fun resolveStrategy(request: Request): RetryStrategy? {
        val invocation = request.tag(Invocation::class.java)
        val retry = invocation?.method()?.getAnnotation(Retry::class.java)

        if (retry != null) {
            if (retry.maxAttempts == 0) return null

            if (retry.maxAttempts > 0) {
                return AnnotationRetryStrategy(
                    maxRetries = retry.maxAttempts,
                    initialBackoffMillis = if (retry.initialBackoffMs > 0) retry.initialBackoffMs else DEFAULT_INITIAL_BACKOFF_MS,
                    maxBackoffMillis = if (retry.maxBackoffMs > 0) retry.maxBackoffMs else DEFAULT_MAX_BACKOFF_MS,
                    retryOnPost = retry.retryOnPost
                )
            }
        }

        return fallbackStrategy
    }

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
            return min(maxBackoffMillis, raw.toLong())
        }
    }
}
