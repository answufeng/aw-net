package com.answufeng.net.http.model

import com.answufeng.net.http.exception.RequestException
import org.junit.Assert.*
import org.junit.Test

/**
 * NetworkResult 及其扩展函数的单元测试。
 */
class NetworkResultTest {

    // ==================== 基本类型测试 ====================

    @Test
    fun `Success with data`() {
        val result: NetworkResult<String> = NetworkResult.Success("hello")
        assertTrue(result.isSuccess())
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `Success with null data`() {
        val result: NetworkResult<String> = NetworkResult.Success(null)
        assertTrue(result.isSuccess())
        assertNull(result.getOrNull())
    }

    @Test
    fun `TechnicalFailure returns null on getOrNull`() {
        val ex = RequestException(-1, "timeout")
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(ex)
        assertFalse(result.isSuccess())
        assertNull(result.getOrNull())
    }

    @Test
    fun `BusinessFailure returns null on getOrNull`() {
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(1001, "token expired")
        assertFalse(result.isSuccess())
        assertNull(result.getOrNull())
    }

    // ==================== getOrDefault ====================

    @Test
    fun `getOrDefault returns data on Success`() {
        val result: NetworkResult<String> = NetworkResult.Success("data")
        assertEquals("data", result.getOrDefault("default"))
    }

    @Test
    fun `getOrDefault returns default on null data`() {
        val result: NetworkResult<String> = NetworkResult.Success(null)
        assertEquals("default", result.getOrDefault("default"))
    }

    @Test
    fun `getOrDefault returns default on failure`() {
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(1, "err")
        assertEquals("default", result.getOrDefault("default"))
    }

    // ==================== getOrThrow ====================

    @Test
    fun `getOrThrow returns data on Success`() {
        val result: NetworkResult<String> = NetworkResult.Success("ok")
        assertEquals("ok", result.getOrThrow())
    }

    @Test(expected = RequestException::class)
    fun `getOrThrow throws on TechnicalFailure`() {
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(
            RequestException(-1, "network error")
        )
        result.getOrThrow()
    }

    @Test(expected = IllegalStateException::class)
    fun `getOrThrow throws on BusinessFailure`() {
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(401, "unauthorized")
        result.getOrThrow()
    }

    // ==================== onSuccess / onFailure callbacks ====================

    @Test
    fun `onSuccess callback fires on Success`() {
        var captured: String? = null
        val result: NetworkResult<String> = NetworkResult.Success("value")
        result.onSuccess { captured = it }
        assertEquals("value", captured)
    }

    @Test
    fun `onSuccess callback does not fire on failure`() {
        var fired = false
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(1, "err")
        result.onSuccess { fired = true }
        assertFalse(fired)
    }

    @Test
    fun `onBusinessFailure callback fires`() {
        var capturedCode = 0
        var capturedMsg = ""
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(1001, "expired")
        result.onBusinessFailure { code, msg ->
            capturedCode = code
            capturedMsg = msg
        }
        assertEquals(1001, capturedCode)
        assertEquals("expired", capturedMsg)
    }

    @Test
    fun `onTechnicalFailure callback fires`() {
        var capturedEx: Throwable? = null
        val ex = RequestException(-2, "no network")
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(ex)
        result.onTechnicalFailure { capturedEx = it }
        assertSame(ex, capturedEx)
    }

    // ==================== chaining ====================

    @Test
    fun `callbacks can be chained`() {
        var successFired = false
        var failFired = false

        NetworkResult.Success("ok")
            .onSuccess { successFired = true }
            .onBusinessFailure { _, _ -> failFired = true }

        assertTrue(successFired)
        assertFalse(failFired)
    }

    // ==================== map ====================

    @Test
    fun `map transforms Success data`() {
        val result = NetworkResult.Success(42).map { (it ?: 0) * 2 }
        assertEquals(84, (result as NetworkResult.Success).data)
    }

    @Test
    fun `map preserves TechnicalFailure`() {
        val ex = RequestException(-1, "timeout")
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(ex)
        val mapped = result.map { it?.length ?: 0 }
        assertTrue(mapped is NetworkResult.TechnicalFailure)
    }

    @Test
    fun `map preserves BusinessFailure`() {
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(404, "not found")
        val mapped = result.map { it?.length ?: 0 }
        assertTrue(mapped is NetworkResult.BusinessFailure)
        assertEquals(404, (mapped as NetworkResult.BusinessFailure).code)
    }

    // ==================== fold ====================

    @Test
    fun `fold handles Success`() {
        val text = NetworkResult.Success("hello").fold(
            onSuccess = { "data: $it" },
            onTechnicalFailure = { "tech error" },
            onBusinessFailure = { _, msg -> "biz: $msg" }
        )
        assertEquals("data: hello", text)
    }

    @Test
    fun `fold handles TechnicalFailure`() {
        val text = NetworkResult.TechnicalFailure(RequestException(-1, "timeout")).fold(
            onSuccess = { "ok" },
            onTechnicalFailure = { "tech: ${it.message}" },
            onBusinessFailure = { _, msg -> "biz: $msg" }
        )
        assertEquals("tech: timeout", text)
    }

    @Test
    fun `fold handles BusinessFailure`() {
        val text = NetworkResult.BusinessFailure(401, "unauthorized").fold(
            onSuccess = { "ok" },
            onTechnicalFailure = { "tech" },
            onBusinessFailure = { code, msg -> "$code: $msg" }
        )
        assertEquals("401: unauthorized", text)
    }
}
