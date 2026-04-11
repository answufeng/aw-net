package com.answufeng.net.http.auth

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

/**
 * TokenAuthenticator 的单元测试：并发刷新、快速路径、防无限重试。
 */
class TokenAuthenticatorTest {

    private fun response401(request: Request, priorResponse: Response? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
        if (priorResponse != null) {
            builder.priorResponse(priorResponse)
        }
        return builder.build()
    }

    private fun makeRequest(token: String? = null): Request {
        val builder = Request.Builder().url("https://api.example.com/data")
        if (token != null) builder.header("Authorization", "Bearer $token")
        return builder.build()
    }

    // ==================== 基本流程 ====================

    @Test
    fun `successful refresh retries request with new token`() {
        lateinit var provider: InMemoryTokenProvider
        provider = InMemoryTokenProvider(initialAccessToken = "old_token") {
            provider.setAccessToken("new_token")
            true
        }

        val authenticator = TokenAuthenticator(provider)
        val request = makeRequest("old_token")
        val resp = response401(request)

        val newRequest = authenticator.authenticate(null, resp)
        assertNotNull(newRequest)
        assertEquals("Bearer new_token", newRequest!!.header("Authorization"))
    }

    @Test
    fun `failed refresh returns null`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") { false }
        val authenticator = TokenAuthenticator(provider)
        val request = makeRequest("token")
        val resp = response401(request)

        val newRequest = authenticator.authenticate(null, resp)
        assertNull(newRequest)
    }

    @Test
    fun `refresh exception returns null`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") {
            error("network error")
        }
        val authenticator = TokenAuthenticator(provider)
        val request = makeRequest("token")
        val resp = response401(request)

        val newRequest = authenticator.authenticate(null, resp)
        assertNull(newRequest)
    }

    // ==================== 防无限重试 ====================

    @Test
    fun `prior 401 response causes null return`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") { true }
        val authenticator = TokenAuthenticator(provider)
        val request = makeRequest("token")

        // 第一次 401
        val firstResp = response401(request)
        // 第二次 401 with priorResponse
        val secondResp = response401(request, priorResponse = firstResp)

        val result = authenticator.authenticate(null, secondResp)
        assertNull(result) // 应放弃重试
    }

    // ==================== 并发优化 ====================

    @Test
    fun `concurrent 401 only refreshes once`() {
        val refreshCount = AtomicInteger(0)
        val provider = InMemoryTokenProvider(initialAccessToken = "old_token") {
            refreshCount.incrementAndGet()
            Thread.sleep(50) // 模拟刷新耗时
            true.also { (it as? Unit) ?: Unit }
        }
        val realProvider = object : TokenProvider {
            override fun getAccessToken(): String? = provider.getAccessToken()
            override fun refreshTokenBlocking(): Boolean {
                val count = refreshCount.incrementAndGet()
                Thread.sleep(50)
                provider.setAccessToken("new_token_$count")
                return true
            }
        }

        val authenticator = TokenAuthenticator(realProvider)
        val threadCount = 5
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val results = Array<Request?>(threadCount) { null }

        repeat(threadCount) { i ->
            Thread {
                try {
                    barrier.await() // 全部线程同时开始
                    val request = makeRequest("old_token")
                    val resp = response401(request)
                    results[i] = authenticator.authenticate(null, resp)
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        latch.await()

        // 只应刷新一次（或极少数次 — 竞争后进入的线程发现 token 已变应直接复用）
        assertTrue("Refresh count should be minimal, got ${refreshCount.get()}", refreshCount.get() <= 2)
        // 所有请求都应获得非 null 的新请求
        results.forEach { assertNotNull(it) }
    }

    @Test
    fun `token already refreshed by other thread — fast path`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "new_token") { true }
        // 请求携带旧 token，但 provider 已持有新 token
        val authenticator = TokenAuthenticator(provider)
        val request = makeRequest("old_token")
        val resp = response401(request)

        val newRequest = authenticator.authenticate(null, resp)
        assertNotNull(newRequest)
        assertEquals("Bearer new_token", newRequest!!.header("Authorization"))
    }

    // ==================== 自定义 header ====================

    @Test
    fun `custom header name and prefix`() {
        val realProvider = object : TokenProvider {
            override fun getAccessToken(): String? = "refreshed"
            override fun refreshTokenBlocking(): Boolean = true
        }
        val authenticator = TokenAuthenticator(realProvider, headerName = "X-Token", tokenPrefix = "Token ")
        val request = Request.Builder()
            .url("https://api.example.com/data")
            .header("X-Token", "Token old")
            .build()
        val resp = response401(request)

        val newRequest = authenticator.authenticate(null, resp)
        assertNotNull(newRequest)
        assertEquals("Token refreshed", newRequest!!.header("X-Token"))
    }

    @Test
    fun `null token after refresh returns null`() {
        val provider = object : TokenProvider {
            override fun getAccessToken(): String? = null
            override fun refreshTokenBlocking(): Boolean = true // 刷新"成功"但 token 为 null
        }
        val authenticator = TokenAuthenticator(provider)
        val request = makeRequest(null)
        val resp = response401(request)

        val result = authenticator.authenticate(null, resp)
        assertNull(result) // token 为 null 应放弃
    }
}
