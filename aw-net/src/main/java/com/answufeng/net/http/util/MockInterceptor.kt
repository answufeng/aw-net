package com.answufeng.net.http.util

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock 拦截器，用于开发和测试阶段模拟 API 响应。
 *
 * ### 用法
 * ```kotlin
 * val mockInterceptor = MockInterceptor().apply {
 *     mock("/users", """{"code":0,"msg":"success","data":[{"id":1,"name":"Alice"}]}""")
 *     mock("/login", 401, """{"code":401,"msg":"unauthorized","data":null}""")
 *     mock("/delay", 200, """{"code":0,"msg":"ok"}""", delayMs = 1000)
 * }
 *
 * // 在 NetworkConfig 中注册
 * NetworkConfig.builder("https://api.example.com/")
 *     .cookieJar(...)
 *     .build()
 * // 或直接添加到 OkHttpClient
 * ```
 *
 * @param enable 是否启用 Mock，默认 true。生产环境应设为 false
 * @since 1.1.0
 */
class MockInterceptor(
    private val enable: Boolean = true
) : Interceptor {

    private val mocks = ConcurrentHashMap<String, MockEntry>()

    data class MockEntry(
        val code: Int,
        val body: String,
        val delayMs: Long = 0
    )

    /**
     * 注册一个 Mock 响应。匹配 URL 路径（不含 query 参数）。
     * @param path URL 路径，如 "/users"。支持精确匹配
     * @param code HTTP 状态码，默认 200
     * @param body 响应体 JSON 字符串
     * @param delayMs 模拟延迟毫秒数，默认 0
     * @since 1.1.0
     */
    fun mock(path: String, code: Int = 200, body: String, delayMs: Long = 0) {
        mocks[path] = MockEntry(code, body, delayMs)
    }

    /**
     * 移除指定路径的 Mock。
     * @since 1.1.0
     */
    fun removeMock(path: String) {
        mocks.remove(path)
    }

    /**
     * 清除所有 Mock。
     * @since 1.1.0
     */
    fun clearAll() {
        mocks.clear()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enable) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val path = request.url.encodedPath

        val entry = findMock(path)
        if (entry != null) {
            if (entry.delayMs > 0) {
                Thread.sleep(entry.delayMs)
            }
            return buildMockResponse(request, entry)
        }

        return chain.proceed(request)
    }

    private fun findMock(path: String): MockEntry? {
        mocks[path]?.let { return it }
        for ((key, entry) in mocks) {
            if (key.contains("*")) {
                val regex = key.replace("*", ".*").toRegex()
                if (regex.matches(path)) return entry
            }
        }
        return null
    }

    private fun buildMockResponse(request: Request, entry: MockEntry): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(entry.code)
            .message("OK")
            .body(entry.body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }
}
