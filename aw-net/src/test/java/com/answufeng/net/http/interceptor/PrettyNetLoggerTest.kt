package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.INetLogger
import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkConfigProvider
import org.junit.Assert.*
import org.junit.Test

/**
 * PrettyNetLogger 日志格式化、脱敏、截断的单元测试。
 */
class PrettyNetLoggerTest {

    /** 简单的日志收集器 */
    private class CollectingLogger : INetLogger {
        val logs = mutableListOf<String>()
        override fun d(tag: String, msg: String) { logs.add(msg) }
        override fun e(tag: String, msg: String, throwable: Throwable?) { logs.add(msg) }
    }

    // ==================== JSON 格式化 ====================

    @Test
    fun `formats JSON object with indentation`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("""{"name":"test","value":42}""")
        // 应该被格式化为多行
        assertTrue(logger.logs.size > 1 || logger.logs.any { it.contains("\"name\"") })
    }

    @Test
    fun `formats JSON array with indentation`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("""[{"id":1},{"id":2}]""")
        assertTrue(logger.logs.any { it.contains("\"id\"") })
    }

    @Test
    fun `invalid JSON passes through as plain text`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("{invalid json")
        assertEquals(1, logger.logs.size)
        assertTrue(logger.logs[0].contains("{invalid json"))
    }

    @Test
    fun `non-JSON message passes through`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("GET https://api.example.com/user")
        assertEquals(1, logger.logs.size)
        assertEquals("GET https://api.example.com/user", logger.logs[0])
    }

    // ==================== 敏感 Header 脱敏 ====================

    @Test
    fun `masks Authorization header`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("Authorization: Bearer my-secret-token-123")
        assertEquals(1, logger.logs.size)
        assertEquals("Authorization: ****(masked)", logger.logs[0])
    }

    @Test
    fun `masks Cookie header case insensitive`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("cookie: session=abc123")
        assertEquals("cookie: ****(masked)", logger.logs[0])
    }

    @Test
    fun `does not mask non-sensitive header`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("Content-Type: application/json")
        assertEquals("Content-Type: application/json", logger.logs[0])
    }

    @Test
    fun `masks custom sensitive header from config`() {
        val config = NetworkConfig(
            baseUrl = "https://api.example.com/",
            sensitiveHeaders = NetworkConfig.DEFAULT_SENSITIVE_HEADERS + setOf("x-custom-secret")
        )
        val configProvider = NetworkConfigProvider(config)
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger, configProvider)
        prettyLogger.log("X-Custom-Secret: super-secret-value")
        assertEquals("X-Custom-Secret: ****(masked)", logger.logs[0])
    }

    @Test
    fun `default sensitive headers include common auth headers`() {
        val defaults = NetworkConfig.DEFAULT_SENSITIVE_HEADERS
        assertTrue(defaults.contains("authorization"))
        assertTrue(defaults.contains("cookie"))
        assertTrue(defaults.contains("set-cookie"))
        assertTrue(defaults.contains("x-api-key"))
    }

    // ==================== 截断 ====================

    @Test
    fun `truncates message exceeding max length`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        val longMessage = "A".repeat(5000)
        prettyLogger.log(longMessage)
        assertTrue(logger.logs[0].endsWith("... (truncated)"))
        assertTrue(logger.logs[0].length < longMessage.length)
    }

    @Test
    fun `does not truncate message within limit`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        val message = "A".repeat(100)
        prettyLogger.log(message)
        assertEquals(message, logger.logs[0])
    }

    // ==================== Body 字段脱敏 ====================

    @Test
    fun `masks password field in JSON body`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("""{"username":"admin","password":"123456"}""")
        val output = logger.logs.joinToString("\n")
        assertTrue("password should be masked", output.contains("****(masked)"))
        assertFalse("password value should not appear", output.contains("123456"))
    }

    @Test
    fun `masks nested sensitive field`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("""{"user":{"name":"test","creditCard":"4111-1111-1111-1111"}}""")
        val output = logger.logs.joinToString("\n")
        assertTrue(output.contains("****(masked)"))
        assertFalse(output.contains("4111"))
    }

    @Test
    fun `masks sensitive fields in JSON array`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("""[{"password":"secret1"},{"password":"secret2"}]""")
        val output = logger.logs.joinToString("\n")
        assertFalse(output.contains("secret1"))
        assertFalse(output.contains("secret2"))
    }

    @Test
    fun `custom sensitive body fields from config`() {
        val config = NetworkConfig(
            baseUrl = "https://api.example.com/",
            sensitiveBodyFields = setOf("my_secret_field")
        )
        val configProvider = NetworkConfigProvider(config)
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger, configProvider)
        prettyLogger.log("""{"my_secret_field":"top_secret","public_field":"visible"}""")
        val output = logger.logs.joinToString("\n")
        assertFalse(output.contains("top_secret"))
        assertTrue(output.contains("visible"))
    }

    @Test
    fun `non-sensitive fields remain unmasked`() {
        val logger = CollectingLogger()
        val prettyLogger = PrettyNetLogger(logger)
        prettyLogger.log("""{"name":"test","email":"test@example.com"}""")
        val output = logger.logs.joinToString("\n")
        assertTrue(output.contains("test"))
        assertTrue(output.contains("test@example.com"))
        assertFalse(output.contains("masked"))
    }
}
