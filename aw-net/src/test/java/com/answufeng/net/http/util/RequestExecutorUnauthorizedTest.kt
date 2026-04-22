package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.auth.InMemoryTokenProvider
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.model.GlobalResponse
import com.answufeng.net.http.model.NetCode
import com.answufeng.net.http.model.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

class RequestExecutorUnauthorizedTest {

    private lateinit var configProvider: NetworkConfigProvider

    @Before
    fun setUp() {
        configProvider = NetworkConfigProvider(
            NetworkConfig(baseUrl = "https://test.example.com/")
        )
    }

    @Test
    fun `business 401 without coordinator notifies handler`() = runTest {
        val handlerCalls = AtomicInteger(0)
        val handler = UnauthorizedHandler { handlerCalls.incrementAndGet() }
        val executor = RequestExecutor(
            configProvider = configProvider,
            refreshCoordinator = null,
            unauthorizedHandlerOptional = Optional.of(handler)
        )

        val result = executor.executeRequest<String>(dispatcher = Dispatchers.Unconfined) {
            GlobalResponse(code = NetCode.Business.UNAUTHORIZED, msg = "unauthorized", data = null)
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(1, handlerCalls.get())
    }

    @Test
    fun `business 401 with successful refresh retries call`() = runTest {
        lateinit var provider: InMemoryTokenProvider
        provider = InMemoryTokenProvider(initialAccessToken = "old_token") {
            provider.setAccessToken("new_token")
            true
        }
        val coordinator = TokenRefreshCoordinator(provider)
        val callCount = AtomicInteger(0)

        val executor = RequestExecutor(
            configProvider = configProvider,
            refreshCoordinator = coordinator,
            unauthorizedHandlerOptional = Optional.empty<UnauthorizedHandler>()
        )

        val result = executor.executeRequest<String>(dispatcher = Dispatchers.Unconfined) {
            val count = callCount.incrementAndGet()
            if (count == 1) {
                GlobalResponse(code = NetCode.Business.UNAUTHORIZED, msg = "unauthorized", data = null)
            } else {
                GlobalResponse(code = 0, msg = "ok", data = "success")
            }
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals("success", (result as NetworkResult.Success).data)
        assertEquals(2, callCount.get())
    }

    @Test
    fun `business 401 with failed refresh notifies handler once`() = runTest {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") { false }
        val coordinator = TokenRefreshCoordinator(provider)
        val handlerCalls = AtomicInteger(0)
        val handler = UnauthorizedHandler { handlerCalls.incrementAndGet() }

        val executor = RequestExecutor(
            configProvider = configProvider,
            refreshCoordinator = coordinator,
            unauthorizedHandlerOptional = Optional.of(handler)
        )

        val result = executor.executeRequest<String>(dispatcher = Dispatchers.Unconfined) {
            GlobalResponse(code = NetCode.Business.UNAUTHORIZED, msg = "unauthorized", data = null)
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(401, (result as NetworkResult.BusinessFailure).code)
        assertEquals(1, handlerCalls.get())
    }

    @Test
    fun `non-401 business failure does not trigger refresh`() = runTest {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") { true }
        val coordinator = TokenRefreshCoordinator(provider)

        val executor = RequestExecutor(
            configProvider = configProvider,
            refreshCoordinator = coordinator,
            unauthorizedHandlerOptional = Optional.empty<UnauthorizedHandler>()
        )

        val result = executor.executeRequest<String>(dispatcher = Dispatchers.Unconfined) {
            GlobalResponse(code = 1001, msg = "business error", data = null)
        }

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(1001, (result as NetworkResult.BusinessFailure).code)
    }

    @Test
    fun `success result does not trigger refresh`() = runTest {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") { true }
        val coordinator = TokenRefreshCoordinator(provider)

        val executor = RequestExecutor(
            configProvider = configProvider,
            refreshCoordinator = coordinator,
            unauthorizedHandlerOptional = Optional.empty<UnauthorizedHandler>()
        )

        val result = executor.executeRequest<String>(dispatcher = Dispatchers.Unconfined) {
            GlobalResponse(code = 0, msg = "ok", data = "success")
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals("success", (result as NetworkResult.Success).data)
    }
}
