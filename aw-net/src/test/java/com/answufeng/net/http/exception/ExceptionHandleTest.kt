package com.answufeng.net.http.exception

import com.answufeng.net.http.model.NetCode
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * ExceptionHandle 异常转换逻辑的单元测试。
 */
class ExceptionHandleTest {

    // ==================== 直通：已经是 BaseNetException ====================

    @Test
    fun `BaseNetException passes through unchanged`() {
        val original = RequestException(42, "already handled")
        val result = ExceptionHandle.handleException(original)
        assertSame(original, result)
    }

    // ==================== 网络层异常 ====================

    @Test
    fun `SocketTimeoutException maps to RequestException TIMEOUT`() {
        val result = ExceptionHandle.handleException(SocketTimeoutException("read timed out"))
        assertTrue(result is RequestException)
        assertEquals(NetCode.Tech.TIMEOUT, result.code)
    }

    @Test
    fun `ConnectException maps to RequestException NO_NETWORK`() {
        val result = ExceptionHandle.handleException(ConnectException("Connection refused"))
        assertTrue(result is RequestException)
        assertEquals(NetCode.Tech.NO_NETWORK, result.code)
    }

    @Test
    fun `UnknownHostException maps to RequestException NO_NETWORK`() {
        val result = ExceptionHandle.handleException(UnknownHostException("unknown host"))
        assertTrue(result is RequestException)
        assertEquals(NetCode.Tech.NO_NETWORK, result.code)
    }

    @Test
    fun `SSLException maps to RequestException SSL_ERROR`() {
        val result = ExceptionHandle.handleException(SSLException("handshake failed"))
        assertTrue(result is RequestException)
        assertEquals(NetCode.Tech.SSL_ERROR, result.code)
    }

    @Test
    fun `CancellationException maps to RequestException REQUEST_CANCELED`() {
        val result = ExceptionHandle.handleException(
            java.util.concurrent.CancellationException("job cancelled")
        )
        assertTrue(result is RequestException)
        assertEquals(NetCode.Tech.REQUEST_CANCELED, result.code)
    }

    // ==================== 解析层异常 ====================

    @Test
    fun `JsonParseException maps to ParseException`() {
        val result = ExceptionHandle.handleException(JsonParseException("bad json"))
        assertTrue(result is ParseException)
        assertEquals(NetCode.Tech.PARSE_ERROR, result.code)
    }

    @Test
    fun `JsonSyntaxException maps to ParseException`() {
        val result = ExceptionHandle.handleException(JsonSyntaxException("syntax error"))
        assertTrue(result is ParseException)
        assertEquals(NetCode.Tech.PARSE_ERROR, result.code)
    }

    @Test
    fun `JSONException maps to ParseException`() {
        val result = ExceptionHandle.handleException(JSONException("no value for key"))
        assertTrue(result is ParseException)
        assertEquals(NetCode.Tech.PARSE_ERROR, result.code)
    }

    @Test
    fun `ClassCastException maps to ParseException`() {
        val result = ExceptionHandle.handleException(ClassCastException("String cannot be cast to Int"))
        assertTrue(result is ParseException)
        assertEquals(NetCode.Tech.PARSE_ERROR, result.code)
    }

    // ==================== 未知异常 ====================

    @Test
    fun `Unknown exception maps to UnknownNetException`() {
        val result = ExceptionHandle.handleException(IllegalStateException("something went wrong"))
        assertTrue(result is UnknownNetException)
        assertEquals(NetCode.Tech.UNKNOWN, result.code)
    }

    @Test
    fun `cause is preserved for timeout`() {
        val cause = SocketTimeoutException("timeout")
        val result = ExceptionHandle.handleException(cause)
        assertSame(cause, result.cause)
    }

    @Test
    fun `cause is preserved for unknown`() {
        val cause = RuntimeException("surprise")
        val result = ExceptionHandle.handleException(cause)
        assertSame(cause, result.cause)
    }
}
