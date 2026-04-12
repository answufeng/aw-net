package com.answufeng.net.http.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 抽象的 Token 提供者，用于对接应用层的 token 存储与刷新逻辑。
 *
 * 说明：
 * - OkHttp 的 Authenticator 是同步的（blocking），因此需要提供一个阻塞刷新方法 `refreshTokenBlocking()`。
 * - 为了方便在协程环境下使用，也提供了一个可选的 suspend 方法 `refreshTokenSuspend()`，默认实现会调用阻塞方法。
 * @since 1.0.0
 */
interface TokenProvider {
    /** 当前可用的 access token（未包含前缀，如 "Bearer "） 
    * @since 1.0.0
 */
    fun getAccessToken(): String?

    /**
     * 在阻塞上下文中刷新 token（供 OkHttp Authenticator 调用）。
     * 返回 true 表示刷新成功且后续调用 `getAccessToken()` 可获得新 token。
     * @since 1.0.0
 */
    fun refreshTokenBlocking(): Boolean

    /**
     * 协程版本的刷新（供应用层在挂起函数中调用）。
     * 默认实现通过 `Dispatchers.IO` 调用 blocking 版本，避免阻塞调用者协程。
     * @since 1.0.0
 */
    suspend fun refreshTokenSuspend(): Boolean =
        withContext(Dispatchers.IO) { refreshTokenBlocking() }

    /**
     * 清理存储的 token（在登出场景或刷新失败后调用）。默认实现为空，项目层可覆盖以清除持久化 token。
     * @since 1.0.0
 */
    fun clear() {}
}
