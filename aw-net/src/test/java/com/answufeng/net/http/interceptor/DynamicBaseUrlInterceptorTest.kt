package com.answufeng.net.http.interceptor

import com.answufeng.net.http.config.NetworkConfig
import com.answufeng.net.http.config.NetworkConfigProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DynamicBaseUrlInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var server2: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server2 = MockWebServer()
        server2.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        server2.shutdown()
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

    @Test
    fun `runtime baseUrl change redirects request to new host`() {
        val originalBaseUrl = server.url("/").toString()
        val newBaseUrl = server2.url("/").toString()
        val configProvider = NetworkConfigProvider(NetworkConfig(baseUrl = originalBaseUrl))

        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(configProvider))
            .build()

        server2.enqueue(MockResponse().setBody("from-server2"))

        configProvider.updateConfig(configProvider.current.copy(baseUrl = newBaseUrl))

        val response = client.newCall(
            Request.Builder().url(server.url("/api/data")).build()
        ).execute()

        assertEquals(200, response.code)
        assertEquals("from-server2", response.body?.string())

        val recorded = server2.takeRequest()
        assertEquals("/api/data", recorded.path)
    }

    @Test
    fun `no redirect when baseUrl unchanged`() {
        val baseUrl = server.url("/").toString()
        val configProvider = NetworkConfigProvider(NetworkConfig(baseUrl = baseUrl))

        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(configProvider))
            .build()

        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/api/data")).build()).execute()

        val recorded = server.takeRequest()
        assertEquals("/api/data", recorded.path)
    }

    @Test
    fun `null configProvider does not redirect`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(null))
            .build()

        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/api/data")).build()).execute()

        val recorded = server.takeRequest()
        assertEquals("/api/data", recorded.path)
    }
}
