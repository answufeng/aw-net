package com.answufeng.net.http.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * DynamicBaseUrlInterceptor 的集成测试：路径拼接和查询参数保留。
 *
 * 注意：@BaseUrl 注解通过 Retrofit Invocation tag 读取，
 * 纯 OkHttp 层无法直接测试。这里测试拦截器在没有注解时的穿透行为。
 */
class DynamicBaseUrlInterceptorTest {

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

    @Test
    fun `request without BaseUrl annotation passes through`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor())
            .build()

        server.enqueue(MockResponse().setBody("ok"))
        val response = client.newCall(
            Request.Builder().url(server.url("/api/user")).build()
        ).execute()

        assertEquals(200, response.code)
        val recorded = server.takeRequest()
        assertEquals("/api/user", recorded.path)
    }

    @Test
    fun `preserves query parameters`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor())
            .build()

        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(
            Request.Builder().url(server.url("/api/search?q=test&page=1")).build()
        ).execute()

        val recorded = server.takeRequest()
        assertEquals("/api/search?q=test&page=1", recorded.path)
    }
}
