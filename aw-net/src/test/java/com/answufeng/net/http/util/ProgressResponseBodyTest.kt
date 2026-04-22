package com.answufeng.net.http.util

import com.answufeng.net.http.model.ProgressInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import okio.buffer
import okio.source

/**
 * ProgressResponseBody 进度发射和线程安全的单元测试。
 */
class ProgressResponseBodyTest {

    @Test
    fun `emits progress on read`() {
        val body = "Hello, World!".toByteArray()
        val responseBody = body.toResponseBody("text/plain".toMediaType())
        val events = mutableListOf<ProgressInfo>()

        val progressBody = ProgressResponseBody(responseBody) { events.add(it) }
        val source = progressBody.source()
        source.readByteArray() // 读完所有内容
        source.close()

        assertTrue(events.isNotEmpty())
        // 最后一个事件应标 isDone
        assertTrue(events.last().isDone)
        assertEquals(body.size.toLong(), events.last().currentSize)
    }

    @Test
    fun `progress reaches 100 for known content length`() {
        val body = "A".repeat(1000).toByteArray()
        val responseBody = body.toResponseBody("text/plain".toMediaType())
        val events = mutableListOf<ProgressInfo>()

        val progressBody = ProgressResponseBody(responseBody) { events.add(it) }
        progressBody.source().readByteArray()

        val lastProgress = events.last { it.isDone }
        assertEquals(100, lastProgress.progress)
        assertEquals(body.size.toLong(), lastProgress.totalSize)
    }

    @Test
    fun `seq counter is strictly increasing`() {
        val body = "A".repeat(5000).toByteArray()
        val responseBody = body.toResponseBody("text/plain".toMediaType())
        val events = mutableListOf<ProgressInfo>()

        val progressBody = ProgressResponseBody(responseBody) { events.add(it) }
        progressBody.source().readByteArray()

        for (i in 1 until events.size) {
            assertTrue(events[i].seq > events[i - 1].seq)
        }
    }

    @Test
    fun `source called multiple times returns same instance`() {
        val responseBody = "data".toResponseBody("text/plain".toMediaType())
        val progressBody = ProgressResponseBody(responseBody) {}

        val source1 = progressBody.source()
        val source2 = progressBody.source()
        assertSame(source1, source2)
    }

    @Test
    fun `close delegates to underlying response body`() {
        var closeCount = 0
        val inner = object : ResponseBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun contentLength() = 4L
            override fun source() = ByteArrayInputStream("test".toByteArray()).source().buffer()
            override fun close() {
                closeCount++
                super.close()
            }
        }
        val progressBody = ProgressResponseBody(inner) {}
        progressBody.close()
        assertTrue("underlying close() should be invoked at least once", closeCount >= 1)
    }

    @Test
    fun `content type and content length delegated`() {
        val mediaType = "application/json".toMediaType()
        val body = """{"key":"value"}""".toByteArray()
        val responseBody = body.toResponseBody(mediaType)
        val progressBody = ProgressResponseBody(responseBody) {}

        assertEquals(mediaType, progressBody.contentType())
        assertEquals(body.size.toLong(), progressBody.contentLength())
    }
}
