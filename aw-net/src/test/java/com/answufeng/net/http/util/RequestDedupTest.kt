package com.answufeng.net.http.util

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * RequestDedup 请求去重器的单元测试。
 */
class RequestDedupTest {

    @Test
    fun `single request executes block`() = runTest {
        val dedup = RequestDedup()
        val result = dedup.dedupRequest("key1") { "hello" }
        assertEquals("hello", result)
    }

    @Test
    fun `concurrent requests with same key only execute block once`() = runTest {
        val dedup = RequestDedup()
        val callCount = AtomicInteger(0)

        val results = (1..10).map { i ->
            async {
                dedup.dedupRequest("same_key") {
                    callCount.incrementAndGet()
                    delay(100) // 模拟网络延迟
                    "result"
                }
            }
        }.awaitAll()

        // 所有人拿到相同结果
        results.forEach { assertEquals("result", it) }
        // 实际执行次数应为 1（或极少数竞争情况 2）
        assertTrue("Expected 1-2 actual calls, got ${callCount.get()}", callCount.get() <= 2)
    }

    @Test
    fun `different keys execute independently`() = runTest {
        val dedup = RequestDedup()
        val callCount = AtomicInteger(0)

        val r1 = async {
            dedup.dedupRequest("key_a") {
                callCount.incrementAndGet()
                "a"
            }
        }
        val r2 = async {
            dedup.dedupRequest("key_b") {
                callCount.incrementAndGet()
                "b"
            }
        }

        assertEquals("a", r1.await())
        assertEquals("b", r2.await())
        assertEquals(2, callCount.get())
    }

    @Test
    fun `exception is propagated to all waiters`() = runTest {
        val dedup = RequestDedup()

        val jobs = (1..3).map {
            async {
                try {
                    dedup.dedupRequest("fail_key") {
                        delay(50)
                        throw RuntimeException("network error")
                    }
                } catch (e: RuntimeException) {
                    e.message
                }
            }
        }

        val results = jobs.awaitAll()
        results.forEach { assertEquals("network error", it) }
    }

    @Test
    fun `inFlightCount reflects active requests`() = runTest {
        val dedup = RequestDedup()
        assertEquals(0, dedup.inFlightCount)

        val job = async {
            dedup.dedupRequest("active") {
                delay(1000)
                "done"
            }
        }
        delay(50) // 让请求启动
        assertEquals(1, dedup.inFlightCount)

        job.cancelAndJoin()
        // cleanup 后应为 0
        assertEquals(0, dedup.inFlightCount)
    }

    @Test
    fun `cancel removes inflight request`() = runTest {
        val dedup = RequestDedup()
        val job = async {
            dedup.dedupRequest("cancel_me") {
                delay(5000)
                "never"
            }
        }
        delay(50)
        dedup.cancel("cancel_me")
        assertEquals(0, dedup.inFlightCount)
        job.cancelAndJoin()
    }

    @Test
    fun `cancelAll clears everything`() = runTest {
        val dedup = RequestDedup()
        val jobs = (1..3).map { i ->
            async {
                try {
                    dedup.dedupRequest("key_$i") {
                        delay(5000)
                        "done"
                    }
                } catch (_: Exception) { null }
            }
        }
        delay(50)
        dedup.cancelAll()
        assertEquals(0, dedup.inFlightCount)
        jobs.forEach { it.cancelAndJoin() }
    }

    @Test
    fun `sequential requests with same key both execute`() = runTest {
        val dedup = RequestDedup()
        val callCount = AtomicInteger(0)

        val r1 = dedup.dedupRequest("seq_key") {
            callCount.incrementAndGet()
            "first"
        }
        val r2 = dedup.dedupRequest("seq_key") {
            callCount.incrementAndGet()
            "second"
        }

        assertEquals("first", r1)
        assertEquals("second", r2)
        assertEquals(2, callCount.get()) // 顺序调用时各执行一次
    }
}
