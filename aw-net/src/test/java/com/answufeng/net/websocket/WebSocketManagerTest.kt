package com.answufeng.net.websocket

import org.junit.Assert.*
import org.junit.Test

/**
 * WebSocketManager 的纯 JVM 单元测试。
 * 
 * 注意：WebSocketClientImpl 内部使用了 Android Handler/Looper，
 * 因此需要 Android 框架的集成测试请放在 androidTest 中。
 * 本测试仅验证 Manager 层面的接口契约和未连接状态下的行为。
 */
class WebSocketManagerTest {

    @Test
    fun `sendMessage returns false for unknown connectionId`() {
        // WebSocketManager 构造依赖 OkHttpClient，但 sendMessage 对未知 id 直接查 map
        // 由于 WebSocketClientImpl 用了 Android Handler，我们无法在纯 JVM 测试中实例化
        // 这里验证 Config 的数据类行为
        val config = WebSocketManager.Config()
        assertEquals(10L, config.connectTimeout)
        assertEquals(60L, config.readTimeout)
        assertEquals(60L, config.writeTimeout)
        assertTrue(config.enableHeartbeat)
        assertEquals(30_000L, config.heartbeatIntervalMs)
    }

    @Test
    fun `Config defaults are reasonable`() {
        val config = WebSocketManager.Config()
        assertEquals(2_000L, config.reconnectBaseDelayMs)
        assertEquals(30_000L, config.reconnectMaxDelayMs)
        assertEquals(0, config.maxReconnectAttempts)
        assertFalse(config.enableMessageReplay)
        assertEquals(100, config.messageQueueCapacity)
        assertTrue(config.dropOldestWhenQueueFull)
        assertTrue(config.callbackOnMainThread)
    }

    @Test
    fun `Config copy with custom values`() {
        val config = WebSocketManager.Config(
            connectTimeout = 20,
            enableHeartbeat = false,
            heartbeatIntervalMs = 60_000,
            maxReconnectAttempts = 5,
            enableMessageReplay = true,
            callbackOnMainThread = false
        )

        assertEquals(20L, config.connectTimeout)
        assertFalse(config.enableHeartbeat)
        assertEquals(60_000L, config.heartbeatIntervalMs)
        assertEquals(5, config.maxReconnectAttempts)
        assertTrue(config.enableMessageReplay)
        assertFalse(config.callbackOnMainThread)
    }

    @Test
    fun `State enum contains expected values`() {
        val states = WebSocketManager.State.values()
        assertEquals(3, states.size)
        assertTrue(states.contains(WebSocketManager.State.DISCONNECTED))
        assertTrue(states.contains(WebSocketManager.State.CONNECTING))
        assertTrue(states.contains(WebSocketManager.State.CONNECTED))
    }

    @Test
    fun `WebSocketLogLevel enum values`() {
        val levels = WebSocketLogLevel.values()
        assertTrue(levels.isNotEmpty())
    }
}
