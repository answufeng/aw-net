package com.answufeng.net.http.util

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val keyMutexes = ConcurrentHashMap<String, Mutex>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> throttleRequest(key: String, block: suspend () -> T): T {
        val mutex = keyMutexes.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val cached = cache[key]
            if (cached != null && (now - cached.timestampMs) < intervalMs) {
                return@withLock cached.value as T
            }
            val result = block()
            cache[key] = CachedResult(result, System.currentTimeMillis())
            result
        }
    }

    fun invalidate(key: String) {
        cache.remove(key)
        keyMutexes.remove(key)
    }

    fun invalidateAll() {
        cache.clear()
        keyMutexes.clear()
    }
}
