package com.answufeng.net.http.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * 请求去重器（Request Deduplicator）。
 *
 * 当同一 key 的请求正在执行时，后续相同 key 的请求会等待并共享结果，避免重复发送。
 * 典型场景：多个页面同时拉取同一用户信息、多个组件并发请求同一配置接口。
 *
 * ### 用法
 * ```kotlin
 * val dedup = RequestDedup()
 *
 * // 多个协程并发调用，只会发送一次实际请求
 * val result = dedup.dedupRequest("user_info_123") {
 *     api.getUserInfo(123)
 * }
 * ```
 *
 * ### 与节流的区别
 * - **去重**：相同请求同时进行时合并为一次，结果共享给所有等待者
 * - **节流**：限制某个请求的最低调用间隔，间隔内直接返回缓存结果
 *
 * @see RequestThrottle
 * @since 1.0.0
 */
class RequestDedup {

    private val inFlight = ConcurrentHashMap<String, Deferred<Any?>>()

    /**
     * 发起去重请求。
     *
     * 如果同一 [key] 的请求正在执行中，直接等待并复用其结果。
     * 否则启动新请求。
     *
     * @param key 请求唯一标识（建议用 URL + 关键参数拼接）
     * @param block 实际执行请求的挂起函数
     * @return 请求结果
     * @since 1.0.0
 */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> dedupRequest(key: String, timeoutMs: Long = 0, block: suspend () -> T): T {
        val existing = inFlight[key]
        if (existing != null) {
            val awaitBlock: suspend () -> T = { existing.await() as T }
            return if (timeoutMs > 0) withTimeout(timeoutMs) { awaitBlock() } else awaitBlock()
        }

        val deferred = CompletableDeferred<Any?>()
        deferred.invokeOnCompletion { inFlight.remove(key) }
        val prev = inFlight.putIfAbsent(key, deferred)
        if (prev != null) {
            val awaitBlock: suspend () -> T = { prev.await() as T }
            return if (timeoutMs > 0) withTimeout(timeoutMs) { awaitBlock() } else awaitBlock()
        }

        return try {
            val result = block()
            deferred.complete(result)
            result
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        }
    }

    /**
     * 取消指定 key 的进行中请求。
     * @since 1.0.0
 */
    fun cancel(key: String) {
        inFlight.remove(key)?.cancel()
    }

    /**
     * 取消所有进行中的请求。
     * @since 1.0.0
 */
    fun cancelAll() {
        inFlight.keys.toList().forEach { cancel(it) }
    }

    /**
     * 当前进行中的请求数量。
     * @since 1.0.0
 */
    val inFlightCount: Int get() = inFlight.size
}
