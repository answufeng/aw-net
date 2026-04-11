package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.model.GlobalResponse
import com.answufeng.net.http.model.IBaseResponse
import com.answufeng.net.http.model.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

/**
 * RequestExecutor 协程级重试功能测试。
 */
class RequestExecutorRetryTest {

    private lateinit var configProvider: NetworkConfigProvider
    private lateinit var executor: RequestExecutor

    @Before
    fun setUp() {
        configProvider = NetworkConfigProvider(
            NetworkConfig(baseUrl = "https://test.example.com/")
        )
        executor = RequestExecutor(
            configProvider = configProvider,
            tokenProviderOptional = Optional.empty<TokenProvider>(),
            unauthorizedHandlerOptional = Optional.empty<UnauthorizedHandler>()
        )
    }

    // ==================== executeRequest 重试 ====================

    @Test
    fun `executeRequest - no retry by default`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRequest<String>(
            dispatcher = Dispatchers.Unconfined
        ) {
            callCount.incrementAndGet()
            throw IOException("network error")
        }

        assertTrue(result is NetworkResult.TechnicalFailure)
        assertEquals(1, callCount.get())
    }

    @Test
    fun `executeRequest - retries on technical failure`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 2,
            retryDelayMs = 10
        ) {
            val count = callCount.incrementAndGet()
            if (count < 3) throw IOException("network error")
            GlobalResponse(code = 0, msg = "ok", data = "success")
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals("success", (result as NetworkResult.Success).data)
        assertEquals(3, callCount.get()) // 1 initial + 2 retries
    }

    @Test
    fun `executeRequest - retries exhausted returns last failure`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 2,
            retryDelayMs = 10
        ) {
            callCount.incrementAndGet()
            throw IOException("persistent error")
        }

        assertTrue(result is NetworkResult.TechnicalFailure)
        assertEquals(3, callCount.get()) // 1 initial + 2 retries, 全部失败
    }

    @Test
    fun `executeRequest - does not retry on business failure by default`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 3,
            retryDelayMs = 10,
            retryOnBusiness = false // 默认值
        ) {
            callCount.incrementAndGet()
            GlobalResponse(code = 1001, msg = "business error", data = null)
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(1, callCount.get()) // 不重试业务错误
    }

    @Test
    fun `executeRequest - retries on business failure when enabled`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 2,
            retryDelayMs = 10,
            retryOnBusiness = true
        ) {
            val count = callCount.incrementAndGet()
            if (count < 3) {
                GlobalResponse(code = 1001, msg = "business error", data = null)
            } else {
                GlobalResponse(code = 0, msg = "ok", data = "recovered")
            }
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals("recovered", (result as NetworkResult.Success).data)
        assertEquals(3, callCount.get())
    }

    @Test
    fun `executeRequest - does not retry 401 via coroutine retry`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 3,
            retryDelayMs = 10,
            retryOnBusiness = true // 即使开启了业务重试
        ) {
            callCount.incrementAndGet()
            GlobalResponse(code = 401, msg = "unauthorized", data = null)
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(401, (result as NetworkResult.BusinessFailure).code)
        assertEquals(1, callCount.get()) // 401 不通过协程级重试
    }

    @Test
    fun `executeRequest - success on first try does not retry`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 5,
            retryDelayMs = 10
        ) {
            callCount.incrementAndGet()
            GlobalResponse(code = 0, msg = "ok", data = "first")
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals("first", (result as NetworkResult.Success).data)
        assertEquals(1, callCount.get()) // 首次成功，不重试
    }

    @Test
    fun `executeRequest - retryOnTechnical=false does not retry technical failure`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 3,
            retryDelayMs = 10,
            retryOnTechnical = false
        ) {
            callCount.incrementAndGet()
            throw IOException("network error")
        }

        assertTrue(result is NetworkResult.TechnicalFailure)
        assertEquals(1, callCount.get()) // 技术错误重试被禁用
    }

    // ==================== executeRawRequest 重试 ====================

    @Test
    fun `executeRawRequest - no retry by default`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRawRequest<String>(
            dispatcher = Dispatchers.Unconfined
        ) {
            callCount.incrementAndGet()
            throw IOException("network error")
        }

        assertTrue(result is NetworkResult.TechnicalFailure)
        assertEquals(1, callCount.get())
    }

    @Test
    fun `executeRawRequest - retries on failure`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRawRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 2,
            retryDelayMs = 10
        ) {
            val count = callCount.incrementAndGet()
            if (count < 3) throw IOException("network error")
            "success"
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals("success", (result as NetworkResult.Success).data)
        assertEquals(3, callCount.get())
    }

    @Test
    fun `executeRawRequest - retries exhausted returns failure`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRawRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 1,
            retryDelayMs = 10
        ) {
            callCount.incrementAndGet()
            throw IOException("persistent error")
        }

        assertTrue(result is NetworkResult.TechnicalFailure)
        assertEquals(2, callCount.get()) // 1 initial + 1 retry
    }

    @Test
    fun `executeRawRequest - success on first try does not retry`() = runTest {
        val callCount = AtomicInteger(0)
        val result = executor.executeRawRequest<String>(
            dispatcher = Dispatchers.Unconfined,
            retryOnFailure = 5,
            retryDelayMs = 10
        ) {
            callCount.incrementAndGet()
            "immediate_success"
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals("immediate_success", (result as NetworkResult.Success).data)
        assertEquals(1, callCount.get())
    }
}
