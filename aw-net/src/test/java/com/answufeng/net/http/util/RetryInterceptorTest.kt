package com.answufeng.net.http.util

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * RetryInterceptor 使用 MockWebServer 的集成测试。
 */
class RetryInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun buildClient(strategy: RetryStrategy): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(strategy))
            .build()
    }

    @Test
    fun `does not retry on 200`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = buildClient(DefaultRetryStrategy(maxRetries = 3))
        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retries on 500 for GET`() {
        // 第一次 500，第二次 200
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val strategy = DefaultRetryStrategy(
            maxRetries = 3,
            initialBackoffMillis = 10, // 最小延迟以加速测试
            maxBackoffMillis = 20
        )
        val client = buildClient(strategy)
        val request = Request.Builder().url(server.url("/data")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `does not retry POST on 500`() {
        // POST 不是幂等方法，不应重试
        server.enqueue(MockResponse().setResponseCode(500))

        val strategy = DefaultRetryStrategy(maxRetries = 3)
        val client = buildClient(strategy)
        val request = Request.Builder()
            .url(server.url("/submit"))
            .post(okhttp3.RequestBody.create(null, "body"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(500, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retries up to maxRetries then returns last 500`() {
        // 4 次 500（第 0 次 + 3 次重试）
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }

        val strategy = DefaultRetryStrategy(
            maxRetries = 3,
            initialBackoffMillis = 10,
            maxBackoffMillis = 20
        )
        val client = buildClient(strategy)
        val request = Request.Builder().url(server.url("/flaky")).build()
        val response = client.newCall(request).execute()

        assertEquals(500, response.code)
        assertEquals(4, server.requestCount) // 1 初始 + 3 重试
    }

    @Test
    fun `retries on 429 Too Many Requests`() {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val strategy = DefaultRetryStrategy(
            maxRetries = 2,
            initialBackoffMillis = 10,
            maxBackoffMillis = 20
        )
        val client = buildClient(strategy)
        val request = Request.Builder().url(server.url("/rate-limited")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `uses custom strategy`() {
        // 自定义策略：总是重试 1 次
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        val strategy = object : RetryStrategy {
            override fun shouldRetry(request: Request, response: okhttp3.Response?, error: IOException?, attempt: Int): Boolean {
                return attempt < 1 // 只重试一次
            }
            override fun nextDelayMillis(attempt: Int) = 10L
        }

        val client = buildClient(strategy)
        val request = Request.Builder().url(server.url("/custom")).build()
        val response = client.newCall(request).execute()

        assertEquals(404, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `retries on IOException for GET`() {
        // 断开连接引发 IOException
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200).setBody("recovered"))

        val strategy = DefaultRetryStrategy(
            maxRetries = 2,
            initialBackoffMillis = 10,
            maxBackoffMillis = 20
        )
        val client = buildClient(strategy)
        val request = Request.Builder().url(server.url("/unstable")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertTrue(server.requestCount >= 2)
    }
}
