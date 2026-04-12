package com.answufeng.net.http.auth

import java.util.concurrent.atomic.AtomicReference

/**
 * 简单的内存 TokenProvider 实现，供 demo 与测试使用。
 *
 * @param initialAccessToken 初始 access token，可为 null
 * @param refresher 刷新 token 的回调函数，返回 true 表示刷新成功；默认始终返回 false
 * @since 1.0.0
 */
class InMemoryTokenProvider(
    initialAccessToken: String? = null,
    private val refresher: () -> Boolean = { false }
) : TokenProvider {

    private val tokenRef = AtomicReference<String?>(initialAccessToken)

    override fun getAccessToken(): String? = tokenRef.get()

    override fun refreshTokenBlocking(): Boolean {
        // 调用传入的 refresher，成功时不直接设置 token（应用层负责持久化并让 getAccessToken 返回新值），
        // 也可以在 refresher 内部直接修改 tokenRef（如果需要）。
        return try {
            refresher()
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun refreshTokenSuspend(): Boolean = refreshTokenBlocking()

    override fun clear() {
        tokenRef.set(null)
    }

    /**
     * 手动设置 access token。
     *
     * 适用于登录成功后设置 token、TokenAuthenticator 刷新成功后更新 token 等场景。
     * @param token 新的 access token，传 null 等同于调用 [clear]
     * @since 1.0.0
$     */
    fun setAccessToken(token: String?) {
        tokenRef.set(token)
    }
}
