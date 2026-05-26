package com.answufeng.net.http.interceptor

import com.answufeng.net.http.auth.TokenProvider
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 为每个请求自动附加 Authorization（或其它）Header。
 *
 * 需在 Hilt 中提供 [TokenProvider] 后由 [com.answufeng.net.http.di.NetworkModule] 自动注册。
 */
class AuthHeaderInterceptor(
    private val tokenProvider: TokenProvider,
    private val headerName: String = "Authorization",
    private val tokenPrefix: String = "Bearer ",
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.getAccessToken()
        val request =
            if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .header(headerName, tokenPrefix + token.trim())
                    .build()
            } else {
                chain.request()
            }
        return chain.proceed(request)
    }
}
