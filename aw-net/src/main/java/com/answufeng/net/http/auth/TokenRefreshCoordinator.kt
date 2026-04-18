package com.answufeng.net.http.auth

import com.answufeng.net.http.annotations.NetLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Token 刷新协调器，统一管理 OkHttp 层和协程层的 Token 刷新逻辑。
 *
 * 解决的问题：
 * - OkHttp 的 [TokenAuthenticator] 在 HTTP 401 时触发刷新（阻塞式）
 * - [com.answufeng.net.http.util.RequestExecutor] 在业务 code=401 时触发刷新（协程式）
 * - 两条路径可能并发执行，导致 Token 被重复刷新
 *
 * 协调策略：
 * - 统一使用 [ReentrantLock] 串行化所有刷新路径（阻塞式和协程式共享同一把锁）
 * - 快速路径：如果当前 token 已被其他线程/协程刷新，直接复用新 token
 * - 刷新失败时不通知 [UnauthorizedHandler]，由调用方决定通知策略
 *
 * @param tokenProvider Token 提供者
 * @param headerName Authorization header 名称，默认 "Authorization"
 * @param tokenPrefix Token 前缀，默认 "Bearer "
 * @param logger 日志记录器
 * @since 2.0.0
 */
class TokenRefreshCoordinator(
    private val tokenProvider: TokenProvider,
    private val headerName: String = "Authorization",
    private val tokenPrefix: String = "Bearer ",
    private val logger: NetLogger? = null
) {

    private val lock = ReentrantLock()

    @Volatile
    private var lastRefreshTimestamp = 0L

    /**
     * 在阻塞上下文中刷新 Token（供 OkHttp Authenticator 调用）。
     *
     * 快速路径：如果当前 token 与请求中的 token 不同，说明已被其他线程刷新，直接返回。
     * 否则加锁执行刷新，锁内再次检查 token 是否已变（double-check）。
     *
     * @param requestToken 请求中携带的旧 token（不含前缀）
     * @return 新的 Authorization header 值（含前缀），刷新失败返回 null
     */
    fun refreshIfNeededBlocking(requestToken: String?): String? {
        val current = tokenProvider.getAccessToken()
        if (current != null && current != requestToken) {
            return "$tokenPrefix$current"
        }

        return lock.withLock {
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
        }
    }

    /**
     * 在协程上下文中刷新 Token（供 RequestExecutor 调用）。
     *
     * 与 [refreshIfNeededBlocking] 共享同一把 [ReentrantLock]，
     * 确保 OkHttp 线程和协程不会同时执行 Token 刷新。
     * 协程层通过 [Dispatchers.IO] 调用 lock.withLock，避免阻塞调用者协程所在的线程。
     *
     * @param requestToken 请求中携带的旧 token（不含前缀）
     * @return 新的 Authorization header 值（含前缀），刷新失败返回 null
     */
    suspend fun refreshIfNeededSuspend(requestToken: String?): String? {
        val current = tokenProvider.getAccessToken()
        if (current != null && current != requestToken) {
            return "$tokenPrefix$current"
        }

        return withContext(Dispatchers.IO) {
            lock.withLock {
                val afterLock = tokenProvider.getAccessToken()
                if (afterLock != null && afterLock != requestToken) {
                    return@withContext "$tokenPrefix$afterLock"
                }

                val refreshed = try {
                    tokenProvider.refreshTokenBlocking()
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
    }

    fun getAccessToken(): String? = tokenProvider.getAccessToken()
}
