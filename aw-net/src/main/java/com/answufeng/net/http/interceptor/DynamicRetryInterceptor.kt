package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.Retry
import com.answufeng.net.http.util.DefaultRetryStrategy
import com.answufeng.net.http.util.RetryStrategy
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * 在应用层拦截器链上执行带退避的 HTTP 重试。
 *
 * 退避使用 [Thread.sleep] 在 **当前 OkHttp 工作线程** 上执行，会阻塞该线程直至睡眠结束，高并发与长退避时可能占用线程池；
 * 对「大流量 + 可恢复错误」可优先依赖 [com.answufeng.net.http.util.RequestExecutor] 的协程重试，而非仅依赖本拦截器。
 * [com.answufeng.net.http.util.RetryStrategy] 必须保证在合理次数后 [RetryStrategy.shouldRetry] 为 false，否则将触发本类内置的绝对次数上限以终止死循环。
 *
 * @param minJitteredBackoffLowerBoundMs 对 [Thread.sleep] 前退避时长的**下限**（经抖动后再取 [kotlin.math.max]），默认 50ms，减轻对服务端的热连打；可传入 `0L` 供测试或受控内网使用。
 */
class DynamicRetryInterceptor @JvmOverloads constructor(
    private val fallbackStrategy: RetryStrategy = DefaultRetryStrategy(),
    private val minJitteredBackoffLowerBoundMs: Long = 50L
) : Interceptor {

    companion object {
        private const val DEFAULT_INITIAL_BACKOFF_MS = 300L
        private const val DEFAULT_MAX_BACKOFF_MS = 5_000L
        private const val JITTER_BASE = 0.9
        private const val JITTER_RANGE = 0.2
        /** 防止自定义 [RetryStrategy] 错误地永远返回重试，导致 [while] 与无限睡眠（测试可断言与之一致） */
        internal const val ABSOLUTE_MAX_RETRY_ROUNDS = 256
    }

    init {
        require(minJitteredBackoffLowerBoundMs >= 0L) {
            "minJitteredBackoffLowerBoundMs must be >= 0, actual: $minJitteredBackoffLowerBoundMs"
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.tag(SkipRetry::class.java) != null) {
            return chain.proceed(request)
        }
        val strategy = resolveStrategy(request)
            ?: return chain.proceed(request)

        var attempt = 0
        var lastError: IOException? = null
        while (true) {
            if (attempt >= ABSOLUTE_MAX_RETRY_ROUNDS) {
                throw lastError ?: IOException(
                    "DynamicRetryInterceptor: exceeded safety cap ($ABSOLUTE_MAX_RETRY_ROUNDS). " +
                        "Check RetryStrategy.shouldRetry() — it must return false for some attempt value."
                )
            }
            try {
                val response = chain.proceed(request)
                if (!strategy.shouldRetry(request, response, null, attempt)) {
                    return response
                }
                response.close()
            } catch (ioe: IOException) {
                lastError = ioe
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
        val jittered = (baseDelay * jitterFactor).toLong()
        return if (minJitteredBackoffLowerBoundMs == 0L) jittered else max(minJitteredBackoffLowerBoundMs, jittered)
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

    class SkipRetry
}
