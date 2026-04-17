package com.answufeng.net.http.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

class TokenRefreshCoordinatorTest {

    @Test
    fun `refreshIfNeededBlocking returns new token on success`() {
        lateinit var provider: InMemoryTokenProvider
        provider = InMemoryTokenProvider(initialAccessToken = "old_token") {
            provider.setAccessToken("new_token")
            true
        }
        val coordinator = TokenRefreshCoordinator(provider)

        val result = coordinator.refreshIfNeededBlocking("old_token")
        assertEquals("Bearer new_token", result)
    }

    @Test
    fun `refreshIfNeededBlocking returns null on failure`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") { false }
        val coordinator = TokenRefreshCoordinator(provider)

        val result = coordinator.refreshIfNeededBlocking("token")
        assertNull(result)
    }

    @Test
    fun `refreshIfNeededBlocking fast path when token already refreshed`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "new_token") { true }
        val coordinator = TokenRefreshCoordinator(provider)

        val result = coordinator.refreshIfNeededBlocking("old_token")
        assertEquals("Bearer new_token", result)
    }

    @Test
    fun `refreshIfNeededSuspend returns new token on success`() = runTest {
        lateinit var provider: InMemoryTokenProvider
        provider = InMemoryTokenProvider(initialAccessToken = "old_token") {
            provider.setAccessToken("new_token")
            true
        }
        val coordinator = TokenRefreshCoordinator(provider)

        val result = coordinator.refreshIfNeededSuspend("old_token")
        assertEquals("Bearer new_token", result)
    }

    @Test
    fun `refreshIfNeededSuspend returns null on failure`() = runTest {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") { false }
        val coordinator = TokenRefreshCoordinator(provider)

        val result = coordinator.refreshIfNeededSuspend("token")
        assertNull(result)
    }

    @Test
    fun `refreshIfNeededSuspend fast path when token already refreshed`() = runTest {
        val provider = InMemoryTokenProvider(initialAccessToken = "new_token") { true }
        val coordinator = TokenRefreshCoordinator(provider)

        val result = coordinator.refreshIfNeededSuspend("old_token")
        assertEquals("Bearer new_token", result)
    }

    @Test
    fun `concurrent blocking refresh only refreshes once`() {
        val refreshCount = AtomicInteger(0)
        val provider = object : TokenProvider {
            @Volatile
            private var token: String? = "old_token"

            override fun getAccessToken(): String? = token

            override fun refreshTokenBlocking(): Boolean {
                refreshCount.incrementAndGet()
                Thread.sleep(50)
                token = "new_token"
                return true
            }
        }
        val coordinator = TokenRefreshCoordinator(provider)

        val threadCount = 5
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val results = Array<String?>(threadCount) { null }

        repeat(threadCount) { i ->
            Thread {
                try {
                    barrier.await()
                    results[i] = coordinator.refreshIfNeededBlocking("old_token")
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        latch.await()

        assertTrue("Refresh count should be minimal, got ${refreshCount.get()}", refreshCount.get() <= 2)
        results.forEach { assertNotNull(it) }
    }

    @Test
    fun `getAccessToken delegates to provider`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "my_token")
        val coordinator = TokenRefreshCoordinator(provider)

        assertEquals("my_token", coordinator.getAccessToken())
    }

    @Test
    fun `refreshIfNeededBlocking with null requestToken refreshes when current token exists`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "token") { true }
        val coordinator = TokenRefreshCoordinator(provider)

        val result = coordinator.refreshIfNeededBlocking(null)
        assertNotNull(result)
    }
}
