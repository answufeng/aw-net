package com.answufeng.net.http.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

/**
 * GlobalResponseTypeAdapterFactory 反序列化能力测试。
 */
class GlobalResponseTypeAdapterFactoryTest {

    private fun buildGson(mapping: ResponseFieldMapping = ResponseFieldMapping()): Gson {
        return GsonBuilder()
            .registerTypeAdapterFactory(GlobalResponseTypeAdapterFactory { mapping })
            .create()
    }

    private inline fun <reified T> parseResponse(gson: Gson, json: String): GlobalResponse<T> {
        val type = TypeToken.getParameterized(GlobalResponse::class.java, T::class.java).type
        return gson.fromJson(json, type)
    }

    @Test
    fun `parses default field names`() {
        val gson = buildGson()
        val json = """{"code":0,"msg":"success","data":"hello"}"""
        val result = parseResponse<String>(gson, json)

        assertEquals(0, result.code)
        assertEquals("success", result.msg)
        assertEquals("hello", result.data)
    }

    @Test
    fun `parses custom field mapping`() {
        val mapping = ResponseFieldMapping(codeKey = "status", msgKey = "message", dataKey = "result")
        val gson = buildGson(mapping)
        val json = """{"status":200,"message":"ok","result":"value"}"""
        val result = parseResponse<String>(gson, json)

        assertEquals(200, result.code)
        assertEquals("ok", result.msg)
        assertEquals("value", result.data)
    }

    @Test
    fun `handles null data`() {
        val gson = buildGson()
        val json = """{"code":0,"msg":"ok","data":null}"""
        val result = parseResponse<String>(gson, json)

        assertEquals(0, result.code)
        assertNull(result.data)
    }

    @Test
    fun `handles missing data key`() {
        val gson = buildGson()
        val json = """{"code":0,"msg":"ok"}"""
        val result = parseResponse<String>(gson, json)

        assertEquals(0, result.code)
        assertNull(result.data)
    }

    @Test
    fun `parses nested object data`() {
        val gson = buildGson()
        val json = """{"code":0,"msg":"ok","data":{"name":"test","age":25}}"""

        data class User(val name: String, val age: Int)

        val type = TypeToken.getParameterized(GlobalResponse::class.java, User::class.java).type
        val result: GlobalResponse<User> = gson.fromJson(json, type)

        assertEquals(0, result.code)
        assertNotNull(result.data)
        assertEquals("test", result.data?.name)
        assertEquals(25, result.data?.age)
    }

    @Test
    fun `uses fallback code key`() {
        val mapping = ResponseFieldMapping(
            codeKey = "code",
            codeFallbackKeys = listOf("status", "errorCode")
        )
        val gson = buildGson(mapping)
        val json = """{"status":200,"msg":"ok","data":"val"}"""
        val result = parseResponse<String>(gson, json)

        assertEquals(200, result.code)
    }

    @Test
    fun `uses fallback msg key`() {
        val mapping = ResponseFieldMapping(
            msgKey = "msg",
            msgFallbackKeys = listOf("message", "errorMsg")
        )
        val gson = buildGson(mapping)
        val json = """{"code":0,"message":"fallback msg","data":"val"}"""
        val result = parseResponse<String>(gson, json)

        assertEquals("fallback msg", result.msg)
    }

    @Test
    fun `uses fallback data key`() {
        val mapping = ResponseFieldMapping(
            dataKey = "data",
            dataFallbackKeys = listOf("result", "body")
        )
        val gson = buildGson(mapping)
        val json = """{"code":0,"msg":"ok","result":"fallback data"}"""
        val result = parseResponse<String>(gson, json)

        assertEquals("fallback data", result.data)
    }

    @Test
    fun `serializes GlobalResponse correctly`() {
        val gson = buildGson()
        val response = GlobalResponse(code = 0, msg = "ok", data = "hello")
        val type = TypeToken.getParameterized(GlobalResponse::class.java, String::class.java).type
        val json = gson.toJson(response, type)

        assertTrue(json.contains("\"code\":0"))
        assertTrue(json.contains("\"msg\":\"ok\""))
        assertTrue(json.contains("\"data\":\"hello\""))
    }

    @Test
    fun `handles null input json`() {
        val gson = buildGson()
        val type = TypeToken.getParameterized(GlobalResponse::class.java, String::class.java).type
        val result: GlobalResponse<String> = gson.fromJson("null", type)

        assertNotNull(result)
        assertNull(result.data)
    }

    @Test
    fun `does not interfere with non-GlobalResponse types`() {
        val gson = buildGson()
        // 普通类型不应被 Factory 拦截
        val json = """{"name":"test"}"""
        data class Simple(val name: String)
        val result = gson.fromJson(json, Simple::class.java)
        assertEquals("test", result.name)
    }

    @Test
    fun `string code is resolved via codeValueConverter`() {
        val mapping = ResponseFieldMapping(
            codeValueConverter = { raw, _ ->
                when (raw) {
                    is String -> if (raw == "OK") 0 else -1
                    is Number -> raw.toInt()
                    else -> -1
                }
            }
        )
        val gson = buildGson(mapping)
        val json = """{"code":"OK","msg":"success","data":"val"}"""
        val result = parseResponse<String>(gson, json)

        assertEquals(0, result.code)
    }
}
