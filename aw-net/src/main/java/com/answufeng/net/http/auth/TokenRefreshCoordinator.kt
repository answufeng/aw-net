package com.answufeng.net.http.auth

import com.answufeng.net.http.logging.NetLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
 * - 阻塞路径使用 [ReentrantLock] 串行化，支持 OkHttp 嵌套鉴权场景下的可重入
 * - 协程路径使用 [Mutex] 串行化，支持真正的挂起刷新（调用 [TokenProvider.refreshTokenSuspend]）
 * - 两条路径通过 [refreshing] 原子标记互斥：阻塞路径持锁期间，协程路径等待；反之亦然
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
    private val logger: NetLogger? = null,
) {
    private val blockingLock = ReentrantLock()
    private val suspendMutex = Mutex()
    private val refreshing = AtomicBoolean(false)
    private val refreshSignal = AtomicReference<CompletableDeferred<Unit>?>(null)

    @Volatile
    private var lastRefreshTimestamp = 0L

    /**
     * 在阻塞上下文中刷新 Token（供 OkHttp Authenticator 调用）。
     *
     * 快速路径：如果当前 token 与请求中的 token 不同，说明已被其他线程/协程刷新，直接返回。
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

            refreshing.set(true)
            val signal = CompletableDeferred<Unit>()
            refreshSignal.set(signal)
            val refreshed =
                try {
                    tokenProvider.refreshTokenBlocking()
                } catch (t: Throwable) {
                    logger?.e("TokenRefreshCoordinator", "Token refresh failed (blocking)", t)
                    false
                } finally {
                    refreshing.set(false)
                    refreshSignal.set(null)
                    signal.complete(Unit)
                }

            if (!refreshed) {
                return null
            }

            val newToken = tokenProvider.getAccessToken() ?: return null
            lastRefreshTimestamp = System.currentTimeMillis()
            "$tokenPrefix$newToken"
        } finally {
            if (blockingLock.isHeldByCurrentThread) {
                blockingLock.unlock()
            }
        }
    }

    /**
     * 在协程上下文中刷新 Token（供 RequestExecutor 调用）。
     *
     * 使用 [TokenProvider.refreshTokenSuspend] 进行真正的挂起刷新，避免阻塞 IO 线程。
     * 通过 [suspendMutex] 串行化协程路径的并发刷新；同时通过 [refreshing] 标记与阻塞路径互斥——
     * 若阻塞路径正在刷新，则等待其完成后再做 double-check。
     *
     * @param requestToken 请求中携带的旧 token（不含前缀）
     * @return 新的 Authorization header 值（含前缀），失败或超时时返回 null
     */
    suspend fun refreshIfNeededSuspend(requestToken: String?): String? {
        val current = tokenProvider.getAccessToken()
        if (current != null && current != requestToken) {
            return "$tokenPrefix$current"
        }

        val result =
            withTimeoutOrNull(lockAcquireTimeoutMs) {
                suspendMutex.withLock {
                    doRefreshSuspend(requestToken)
                }
            }

        if (result == null) {
            logger?.e(
                "TokenRefreshCoordinator",
                "timed out after ${lockAcquireTimeoutMs}ms waiting for token refresh lock (suspend)",
                null,
            )
        }

        return result
    }

    private suspend fun doRefreshSuspend(requestToken: String?): String? {
        val afterLock = tokenProvider.getAccessToken()
        if (afterLock != null && afterLock != requestToken) {
            return "$tokenPrefix$afterLock"
        }

        waitForBlockingRefresh()

        val afterBlocking = tokenProvider.getAccessToken()
        if (afterBlocking != null && afterBlocking != requestToken) {
            return "$tokenPrefix$afterBlocking"
        }

        val refreshed =
            try {
                tokenProvider.refreshTokenSuspend()
            } catch (t: Throwable) {
                logger?.e("TokenRefreshCoordinator", "Token refresh failed (suspend)", t)
                false
            }

        if (!refreshed) {
            return null
        }

        val newToken = tokenProvider.getAccessToken() ?: return null
        lastRefreshTimestamp = System.currentTimeMillis()
        return "$tokenPrefix$newToken"
    }

    private suspend fun waitForBlockingRefresh() {
        val signal: CompletableDeferred<Unit>? = refreshSignal.get()
        if (signal != null) {
            withTimeoutOrNull(lockAcquireTimeoutMs) { signal.await() }
        }
    }

    private fun acquireLockWithTimeout(pathLabel: String): Boolean {
        val acquired =
            try {
                if (lockAcquireTimeoutMs <= 0L) {
                    blockingLock.tryLock()
                } else {
                    blockingLock.tryLock(lockAcquireTimeoutMs, TimeUnit.MILLISECONDS)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        if (!acquired) {
            val detail =
                if (lockAcquireTimeoutMs <= 0L) {
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
