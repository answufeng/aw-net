package com.answufeng.net.http.auth

import com.answufeng.net.http.annotations.INetLogger
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 基于 OkHttp [Authenticator] 的自动 token 刷新实现。
 *
 * ## 触发时机
 * OkHttp 在收到 HTTP 401 响应后，会调用 [authenticate] 方法。该方法运行在 OkHttp
 * 的连接线程上，为同步阻塞调用。
 *
 * ## 并发安全策略
 * 当多个请求并发收到 401 时，使用 [ReentrantLock] 串行化进入刷新逻辑，保证 **同一时刻只有
 * 一个线程执行 token 刷新**。后续线程进入锁后会先检查 token 是否已被其他线程更新，若已更新
 * 则直接复用新 token，避免重复刷新。
 *
 * ## 处理流程
 * ```
 *  收到 401
 *    │
 *    ├→ 检查 priorResponse 是否已是 401 → 是 → 返回 null（放弃，防无限重试）
 *    │
 *    └→ 获取锁
 *         │
 *         ├→ 当前 token ≠ 请求中的 token? → 说明其他线程已刷新 → 用新 token 重试
 *         │
 *         └→ 执行 refreshTokenBlocking()
 *              ├→ 成功 → 用新 token 重试
 *              └→ 失败 → 返回 null（放弃重试，上层根据 UnauthorizedHandler 处理）
 * ```
 *
 * @param tokenProvider  token 的读取与刷新实现
 * @param headerName     携带 token 的请求头名称，默认 `Authorization`
 * @param tokenPrefix    token 值的前缀，默认 `Bearer `
 * @param unauthorizedHandler 可选的未授权回调，当刷新失败时触发（如跳转登录页）
 * @since 1.0.0
 */
class TokenAuthenticator(
    private val tokenProvider: TokenProvider,
    private val headerName: String = "Authorization",
    private val tokenPrefix: String = "Bearer ",
    private val unauthorizedHandler: UnauthorizedHandler? = null,
    private val logger: INetLogger? = null
) : Authenticator {

    private val lock = ReentrantLock()

    override fun authenticate(route: Route?, response: Response): Request? {
        val prior = response.priorResponse
        if (prior != null && prior.code == 401) return null

        lock.withLock {
            val current = tokenProvider.getAccessToken()
            val requestToken = response.request.header(headerName)?.removePrefix(tokenPrefix)
            if (current != null && current != requestToken) {
                return response.request.newBuilder()
                    .header(headerName, "$tokenPrefix$current")
                    .build()
            }

            val refreshed = try {
                tokenProvider.refreshTokenBlocking()
            } catch (t: Throwable) {
                logger?.e("TokenAuthenticator", "Token refresh failed", t)
                false
            }

            if (!refreshed) {
                try {
                    unauthorizedHandler?.onUnauthorized()
                } catch (_: Exception) {}
                return null
            }

            val newToken = tokenProvider.getAccessToken() ?: return null
            return response.request.newBuilder()
                .header(headerName, "$tokenPrefix$newToken")
                .build()
        }
    }
}
