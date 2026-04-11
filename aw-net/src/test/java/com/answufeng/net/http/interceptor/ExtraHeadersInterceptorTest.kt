package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkConfigProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ExtraHeadersInterceptor 的集成测试：验证请求头注入、缓存命中和配置变更场景。
 */
class ExtraHeadersInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var configProvider: NetworkConfigProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(ExtraHeadersInterceptor(configProvider))
            .build()
    }

    @Test
    fun `injects extra headers into request`() {
        configProvider = NetworkConfigProvider(
            NetworkConfig(
                baseUrl = "https://api.example.com/",
                extraHeaders = mapOf("X-Custom" to "value1", "X-App" to "Brick")
            )
        )
        server.enqueue(MockResponse().setBody("ok"))

        val client = createClient()
        client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        val recorded = server.takeRequest()
        assertEquals("value1", recorded.getHeader("X-Custom"))
        assertEquals("Brick", recorded.getHeader("X-App"))
    }

    @Test
    fun `no headers when extraHeaders is empty`() {
        configProvider = NetworkConfigProvider(
            NetworkConfig(baseUrl = "https://api.example.com/", extraHeaders = emptyMap())
        )
        server.enqueue(MockResponse().setBody("ok"))

        val client = createClient()
        client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("X-Custom"))
    }

    @Test
    fun `cache invalidated on config update`() {
        configProvider = NetworkConfigProvider(
            NetworkConfig(
                baseUrl = "https://api.example.com/",
                extraHeaders = mapOf("X-Version" to "1.0")
            )
        )
        val client = createClient()

        // 第一次请求
        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        assertEquals("1.0", server.takeRequest().getHeader("X-Version"))

        // 更新配置
        configProvider.update { it.copy(extraHeaders = mapOf("X-Version" to "2.0")) }

        // 第二次请求应使用新 Header
        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        assertEquals("2.0", server.takeRequest().getHeader("X-Version"))
    }

    @Test
    fun `cache hit on same config reference`() {
        configProvider = NetworkConfigProvider(
            NetworkConfig(
                baseUrl = "https://api.example.com/",
                extraHeaders = mapOf("X-Id" to "abc")
            )
        )
        val client = createClient()

        // 两次请求，config 引用不变，应命中缓存
        server.enqueue(MockResponse().setBody("ok"))
        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals("abc", server.takeRequest().getHeader("X-Id"))
        assertEquals("abc", server.takeRequest().getHeader("X-Id"))
    }
}
