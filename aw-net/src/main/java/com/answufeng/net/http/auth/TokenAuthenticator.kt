package com.answufeng.net.http.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp Token 认证器，当服务器返回 401 时自动刷新 Token 并重试请求。
 *
 * 通过 [TokenRefreshCoordinator] 协调多并发请求的 Token 刷新，
 * 确保同时只有一个刷新请求进行，其他请求等待刷新完成后使用新 Token。
 *
 * ### 用法
 * ```kotlin
 * val authenticator = TokenAuthenticator(
 *     coordinator = TokenRefreshCoordinator(tokenProvider),
 *     headerName = "Authorization",
 *     tokenPrefix = "Bearer ",
 *     unauthorizedHandler = object : UnauthorizedHandler {
 *         override fun onUnauthorized() { logout() }
 *     }
 * )
 * val client = OkHttpClient.Builder()
 *     .authenticator(authenticator)
 *     .build()
 * ```
 *
 * @param coordinator         Token 刷新协调器
 * @param headerName          Token 请求头名称，默认 "Authorization"
 * @param tokenPrefix         Token 前缀，默认 "Bearer "
 * @param unauthorizedHandler Token 刷新失败（未授权）回调
 */
class TokenAuthenticator(
    private val coordinator: TokenRefreshCoordinator,
    private val headerName: String = "Authorization",
    private val tokenPrefix: String = "Bearer ",
    private val unauthorizedHandler: UnauthorizedHandler? = null
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val prior = response.priorResponse
        if (prior != null && prior.code == 401) return null

        val requestToken = response.request.header(headerName)?.removePrefix(tokenPrefix)
        val newHeader = coordinator.refreshIfNeededBlocking(requestToken) ?: run {
            notifyUnauthorized()
            return null
        }

        return response.request.newBuilder()
            .header(headerName, newHeader)
            .build()
    }

    private fun notifyUnauthorized() {
        try {
            unauthorizedHandler?.onUnauthorized()
        } catch (_: Exception) {
        }
    }
}
