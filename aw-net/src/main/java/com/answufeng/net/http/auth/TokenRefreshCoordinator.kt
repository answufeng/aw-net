package com.answufeng.net.http.auth

import com.answufeng.net.http.annotations.NetLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Token 刷新协调器，统一管理 OkHttp 层和协程层的 Token 刷新逻辑。
 *
 * 解决的问题：
 * - OkHttp 的 [TokenAuthenticator] 在 HTTP 401 时触发刷新（阻塞式）
 * - [com.answufeng.net.http.util.RequestExecutor] 在业务 code=401 时触发刷新（协程式）
 * - 两条路径可能并发执行，导致 Token 被重复刷新
 *
 * 协调策略：
 * - 使用可重入锁 [ReentrantLock] 串行化刷新，并在持锁上增加 [lockAcquireTimeoutMs] 的 [tryLock] 限制，
 *   避免 [TokenProvider.refreshTokenBlocking] 卡死时永久占用线程。
 *   **说明**：`Semaphore(1)` 在「刷新请求再次触发 401/嵌套鉴权」时同一线程无法重入，可能死锁；
 *   可重入锁在 OkHttp/嵌套场景下与原先行为一致，仅增加「等待锁」的超时。
 * - 快速路径：如果当前 token 已被其他线程/协程刷新，直接复用新 token
 * - 刷新失败时不通知 [UnauthorizedHandler]，由调用方决定通知策略
 *
 * @param tokenProvider Token 提供者
 * @param headerName Authorization header 名称，默认 "Authorization"
 * @param tokenPrefix Token 前缀，默认 "Bearer "
 * @param lockAcquireTimeoutMs 等待进入刷新临界区的最长时间，超时则放弃本次刷新并返回 null
 * @param logger 日志记录器
 */
class TokenRefreshCoordinator(
    private val tokenProvider: TokenProvider,
    private val headerName: String = "Authorization",
    private val tokenPrefix: String = "Bearer ",
    private val lockAcquireTimeoutMs: Long = DEFAULT_LOCK_ACQUIRE_TIMEOUT_MS,
    private val logger: NetLogger? = null
) {

    private val lock = ReentrantLock()

    @Volatile
    private var lastRefreshTimestamp = 0L

    /**
     * 在阻塞上下文中刷新 Token（供 OkHttp Authenticator 调用）。
     *
     * 快速路径：如果当前 token 与请求中的 token 不同，说明已被其他线程刷新，直接返回。
     * 否则在限时等待后进入持锁区执行刷新，锁内再次检查 token 是否已变（double-check）。
     *
     * @param requestToken 请求中携带的旧 token（不含前缀）
     * @return 新的 Authorization header 值（含前缀），刷新失败、超时或锁不可用时返回 null
     */
    fun refreshIfNeededBlocking(requestToken: String?): String? {
        val current = tokenProvider.getAccessToken()
        if (current != null && current != requestToken) {
            return "$tokenPrefix$current"
        }

        if (!acquireLockWithTimeout("blocking")) {
            return null
        }
        return try {
            val afterLock = tokenProvider.getAccessToken()
            if (afterLock != null && afterLock != requestToken) {
                return "$tokenPrefix$afterLock"
            }

            val refreshed = try {
                tokenProvider.refreshTokenBlocking()
            } catch (t: Throwable) {
                logger?.e("TokenRefreshCoordinator", "Token refresh failed (blocking)", t)
                false
            }

            if (!refreshed) {
                return null
            }

            val newToken = tokenProvider.getAccessToken() ?: return null
            lastRefreshTimestamp = System.currentTimeMillis()
            "$tokenPrefix$newToken"
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    /**
     * 在协程上下文中刷新 Token（供 RequestExecutor 调用）。
     *
     * 与 [refreshIfNeededBlocking] 共享同一把锁；协程在 [Dispatchers.IO] 上持锁/刷新，带同样的锁等待超时。
     *
     * 使用 [TokenProvider.refreshTokenSuspend] 而非 [TokenProvider.refreshTokenBlocking]，
     * 以便用户可通过覆写提供真正的异步刷新实现，避免阻塞 IO 线程。
     * 注意：持鎖期間不呼叫 [TokenProvider.refreshTokenSuspend]；僅在鎖內做 double-check，刷新在釋放鎖後執行，
     * 避免掛起後恢復到不同執行緒導致 [ReentrantLock] 無法釋放。
     *
     * @param requestToken 请求中携带的旧 token（不含前缀）
     * @return 新的 Authorization header 值（含前缀），失败或超时时返回 null
     */
    suspend fun refreshIfNeededSuspend(requestToken: String?): String? {
        val current = tokenProvider.getAccessToken()
        if (current != null && current != requestToken) {
            return "$tokenPrefix$current"
        }

        return withContext(Dispatchers.IO) {
            if (!acquireLockWithTimeout("coroutine")) {
                return@withContext null
            }
            try {
                val afterLock = tokenProvider.getAccessToken()
                if (afterLock != null && afterLock != requestToken) {
                    return@withContext "$tokenPrefix$afterLock"
                }
            } finally {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                }
            }

            val refreshed = try {
                tokenProvider.refreshTokenSuspend()
            } catch (t: Throwable) {
                logger?.e("TokenRefreshCoordinator", "Token refresh failed (coroutine)", t)
                false
            }

            if (!refreshed) {
                return@withContext null
            }

            val newToken = tokenProvider.getAccessToken() ?: return@withContext null
            lastRefreshTimestamp = System.currentTimeMillis()
            "$tokenPrefix$newToken"
        }
    }

    private fun acquireLockWithTimeout(pathLabel: String): Boolean {
        val acquired = try {
            if (lockAcquireTimeoutMs <= 0L) {
                lock.tryLock()
            } else {
                lock.tryLock(lockAcquireTimeoutMs, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!acquired) {
            val detail = if (lockAcquireTimeoutMs <= 0L) {
                "lock was not available (no wait, lockAcquireTimeoutMs=0) ($pathLabel)"
            } else {
                "timed out after ${lockAcquireTimeoutMs}ms waiting for token refresh lock ($pathLabel)"
            }
            logger?.e("TokenRefreshCoordinator", detail, null)
        }
        return acquired
    }

    fun getAccessToken(): String? = tokenProvider.getAccessToken()

    companion object {
        const val DEFAULT_LOCK_ACQUIRE_TIMEOUT_MS: Long = 60_000L
    }
}
