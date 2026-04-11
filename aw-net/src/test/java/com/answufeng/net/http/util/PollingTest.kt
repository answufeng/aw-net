package com.answufeng.net.http.util

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * pollingFlow 轮询工具的单元测试。
 */
class PollingTest {

    @Test
    fun `emits block results up to maxAttempts`() = runTest {
        var counter = 0
        val results = pollingFlow(
            periodMillis = 100,
            maxAttempts = 3,
            block = { counter++ }
        ).toList()

        assertEquals(listOf(0, 1, 2), results)
    }

    @Test
    fun `stops when stopWhen returns true`() = runTest {
        var counter = 0
        val results = pollingFlow(
            periodMillis = 100,
            maxAttempts = 100,
            stopWhen = { it >= 2 },
            block = { counter++ }
        ).toList()

        // counter=0 emitted (stopWhen false), counter=1 emitted (stopWhen false), counter=2 emitted (stopWhen true, break)
        assertEquals(listOf(0, 1, 2), results)
    }

    @Test
    fun `single attempt emits once`() = runTest {
        val results = pollingFlow(
            periodMillis = 100,
            maxAttempts = 1,
            block = { "data" }
        ).toList()

        assertEquals(listOf("data"), results)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on non-positive periodMillis`() = runTest {
        pollingFlow(
            periodMillis = 0,
            block = { "data" }
        ).toList()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on non-positive maxAttempts`() = runTest {
        pollingFlow(
            periodMillis = 100,
            maxAttempts = 0,
            block = { "data" }
        ).toList()
    }

    @Test
    fun `stopWhen on first emission stops immediately`() = runTest {
        val results = pollingFlow(
            periodMillis = 100,
            maxAttempts = 100,
            stopWhen = { true },
            block = { "stop" }
        ).toList()

        assertEquals(listOf("stop"), results)
    }

    @Test
    fun `accumulates state across invocations`() = runTest {
        val list = mutableListOf<String>()
        val results = pollingFlow(
            periodMillis = 50,
            maxAttempts = 3,
            block = {
                list.add("item")
                list.size
            }
        ).toList()

        assertEquals(listOf(1, 2, 3), results)
    }
}
