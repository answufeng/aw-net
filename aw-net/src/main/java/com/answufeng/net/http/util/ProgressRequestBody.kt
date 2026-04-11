package com.answufeng.net.http.util

import com.answufeng.net.http.model.ProgressInfo
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/**
 * 封装 RequestBody 并提供进度回调的实现。用于上传时监控已写入字节数。
 * @param delegate 实际的 RequestBody（例如 file.asRequestBody）
 * @param onProgress 每次写入后会回调最新的 [ProgressInfo]
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (ProgressInfo) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        var bytesWritten = 0L
        val totalBytes = contentLength()
        var seqCounter = 0L

        var doneEmitted = false
        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                seqCounter++
                val progress = if (totalBytes > 0) (100 * bytesWritten / totalBytes).toInt() else -1
                val done = totalBytes > 0 && bytesWritten >= totalBytes
                if (done) doneEmitted = true
                onProgress(ProgressInfo(progress, bytesWritten, totalBytes, done, seqCounter))
            }
        }
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()

        // 确保发送最终完成回调（contentLength 未知或最后一次 write 未精确对齐时）
        if (!doneEmitted) {
            seqCounter++
            val finalProgress = if (totalBytes > 0) (100 * bytesWritten / totalBytes).toInt() else 100
            onProgress(ProgressInfo(finalProgress, bytesWritten, totalBytes, true, seqCounter))
        }
    }
}
