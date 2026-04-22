package com.answufeng.net.http.interceptor

import com.answufeng.net.http.util.RetryStrategy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * 恶意 [RetryStrategy] 永远重试时，[DynamicRetryInterceptor] 应在绝对次数内终止并抛错。
 */
class DynamicRetryInterceptorSafetyTest {

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

    private val alwaysRetry = object : RetryStrategy {
        override fun shouldRetry(
            request: okhttp3.Request,
            response: Response?,
            error: IOException?,
            attempt: Int
        ) = true

        override fun nextDelayMillis(attempt: Int) = 0L
    }

    @Test
    fun `stops when RetryStrategy always returns true`() {
        repeat(DynamicRetryInterceptor.ABSOLUTE_MAX_RETRY_ROUNDS + 5) {
            server.enqueue(MockResponse().setResponseCode(500))
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(
                DynamicRetryInterceptor(
                    fallbackStrategy = alwaysRetry,
                    minJitteredBackoffLowerBoundMs = 0L
                )
            )
            .build()

        val request = Request.Builder().url(server.url("/")).build()
        try {
            client.newCall(request).execute()
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(
                e.message?.contains("safety cap", ignoreCase = true) == true
            )
        }

        assertEquals(
            DynamicRetryInterceptor.ABSOLUTE_MAX_RETRY_ROUNDS.toLong(),
            server.requestCount.toLong()
        )
    }
}
