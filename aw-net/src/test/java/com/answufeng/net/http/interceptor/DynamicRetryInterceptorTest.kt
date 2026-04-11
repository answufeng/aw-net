package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.Retry
import com.answufeng.net.http.util.DefaultRetryStrategy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Invocation
import java.lang.reflect.Method

/**
 * DynamicRetryInterceptor 使用 MockWebServer 的集成测试。
 * 验证 @Retry 注解的 per-API 重试配置。
 */
class DynamicRetryInterceptorTest {

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

    private fun buildClient(fallbackMaxRetries: Int = 2): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                DynamicRetryInterceptor(
                    fallbackStrategy = DefaultRetryStrategy(
                        maxRetries = fallbackMaxRetries,
                        initialBackoffMillis = 10 // 加速测试
                    )
                )
            )
            .build()
    }

    /**
     * 构造带 @Retry 注解的 Request（通过模拟 Retrofit Invocation tag）
     */
    private fun requestWithRetryAnnotation(
        url: okhttp3.HttpUrl,
        method: String = "GET",
        retryAnnotation: Retry
    ): Request {
        val annotatedMethod = findAnnotatedMethod(retryAnnotation)
        val invocation = Invocation.of(annotatedMethod, emptyList<Any>())
        val builder = Request.Builder().url(url).tag(Invocation::class.java, invocation)
        if (method == "POST") {
            builder.post(okhttp3.RequestBody.create(null, ""))
        }
        return builder.build()
    }

    /**
     * 通过反射获取带有指定 @Retry 参数的方法。
     * 我们创建动态代理接口来实现这一点。
     */
    private fun findAnnotatedMethod(retry: Retry): Method {
        // 使用预定义的接口方法，根据参数选择
        return when {
            retry.maxAttempts == 0 -> AnnotatedApis::class.java.getMethod("noRetry")
            retry.maxAttempts == 5 && !retry.retryOnPost -> AnnotatedApis::class.java.getMethod("retry5Times")
            retry.maxAttempts == 3 && retry.retryOnPost -> AnnotatedApis::class.java.getMethod("retry3TimesWithPost")
            retry.maxAttempts == 1 -> AnnotatedApis::class.java.getMethod("retry1Time")
            else -> AnnotatedApis::class.java.getMethod("defaultRetry")
        }
    }

    /** 用于测试的标注接口 */
    @Suppress("unused")
    private interface AnnotatedApis {
        @Retry(maxAttempts = 0)
        fun noRetry()

        @Retry(maxAttempts = 5, initialBackoffMs = 10)
        fun retry5Times()

        @Retry(maxAttempts = 3, retryOnPost = true, initialBackoffMs = 10)
        fun retry3TimesWithPost()

        @Retry(maxAttempts = 1, initialBackoffMs = 10)
        fun retry1Time()

        @Retry // 使用默认值 (-1, -1, -1, false) → 走全局策略
        fun defaultRetry()
    }

    // ==================== 基础行为测试 ====================

    @Test
    fun `no annotation - uses fallback strategy and retries on 500`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = buildClient(fallbackMaxRetries = 2)
        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(3, server.requestCount) // 1 initial + 2 retries
    }

    @Test
    fun `no annotation - does not retry POST by default`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val client = buildClient(fallbackMaxRetries = 2)
        val request = Request.Builder()
            .url(server.url("/"))
            .post(okhttp3.RequestBody.create(null, "body"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(500, response.code)
        assertEquals(1, server.requestCount) // POST 不重试
    }

    // ==================== @Retry(maxAttempts=0) 测试 ====================

    @Test
    fun `@Retry maxAttempts=0 - disables retry even for 500 GET`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val client = buildClient(fallbackMaxRetries = 3)
        val request = requestWithRetryAnnotation(
            url = server.url("/"),
            retryAnnotation = AnnotatedApis::class.java.getMethod("noRetry").getAnnotation(Retry::class.java)!!
        )
        val response = client.newCall(request).execute()

        assertEquals(500, response.code)
        assertEquals(1, server.requestCount) // 即使全局开启，也不重试
    }

    // ==================== @Retry(maxAttempts=N) 测试 ====================

    @Test
    fun `@Retry maxAttempts=5 - retries up to 5 times for GET`() {
        // 前 5 次 500，第 6 次 200
        repeat(5) { server.enqueue(MockResponse().setResponseCode(500)) }
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = buildClient(fallbackMaxRetries = 1) // 全局仅 1 次
        val request = requestWithRetryAnnotation(
            url = server.url("/"),
            retryAnnotation = AnnotatedApis::class.java.getMethod("retry5Times").getAnnotation(Retry::class.java)!!
        )
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(6, server.requestCount) // 1 initial + 5 retries (注解覆盖了全局的 1 次)
    }

    @Test
    fun `@Retry maxAttempts=1 - only retries once`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val client = buildClient(fallbackMaxRetries = 5) // 全局 5 次但注解覆盖为 1 次
        val request = requestWithRetryAnnotation(
            url = server.url("/"),
            retryAnnotation = AnnotatedApis::class.java.getMethod("retry1Time").getAnnotation(Retry::class.java)!!
        )
        val response = client.newCall(request).execute()

        assertEquals(500, response.code)
        assertEquals(2, server.requestCount) // 1 initial + 1 retry
    }

    // ==================== @Retry(retryOnPost=true) 测试 ====================

    @Test
    fun `@Retry retryOnPost=true - retries POST requests`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = buildClient(fallbackMaxRetries = 0)
        val request = requestWithRetryAnnotation(
            url = server.url("/"),
            method = "POST",
            retryAnnotation = AnnotatedApis::class.java.getMethod("retry3TimesWithPost").getAnnotation(Retry::class.java)!!
        )
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(3, server.requestCount) // POST 也重试了
    }

    // ==================== @Retry(maxAttempts=-1) 回退到全局 ====================

    @Test
    fun `@Retry default values - falls back to global strategy`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = buildClient(fallbackMaxRetries = 2) // 全局 2 次
        val request = requestWithRetryAnnotation(
            url = server.url("/"),
            retryAnnotation = AnnotatedApis::class.java.getMethod("defaultRetry").getAnnotation(Retry::class.java)!!
        )
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(3, server.requestCount) // 1 initial + 2 retries (使用全局策略)
    }

    // ==================== 429 Too Many Requests ====================

    @Test
    fun `retries on 429 Too Many Requests`() {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = buildClient(fallbackMaxRetries = 2)
        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    // ==================== 200 不重试 ====================

    @Test
    fun `does not retry on success`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = buildClient(fallbackMaxRetries = 3)
        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
    }

    // ==================== 4xx 不重试（除 429） ====================

    @Test
    fun `does not retry on 400 Bad Request`() {
        server.enqueue(MockResponse().setResponseCode(400))

        val client = buildClient(fallbackMaxRetries = 3)
        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertEquals(400, response.code)
        assertEquals(1, server.requestCount) // 只请求一次
    }

    @Test
    fun `does not retry on 404 Not Found`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val client = buildClient(fallbackMaxRetries = 3)
        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertEquals(404, response.code)
        assertEquals(1, server.requestCount)
    }
}
