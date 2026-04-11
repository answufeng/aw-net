package com.answufeng.net.http.util

import com.answufeng.net.http.model.ProgressInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/**
 * ProgressRequestBody 上传进度发射的单元测试。
 */
class ProgressRequestBodyTest {

    @Test
    fun `emits progress events during write`() {
        val data = "A".repeat(1000).toByteArray()
        val delegate = data.toRequestBody("text/plain".toMediaType())
        val events = mutableListOf<ProgressInfo>()

        val progressBody = ProgressRequestBody(delegate) { events.add(it) }
        val sink = Buffer()
        progressBody.writeTo(sink)

        assertTrue(events.isNotEmpty())
        // 最后一个事件应标 isDone
        assertTrue(events.last().isDone)
    }

    @Test
    fun `progress reaches 100 for known content length`() {
        val data = "B".repeat(500).toByteArray()
        val delegate = data.toRequestBody("text/plain".toMediaType())
        val events = mutableListOf<ProgressInfo>()

        val progressBody = ProgressRequestBody(delegate) { events.add(it) }
        progressBody.writeTo(Buffer())

        val doneEvent = events.last { it.isDone }
        assertEquals(100, doneEvent.progress)
    }

    @Test
    fun `delegates content type and content length`() {
        val mediaType = "application/octet-stream".toMediaType()
        val data = ByteArray(256)
        val delegate = data.toRequestBody(mediaType)
        val progressBody = ProgressRequestBody(delegate) {}

        assertEquals(mediaType, progressBody.contentType())
        assertEquals(256L, progressBody.contentLength())
    }

    @Test
    fun `seq counter increases monotonically`() {
        val data = "C".repeat(2000).toByteArray()
        val delegate = data.toRequestBody("text/plain".toMediaType())
        val events = mutableListOf<ProgressInfo>()

        val progressBody = ProgressRequestBody(delegate) { events.add(it) }
        progressBody.writeTo(Buffer())

        for (i in 1 until events.size) {
            assertTrue("seq should increase", events[i].seq > events[i - 1].seq)
        }
    }

    @Test
    fun `ensures final done callback even when unknown content length`() {
        // 对于 contentLength() == -1 的场景，应在最后有 isDone=true
        val delegate = object : RequestBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun contentLength() = -1L
            override fun writeTo(sink: okio.BufferedSink) {
                sink.writeUtf8("hello world")
            }
        }
        val events = mutableListOf<ProgressInfo>()
        val progressBody = ProgressRequestBody(delegate) { events.add(it) }
        progressBody.writeTo(Buffer())

        assertTrue(events.any { it.isDone })
    }
}
