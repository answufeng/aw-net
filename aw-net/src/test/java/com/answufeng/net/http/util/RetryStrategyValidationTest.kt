package com.answufeng.net.http.util

import org.junit.Assert.*
import org.junit.Test

/**
 * DefaultRetryStrategy 输入参数校验测试。
 */
class RetryStrategyValidationTest {

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative maxRetries`() {
        DefaultRetryStrategy(maxRetries = -1)
    }

    @Test
    fun `allows zero maxRetries`() {
        val strategy = DefaultRetryStrategy(maxRetries = 0)
        // maxRetries = 0 means no retries at all
        assertNotNull(strategy)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero initialBackoffMillis`() {
        DefaultRetryStrategy(initialBackoffMillis = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative initialBackoffMillis`() {
        DefaultRetryStrategy(initialBackoffMillis = -100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects maxBackoff less than initialBackoff`() {
        DefaultRetryStrategy(initialBackoffMillis = 1000, maxBackoffMillis = 500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects factor less than 1`() {
        DefaultRetryStrategy(factor = 0.5)
    }

    @Test
    fun `allows factor equal to 1`() {
        val strategy = DefaultRetryStrategy(factor = 1.0)
        // factor = 1.0 means constant backoff
        assertEquals(300L, strategy.nextDelayMillis(0))
        assertEquals(300L, strategy.nextDelayMillis(1))
        assertEquals(300L, strategy.nextDelayMillis(2))
    }

    @Test
    fun `allows equal initial and max backoff`() {
        val strategy = DefaultRetryStrategy(initialBackoffMillis = 500, maxBackoffMillis = 500)
        assertEquals(500L, strategy.nextDelayMillis(0))
        assertEquals(500L, strategy.nextDelayMillis(5))
    }

    @Test
    fun `default parameters are valid`() {
        val strategy = DefaultRetryStrategy()
        assertNotNull(strategy)
    }
}
