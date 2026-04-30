package com.answufeng.net.http.util

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap

class MockInterceptor(
    private val enable: Boolean = true
) : Interceptor {

    private val mocks = ConcurrentHashMap<String, MockEntry>()
    private val regexMocks = ConcurrentHashMap<String, MockEntry>()

    data class MockEntry(
        val code: Int,
        val body: String,
        val delayMs: Long = 0,
        val headers: Map<String, String> = emptyMap()
    )

    fun mock(path: String, code: Int = 200, body: String, delayMs: Long = 0, headers: Map<String, String> = emptyMap()) {
        mocks[path] = MockEntry(code, body, delayMs, headers)
    }

    fun mockRegex(pattern: String, code: Int = 200, body: String, delayMs: Long = 0, headers: Map<String, String> = emptyMap()) {
        regexMocks[pattern] = MockEntry(code, body, delayMs, headers)
    }

    fun removeMock(path: String) {
        mocks.remove(path)
    }

    fun removeRegexMock(pattern: String) {
        regexMocks.remove(pattern)
    }

    fun clearAll() {
        mocks.clear()
        regexMocks.clear()
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
                val regex = Regex(Regex.escape(key).replace("\\*", ".*"))
                if (regex.matches(path)) return entry
            }
        }

        for ((pattern, entry) in regexMocks) {
            try {
                if (Regex(pattern).matches(path)) return entry
            } catch (_: Exception) {
                continue
            }
        }

        return null
    }

    private fun buildMockResponse(request: Request, entry: MockEntry): Response {
        val headersBuilder = Headers.Builder()
        headersBuilder.add("Content-Type", "application/json; charset=utf-8")
        for ((name, value) in entry.headers) {
            headersBuilder.add(name, value)
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(entry.code)
            .message("OK")
            .headers(headersBuilder.build())
            .body(entry.body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }
}
