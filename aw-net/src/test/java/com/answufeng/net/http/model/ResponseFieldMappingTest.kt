package com.answufeng.net.http.model

import org.junit.Assert.*
import org.junit.Test

/**
 * ResponseFieldMapping.resolveCode() 字段映射逻辑的单元测试。
 */
class ResponseFieldMappingTest {

    private val mapping = ResponseFieldMapping(
        successCode = 0,
        failureCode = -1
    )

    // ==================== Number 类型 ====================

    @Test
    fun `resolveCode with Int`() {
        assertEquals(200, mapping.resolveCode(200))
    }

    @Test
    fun `resolveCode with Long`() {
        assertEquals(42, mapping.resolveCode(42L))
    }

    @Test
    fun `resolveCode with Double`() {
        assertEquals(1, mapping.resolveCode(1.9))
    }

    @Test
    fun `resolveCode with zero`() {
        assertEquals(0, mapping.resolveCode(0))
    }

    // ==================== Boolean 类型 ====================

    @Test
    fun `resolveCode true maps to successCode`() {
        assertEquals(0, mapping.resolveCode(true))
    }

    @Test
    fun `resolveCode false maps to failureCode`() {
        assertEquals(-1, mapping.resolveCode(false))
    }

    // ==================== String 类型 ====================

    @Test
    fun `resolveCode numeric string`() {
        assertEquals(200, mapping.resolveCode("200"))
    }

    @Test
    fun `resolveCode string true maps to successCode`() {
        assertEquals(0, mapping.resolveCode("true"))
    }

    @Test
    fun `resolveCode string TRUE maps to successCode`() {
        assertEquals(0, mapping.resolveCode("TRUE"))
    }

    @Test
    fun `resolveCode string false maps to failureCode`() {
        assertEquals(-1, mapping.resolveCode("false"))
    }

    @Test
    fun `resolveCode string FALSE maps to failureCode`() {
        assertEquals(-1, mapping.resolveCode("FALSE"))
    }

    @Test
    fun `resolveCode non-numeric string maps to failureCode`() {
        assertEquals(-1, mapping.resolveCode("unknown"))
    }

    // ==================== Null / Other ====================

    @Test
    fun `resolveCode null maps to failureCode`() {
        assertEquals(-1, mapping.resolveCode(null))
    }

    @Test
    fun `resolveCode unsupported type maps to failureCode`() {
        assertEquals(-1, mapping.resolveCode(listOf(1, 2, 3)))
    }

    // ==================== 自定义 converter ====================

    @Test
    fun `custom codeValueConverter overrides default`() {
        val custom = mapping.copy(
            codeValueConverter = { rawCode, _ -> if (rawCode == "ok") 0 else -1 }
        )
        assertEquals(0, custom.resolveCode("ok"))
        assertEquals(-1, custom.resolveCode("error"))
    }

    // ==================== 自定义 successCode / failureCode ====================

    @Test
    fun `custom successCode and failureCode`() {
        val custom = ResponseFieldMapping(successCode = 1, failureCode = 0)
        assertEquals(1, custom.resolveCode(true))
        assertEquals(0, custom.resolveCode(false))
        assertEquals(0, custom.resolveCode(null))
    }

    // ==================== 数据类默认值 ====================

    @Test
    fun `default field keys`() {
        val default = ResponseFieldMapping()
        assertEquals("code", default.codeKey)
        assertEquals("msg", default.msgKey)
        assertEquals("data", default.dataKey)
        assertTrue(default.codeFallbackKeys.isEmpty())
        assertTrue(default.msgFallbackKeys.isEmpty())
        assertTrue(default.dataFallbackKeys.isEmpty())
        assertEquals(0, default.successCode)
        assertEquals(-1, default.failureCode)
        assertEquals("", default.defaultMsg)
        assertNull(default.codeValueConverter)
    }
}
