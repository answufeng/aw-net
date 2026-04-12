package com.answufeng.net.http.util

import java.util.concurrent.ConcurrentHashMap

/**
 * 请求节流器（Request Throttle）。
 *
 * 在指定间隔内多次调用同一 key 的请求时，直接返回上次缓存的结果。
 * 典型场景：下拉刷新频繁触发、按钮防重复点击导致的请求。
 *
 * ### 用法
 * ```kotlin
 * val throttle = RequestThrottle(intervalMs = 3000)
 *
 * // 3秒内重复调用直接返回缓存结果
 * val result = throttle.throttleRequest("refresh_list") {
 *     api.getList()
 * }
 * ```
 *
 * @param intervalMs 最小请求间隔（毫秒），在此间隔内重复请求将返回缓存结果
 * @see RequestDedup
 * @since 1.0.0
 */
class RequestThrottle(
    private val intervalMs: Long = 3_000L
) {

    init {
        require(intervalMs > 0) { "intervalMs must be positive" }
    }

    private data class CachedResult(
        val value: Any?,
        val timestampMs: Long
    )

    private val cache = ConcurrentHashMap<String, CachedResult>()

    /**
     * 发起节流请求。
     *
     * 如果同一 [key] 在 [intervalMs] 内已有成功结果，直接返回缓存。
     * 否则执行 [block] 并缓存结果。
     *
     * @param key 请求唯一标识
     * @param block 实际执行请求的挂起函数
     * @return 请求结果（可能是缓存的）
     * @since 1.0.0
 */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> throttleRequest(key: String, block: suspend () -> T): T {
        val now = System.currentTimeMillis()
        val cached = cache[key]
        if (cached != null && (now - cached.timestampMs) < intervalMs) {
            return cached.value as T
        }

        val result = block()
        cache.put(key, CachedResult(result, System.currentTimeMillis()))
        return result
    }

    /**
     * 清除指定 key 的缓存，下次请求将重新执行。
     * @since 1.0.0
 */
    fun invalidate(key: String) {
        cache.remove(key)
    }

    /**
     * 清除所有缓存。
     * @since 1.0.0
 */
    fun invalidateAll() {
        cache.clear()
    }
}
