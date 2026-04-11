package com.answufeng.net.http.annotations

import org.junit.Assert.*
import org.junit.Test

/**
 * NetworkConfigProvider 的原子更新、监听通知、并发安全测试。
 */
class NetworkConfigProviderTest {

    private val baseConfig = NetworkConfig(baseUrl = "https://api.example.com/")

    @Test
    fun `initial config returned correctly`() {
        val provider = NetworkConfigProvider(baseConfig)
        assertSame(baseConfig, provider.current)
    }

    @Test
    fun `updateConfig replaces config`() {
        val provider = NetworkConfigProvider(baseConfig)
        val newConfig = baseConfig.copy(connectTimeout = 30)
        provider.updateConfig(newConfig)
        assertSame(newConfig, provider.current)
    }

    @Test
    fun `update applies transform`() {
        val provider = NetworkConfigProvider(baseConfig)
        provider.update { it.copy(readTimeout = 60) }
        assertEquals(60L, provider.current.readTimeout)
    }

    @Test
    fun `listener notified on updateConfig`() {
        val provider = NetworkConfigProvider(baseConfig)
        var notified = false
        provider.registerListener { notified = true }
        provider.updateConfig(baseConfig.copy(connectTimeout = 10))
        assertTrue(notified)
    }

    @Test
    fun `listener notified on update`() {
        val provider = NetworkConfigProvider(baseConfig)
        var count = 0
        provider.registerListener { count++ }
        provider.update { it.copy(readTimeout = 10) }
        provider.update { it.copy(writeTimeout = 10) }
        assertEquals(2, count)
    }

    @Test
    fun `unregister function removes listener`() {
        val provider = NetworkConfigProvider(baseConfig)
        var count = 0
        val unregister = provider.registerListener { count++ }
        provider.updateConfig(baseConfig.copy(connectTimeout = 10))
        assertEquals(1, count)
        unregister()
        provider.updateConfig(baseConfig.copy(connectTimeout = 20))
        assertEquals(1, count) // 不再通知
    }

    @Test
    fun `listener exception does not impact other listeners`() {
        val provider = NetworkConfigProvider(baseConfig)
        var secondNotified = false
        provider.registerListener { error("boom") }
        provider.registerListener { secondNotified = true }
        provider.updateConfig(baseConfig.copy(connectTimeout = 5))
        assertTrue(secondNotified) // 第二个监听器仍然被通知
    }

    @Test
    fun `multiple listeners all notified`() {
        val provider = NetworkConfigProvider(baseConfig)
        val results = mutableListOf<Int>()
        provider.registerListener { results.add(1) }
        provider.registerListener { results.add(2) }
        provider.registerListener { results.add(3) }
        provider.updateConfig(baseConfig.copy(connectTimeout = 10))
        assertEquals(listOf(1, 2, 3), results)
    }

    @Test
    fun `concurrent updates do not lose data`() {
        val provider = NetworkConfigProvider(baseConfig)
        val threads = (1..100).map { i ->
            Thread { provider.update { it.copy(connectTimeout = i.toLong()) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // 最终 connectTimeout 应为 1-100 中的某个值
        assertTrue(provider.current.connectTimeout in 1..100)
    }
}
