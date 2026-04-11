package com.answufeng.net.http.util

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/**
 * DefaultRetryStrategy 重试逻辑的单元测试。
 */
class DefaultRetryStrategyTest {

    private val strategy = DefaultRetryStrategy(
        maxRetries = 3,
        initialBackoffMillis = 100,
        maxBackoffMillis = 2000,
        factor = 2.0
    )

    private fun request(method: String = "GET"): Request =
        Request.Builder().url("https://example.com").method(method, null).build()

    private fun response(code: Int, request: Request = request()): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("msg")
            .build()

    // ==================== shouldRetry ====================

    @Test
    fun `retries GET on IOException`() {
        assertTrue(strategy.shouldRetry(request("GET"), null, IOException("timeout"), 0))
    }

    @Test
    fun `retries HEAD on IOException`() {
        assertTrue(strategy.shouldRetry(request("HEAD"), null, IOException(), 0))
    }

    @Test
    fun `retries PUT on IOException`() {
        val req = Request.Builder().url("https://example.com").put(
            ByteArray(0).toRequestBody(null)
        ).build()
        assertTrue(strategy.shouldRetry(req, null, IOException(), 0))
    }

    @Test
    fun `retries DELETE on IOException`() {
        val req = Request.Builder().url("https://example.com").delete().build()
        assertTrue(strategy.shouldRetry(req, null, IOException(), 0))
    }

    @Test
    fun `does not retry POST on IOException`() {
        val req = Request.Builder().url("https://example.com").post(
            ByteArray(0).toRequestBody(null)
        ).build()
        assertFalse(strategy.shouldRetry(req, null, IOException(), 0))
    }

    @Test
    fun `does not retry PATCH on IOException`() {
        val req = Request.Builder().url("https://example.com").patch(
            ByteArray(0).toRequestBody(null)
        ).build()
        assertFalse(strategy.shouldRetry(req, null, IOException(), 0))
    }

    @Test
    fun `retries GET on 500`() {
        val req = request("GET")
        assertTrue(strategy.shouldRetry(req, response(500, req), null, 0))
    }

    @Test
    fun `retries GET on 502`() {
        val req = request("GET")
        assertTrue(strategy.shouldRetry(req, response(502, req), null, 0))
    }

    @Test
    fun `retries GET on 503`() {
        val req = request("GET")
        assertTrue(strategy.shouldRetry(req, response(503, req), null, 0))
    }

    @Test
    fun `does not retry GET on 404`() {
        val req = request("GET")
        assertFalse(strategy.shouldRetry(req, response(404, req), null, 0))
    }

    @Test
    fun `does not retry GET on 200`() {
        val req = request("GET")
        assertFalse(strategy.shouldRetry(req, response(200, req), null, 0))
    }

    @Test
    fun `does not retry when attempt exceeds maxRetries`() {
        assertFalse(strategy.shouldRetry(request(), null, IOException(), 3))
    }

    @Test
    fun `retries at boundary attempt`() {
        assertTrue(strategy.shouldRetry(request(), null, IOException(), 2))
    }

    @Test
    fun `does not retry when no error and no response`() {
        assertFalse(strategy.shouldRetry(request(), null, null, 0))
    }

    // ==================== nextDelayMillis ====================

    @Test
    fun `delay increases exponentially`() {
        assertEquals(100L, strategy.nextDelayMillis(0))    // 100 * 2^0
        assertEquals(200L, strategy.nextDelayMillis(1))    // 100 * 2^1
        assertEquals(400L, strategy.nextDelayMillis(2))    // 100 * 2^2
    }

    @Test
    fun `delay is capped at maxBackoffMillis`() {
        assertEquals(2000L, strategy.nextDelayMillis(10))  // would be 100 * 2^10 = 102400, capped
    }

    @Test
    fun `default strategy uses defaults`() {
        val defaultStrategy = DefaultRetryStrategy()
        assertEquals(300L, defaultStrategy.nextDelayMillis(0))
        assertEquals(600L, defaultStrategy.nextDelayMillis(1))
    }
}
