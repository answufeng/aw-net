package com.answufeng.net.http.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * RequestThrottle 请求节流器的单元测试。
 */
class RequestThrottleTest {

    @Test
    fun `first request executes block`() = runTest {
        val throttle = RequestThrottle(intervalMs = 5000)
        val result = throttle.throttleRequest("key1") { "fresh" }
        assertEquals("fresh", result)
    }

    @Test
    fun `repeated request within interval returns cached result`() = runTest {
        val throttle = RequestThrottle(intervalMs = 60_000) // 60秒间隔，测试中不会过期
        val callCount = AtomicInteger(0)

        val r1 = throttle.throttleRequest("key") {
            callCount.incrementAndGet()
            "first"
        }
        val r2 = throttle.throttleRequest("key") {
            callCount.incrementAndGet()
            "second"
        }

        assertEquals("first", r1)
        assertEquals("first", r2) // 拿到缓存结果
        assertEquals(1, callCount.get())
    }

    @Test
    fun `different keys are independent`() = runTest {
        val throttle = RequestThrottle(intervalMs = 60_000)
        val callCount = AtomicInteger(0)

        throttle.throttleRequest("a") { callCount.incrementAndGet(); "a_val" }
        throttle.throttleRequest("b") { callCount.incrementAndGet(); "b_val" }

        assertEquals(2, callCount.get())
    }

    @Test
    fun `invalidate forces re-execution`() = runTest {
        val throttle = RequestThrottle(intervalMs = 60_000)
        val callCount = AtomicInteger(0)

        throttle.throttleRequest("key") { callCount.incrementAndGet(); "v1" }
        throttle.invalidate("key")
        val r2 = throttle.throttleRequest("key") { callCount.incrementAndGet(); "v2" }

        assertEquals("v2", r2)
        assertEquals(2, callCount.get())
    }

    @Test
    fun `invalidateAll clears all cache`() = runTest {
        val throttle = RequestThrottle(intervalMs = 60_000)

        throttle.throttleRequest("a") { "1" }
        throttle.throttleRequest("b") { "2" }
        throttle.invalidateAll()

        val callCount = AtomicInteger(0)
        throttle.throttleRequest("a") { callCount.incrementAndGet(); "new_a" }
        throttle.throttleRequest("b") { callCount.incrementAndGet(); "new_b" }

        assertEquals(2, callCount.get())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative intervalMs throws`() {
        RequestThrottle(intervalMs = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero intervalMs throws`() {
        RequestThrottle(intervalMs = 0)
    }

    @Test
    fun `expired cache triggers re-execution`() = runTest {
        val throttle = RequestThrottle(intervalMs = 1) // 1ms 间隔
        val callCount = AtomicInteger(0)

        throttle.throttleRequest("key") { callCount.incrementAndGet(); "v1" }
        // 等待缓存过期
        Thread.sleep(10)
        val r2 = throttle.throttleRequest("key") { callCount.incrementAndGet(); "v2" }

        assertEquals("v2", r2)
        assertEquals(2, callCount.get())
    }
}
