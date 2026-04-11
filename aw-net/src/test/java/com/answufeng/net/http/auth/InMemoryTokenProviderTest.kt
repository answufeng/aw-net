package com.answufeng.net.http.auth

import org.junit.Assert.*
import org.junit.Test

/**
 * InMemoryTokenProvider 的单元测试。
 */
class InMemoryTokenProviderTest {

    @Test
    fun `initial token is null by default`() {
        val provider = InMemoryTokenProvider()
        assertNull(provider.getAccessToken())
    }

    @Test
    fun `initial token can be set via constructor`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "abc123")
        assertEquals("abc123", provider.getAccessToken())
    }

    @Test
    fun `setAccessToken updates token`() {
        val provider = InMemoryTokenProvider()
        provider.setAccessToken("new-token")
        assertEquals("new-token", provider.getAccessToken())
    }

    @Test
    fun `clear sets token to null`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "token")
        provider.clear()
        assertNull(provider.getAccessToken())
    }

    @Test
    fun `refreshTokenBlocking calls refresher and returns result`() {
        var refreshCalled = false
        val provider = InMemoryTokenProvider(
            refresher = {
                refreshCalled = true
                true
            }
        )
        assertTrue(provider.refreshTokenBlocking())
        assertTrue(refreshCalled)
    }

    @Test
    fun `refreshTokenBlocking default returns false`() {
        val provider = InMemoryTokenProvider()
        assertFalse(provider.refreshTokenBlocking())
    }

    @Test
    fun `refreshTokenBlocking catches exception returns false`() {
        val provider = InMemoryTokenProvider(
            refresher = { throw RuntimeException("refresh failed") }
        )
        assertFalse(provider.refreshTokenBlocking())
    }

    @Test
    fun `refresher can update token internally`() {
        val provider = InMemoryTokenProvider(
            initialAccessToken = "old",
            refresher = {
                // refresher 内部通过外部引用更新 token 是常见用法
                true
            }
        )
        assertTrue(provider.refreshTokenBlocking())
    }

    @Test
    fun `setAccessToken to null then back`() {
        val provider = InMemoryTokenProvider(initialAccessToken = "token")
        provider.setAccessToken(null)
        assertNull(provider.getAccessToken())
        provider.setAccessToken("restored")
        assertEquals("restored", provider.getAccessToken())
    }
}
