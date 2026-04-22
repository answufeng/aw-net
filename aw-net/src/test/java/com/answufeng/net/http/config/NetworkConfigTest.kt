package com.answufeng.net.http.config

import org.junit.Assert.*
import org.junit.Test

/**
 * NetworkConfig 初始化参数校验的单元测试。
 */
class NetworkConfigTest {

    // ==================== baseUrl 校验 ====================

    @Test
    fun `valid https baseUrl accepted`() {
        val config = NetworkConfig(baseUrl = "https://api.example.com/")
        assertEquals("https://api.example.com/", config.baseUrl)
    }

    @Test
    fun `valid http baseUrl accepted`() {
        val config = NetworkConfig(baseUrl = "http://api.example.com/")
        assertEquals("http://api.example.com/", config.baseUrl)
    }

    @Test
    fun `path prefix in baseUrl accepted`() {
        val config = NetworkConfig(baseUrl = "https://api.example.com/v1/")
        assertTrue(config.baseUrl.contains("/v1/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `baseUrl with query string rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/?a=b")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `baseUrl with fragment rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com#frag")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `baseUrl without trailing slash rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `baseUrl without protocol rejected`() {
        NetworkConfig(baseUrl = "api.example.com/")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank baseUrl rejected`() {
        NetworkConfig(baseUrl = "  ")
    }

    // ==================== timeout 校验 ====================

    @Test(expected = IllegalArgumentException::class)
    fun `connectTimeout zero rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/", connectTimeout = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `readTimeout negative rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/", readTimeout = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `writeTimeout zero rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/", writeTimeout = 0)
    }

    @Test
    fun `all timeouts at minimum 1 second accepted`() {
        val config = NetworkConfig(
            baseUrl = "https://api.example.com/",
            connectTimeout = 1,
            readTimeout = 1,
            writeTimeout = 1
        )
        assertEquals(1L, config.connectTimeout)
    }

    // ==================== connectionPool 校验 ====================

    @Test(expected = IllegalArgumentException::class)
    fun `maxIdleConnections zero rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/", maxIdleConnections = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `keepAliveDurationSeconds zero rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/", keepAliveDurationSeconds = 0)
    }

    // ==================== retry 校验 ====================

    @Test(expected = IllegalArgumentException::class)
    fun `retryMaxAttempts negative rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/", retryMaxAttempts = -1)
    }

    @Test
    fun `retryMaxAttempts zero accepted — means no retry`() {
        val config = NetworkConfig(baseUrl = "https://api.example.com/", retryMaxAttempts = 0)
        assertEquals(0, config.retryMaxAttempts)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `retryInitialBackoffMs negative rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/", retryInitialBackoffMs = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `retryInitialBackoffMs zero rejected`() {
        NetworkConfig(baseUrl = "https://api.example.com/", retryInitialBackoffMs = 0)
    }

    // ==================== extraHeaders 校验 ====================

    @Test(expected = IllegalArgumentException::class)
    fun `extraHeaders with invalid name rejected`() {
        NetworkConfig(
            baseUrl = "https://api.example.com/",
            extraHeaders = mapOf("X Bad" to "1")
        )
    }

    // ==================== certificatePins 校验 ====================

    @Test
    fun `empty certificatePins accepted`() {
        val config = NetworkConfig(baseUrl = "https://api.example.com/", certificatePins = emptyList())
        assertTrue(config.certificatePins.isEmpty())
    }

    @Test
    fun `valid certificatePin accepted`() {
        val pin = CertificatePin(
            pattern = "api.example.com",
            pins = listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        )
        val config = NetworkConfig(baseUrl = "https://api.example.com/", certificatePins = listOf(pin))
        assertEquals(1, config.certificatePins.size)
    }

    // ==================== CertificatePin 校验 ====================

    @Test(expected = IllegalArgumentException::class)
    fun `CertificatePin blank pattern rejected`() {
        CertificatePin(pattern = " ", pins = listOf("sha256/abc"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `CertificatePin empty pins rejected`() {
        CertificatePin(pattern = "example.com", pins = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `CertificatePin invalid pin prefix rejected`() {
        CertificatePin(pattern = "example.com", pins = listOf("md5/invalid"))
    }

    @Test
    fun `CertificatePin sha1 prefix accepted`() {
        val pin = CertificatePin(pattern = "example.com", pins = listOf("sha1/abc123"))
        assertEquals("sha1/abc123", pin.pins[0])
    }

    // ==================== 默认值 ====================

    @Test
    fun `default config has sensible defaults`() {
        val config = NetworkConfig(baseUrl = "https://api.example.com/")
        assertEquals(15L, config.connectTimeout)
        assertEquals(15L, config.readTimeout)
        assertEquals(15L, config.writeTimeout)
        assertEquals(0, config.defaultSuccessCode)
        assertEquals(NetworkLogLevel.NONE, config.networkLogLevel)
        assertEquals(5, config.maxIdleConnections)
        assertEquals(300, config.keepAliveDurationSeconds)
        assertTrue(config.sensitiveHeaders.contains("authorization"))
        assertTrue(config.sensitiveBodyFields.contains("password"))
        assertTrue(config.sensitiveBodyFields.contains("creditCard"))
        assertTrue(config.enableRequestTracking)
    }

    @Test
    fun `enableRequestTracking can be disabled`() {
        val config = NetworkConfig(
            baseUrl = "https://api.example.com/",
            enableRequestTracking = false
        )
        assertFalse(config.enableRequestTracking)
    }

    @Test
    fun `custom sensitiveBodyFields are accepted`() {
        val config = NetworkConfig(
            baseUrl = "https://api.example.com/",
            sensitiveBodyFields = setOf("my_field")
        )
        assertEquals(setOf("my_field"), config.sensitiveBodyFields)
    }
}
