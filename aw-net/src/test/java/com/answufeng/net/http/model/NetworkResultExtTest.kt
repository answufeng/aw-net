package com.answufeng.net.http.model

import com.answufeng.net.http.exception.RequestException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * NetworkResultExt 扩展函数的补充测试。
 * 覆盖 recover / recoverWith / onSuccessNotNull / fold / map / getOrThrow 等。
 */
class NetworkResultExtTest {

    // ==================== onSuccessNotNull ====================

    @Test
    fun `onSuccessNotNull invoked when data is non-null`() {
        var captured: String? = null
        val result: NetworkResult<String> = NetworkResult.Success("hello")
        result.onSuccessNotNull { captured = it }
        assertEquals("hello", captured)
    }

    @Test
    fun `onSuccessNotNull not invoked when data is null`() {
        var invoked = false
        val result: NetworkResult<String> = NetworkResult.Success(null)
        result.onSuccessNotNull { invoked = true }
        assertFalse(invoked)
    }

    @Test
    fun `onSuccessNotNull not invoked on failure`() {
        var invoked = false
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(1001, "error")
        result.onSuccessNotNull { invoked = true }
        assertFalse(invoked)
    }

    // ==================== recover ====================

    @Test
    fun `recover returns original on success`() {
        val result: NetworkResult<String> = NetworkResult.Success("ok")
        val recovered = result.recover { "fallback" }
        assertEquals("ok", (recovered as NetworkResult.Success).data)
    }

    @Test
    fun `recover transforms TechnicalFailure to Success`() {
        val ex = RequestException(-1, "timeout")
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(ex)
        val recovered = result.recover { "default" }
        assertTrue(recovered is NetworkResult.Success)
        assertEquals("default", recovered.getOrNull())
    }

    @Test
    fun `recover transforms BusinessFailure to Success`() {
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(400, "bad")
        val recovered = result.recover { "fallback" }
        assertTrue(recovered is NetworkResult.Success)
        assertEquals("fallback", recovered.getOrNull())
    }

    // ==================== recoverWith ====================

    @Test
    fun `recoverWith returns original on success`() = runTest {
        val result: NetworkResult<String> = NetworkResult.Success("ok")
        val recovered = result.recoverWith { NetworkResult.Success("other") }
        assertEquals("ok", recovered.getOrNull())
    }

    @Test
    fun `recoverWith invokes fallback on failure`() = runTest {
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(RequestException(-1, "err"))
        val recovered = result.recoverWith { NetworkResult.Success("recovered") }
        assertTrue(recovered is NetworkResult.Success)
        assertEquals("recovered", recovered.getOrNull())
    }

    @Test
    fun `recoverWith can produce another failure`() = runTest {
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(RequestException(-1, "err"))
        val recovered = result.recoverWith { NetworkResult.BusinessFailure(500, "still failed") }
        assertTrue(recovered is NetworkResult.BusinessFailure)
    }

    // ==================== map ====================

    @Test
    fun `map transforms success data`() {
        val result: NetworkResult<Int> = NetworkResult.Success(42)
        val mapped = result.map { (it ?: 0) * 2 }
        assertEquals(84, (mapped as NetworkResult.Success).data)
    }

    @Test
    fun `map preserves TechnicalFailure`() {
        val ex = RequestException(-1, "err")
        val result: NetworkResult<Int> = NetworkResult.TechnicalFailure(ex)
        val mapped = result.map { (it ?: 0) * 2 }
        assertTrue(mapped is NetworkResult.TechnicalFailure)
    }

    @Test
    fun `map preserves BusinessFailure`() {
        val result: NetworkResult<Int> = NetworkResult.BusinessFailure(404, "not found")
        val mapped = result.map { (it ?: 0) * 2 }
        assertTrue(mapped is NetworkResult.BusinessFailure)
        assertEquals(404, (mapped as NetworkResult.BusinessFailure).code)
    }

    // ==================== fold ====================

    @Test
    fun `fold routes to correct branch on success`() {
        val result: NetworkResult<String> = NetworkResult.Success("data")
        val output = result.fold(
            onSuccess = { "success:$it" },
            onTechnicalFailure = { "tech" },
            onBusinessFailure = { _, _ -> "biz" }
        )
        assertEquals("success:data", output)
    }

    @Test
    fun `fold routes to TechnicalFailure branch`() {
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(RequestException(-1, "err"))
        val output = result.fold(
            onSuccess = { "success" },
            onTechnicalFailure = { "tech:${it.message}" },
            onBusinessFailure = { _, _ -> "biz" }
        )
        assertEquals("tech:err", output)
    }

    @Test
    fun `fold routes to BusinessFailure branch`() {
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(401, "unauthorized")
        val output = result.fold(
            onSuccess = { "success" },
            onTechnicalFailure = { "tech" },
            onBusinessFailure = { code, msg -> "biz:$code:$msg" }
        )
        assertEquals("biz:401:unauthorized", output)
    }

    // ==================== getOrThrow ====================

    @Test
    fun `getOrThrow returns data on success`() {
        val result: NetworkResult<String> = NetworkResult.Success("ok")
        assertEquals("ok", result.getOrThrow())
    }

    @Test(expected = RequestException::class)
    fun `getOrThrow throws on TechnicalFailure`() {
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(RequestException(-1, "err"))
        result.getOrThrow()
    }

    @Test(expected = IllegalStateException::class)
    fun `getOrThrow throws on BusinessFailure`() {
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(400, "bad")
        result.getOrThrow()
    }

    // ==================== onFailure ====================

    @Test
    fun `onFailure invoked on TechnicalFailure`() {
        var captured: Any? = null
        val result: NetworkResult<String> = NetworkResult.TechnicalFailure(RequestException(-1, "err"))
        result.onFailure { captured = it }
        assertNotNull(captured)
    }

    @Test
    fun `onFailure invoked on BusinessFailure`() {
        var captured: Any? = null
        val result: NetworkResult<String> = NetworkResult.BusinessFailure(400, "bad")
        result.onFailure { captured = it }
        assertNotNull(captured)
    }

    @Test
    fun `onFailure not invoked on Success`() {
        var invoked = false
        val result: NetworkResult<String> = NetworkResult.Success("ok")
        result.onFailure { invoked = true }
        assertFalse(invoked)
    }

    // ==================== chaining ====================

    @Test
    fun `chaining onSuccess and onFailure both return same result`() {
        var successInvoked = false
        var failureInvoked = false
        val result: NetworkResult<String> = NetworkResult.Success("data")

        val chained = result
            .onSuccess { successInvoked = true }
            .onFailure { failureInvoked = true }

        assertTrue(successInvoked)
        assertFalse(failureInvoked)
        assertSame(result, chained)
    }
}
