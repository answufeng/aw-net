package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.Timeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Invocation
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * [DynamicTimeoutInterceptor]：验证 @Timeout 会缩短该次请求的 read 超时，慢响应会触发 [SocketTimeoutException]。
 */
class DynamicTimeoutInterceptorTest {

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

    private interface ReadSlowApi {
        @Timeout(read = 500, unit = TimeUnit.MILLISECONDS)
        fun readHalfSecond()
    }

    @Test
    fun `read timeout from annotation is shorter than default client read timeout`() {
        // 首字节延迟 2s，远大于注解读超时 500ms → 应触发 read timeout
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .setBodyDelay(2, TimeUnit.SECONDS)
        )

        val method = ReadSlowApi::class.java.getMethod("readHalfSecond")
        val invocation = Invocation.of(method, emptyList<Any>())
        val request = Request.Builder()
            .url(server.url("/"))
            .tag(Invocation::class.java, invocation)
            .build()

        val client = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(DynamicTimeoutInterceptor())
            .build()

        try {
            client.newCall(request).execute().close()
            fail("expected timeout")
        } catch (e: IOException) {
            val isReadTimeout = e is SocketTimeoutException ||
                (e.cause is SocketTimeoutException) ||
                e.message?.contains("timeout", ignoreCase = true) == true
            assertTrue("expected read timeout, got: $e", isReadTimeout)
        }
    }
}
