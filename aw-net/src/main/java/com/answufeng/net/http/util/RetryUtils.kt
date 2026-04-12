package com.answufeng.net.http.util

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * 挂起重试工具函数，支持指数退避 + 随机抖动。
 *
 * @param maxAttempts 最大尝试次数（含首次）
 * @param initialDelayMillis 初始退避延迟（毫秒）
 * @param maxDelayMillis 最大退避延迟（毫秒）
 * @param factor 退避倍数
 * @param shouldRetry 根据异常判断是否重试
 * @param block 需要重试的挂起代码块
 * @return 成功执行的结果
 * @throws Throwable 当达到最大重试次数或不应重试时抛出最后一次异常
 * @since 1.0.0
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 300,
    maxDelayMillis: Long = 5_000,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): T {
    var attempt = 0
    var lastError: Throwable? = null
    while (attempt < maxAttempts) {
        try {
            return block()
        } catch (t: Throwable) {
            lastError = t
            if (!shouldRetry(t) || attempt + 1 >= maxAttempts) break
            val backoff = computeDelay(initialDelayMillis, maxDelayMillis, factor, attempt)
            val jitter = Random.nextLong(0, (initialDelayMillis / 2).coerceAtLeast(1))
            delay(backoff + jitter)
        }
        attempt++
    }
    throw lastError ?: IllegalStateException("retryWithBackoff failed without exception")
}

private fun computeDelay(initial: Long, max: Long, factor: Double, attempt: Int): Long {
    val raw = initial * factor.pow(attempt.toDouble())
    return min(max, raw.toLong())
}
