package com.answufeng.net.http.util

import com.answufeng.net.http.model.ProgressInfo
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * 封装 ResponseBody 并在读取时发射进度。用于下载场景。
 * onProgress 的 [ProgressInfo.done] 在读取到 EOF (bytesRead == -1) 时为 true。
 */
class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val onProgress: (ProgressInfo) -> Unit
) : ResponseBody() {

    /**
     * 使用 lazy + SYNCHRONIZED 模式保证多线程下只创建一次 BufferedSource。
     * OkHttp 内部可能在不同线程调用 source()，此前的 null 检查缺少同步，
     * 并发场景下可能导致多次包装和计数器重置。
 */
    private val bufferedSource: BufferedSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        source(responseBody.source()).buffer()
    }

    override fun contentType(): MediaType? = responseBody.contentType()

    override fun contentLength(): Long = responseBody.contentLength()

    override fun source(): BufferedSource = bufferedSource

    private fun source(source: okio.Source): okio.Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = 0L
            var seqCounter = 0L

            override fun read(sink: okio.Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                seqCounter++
                val totalSize = responseBody.contentLength()
                val done = bytesRead == -1L
                // contentLength 未知时（-1），完成时报 100%，中间报 -1
                val progress = when {
                    totalSize > 0 -> (100 * totalBytesRead / totalSize).toInt()
                    done -> 100
                    else -> -1
                }
                onProgress(ProgressInfo(progress, totalBytesRead, totalSize, done, seqCounter))
                return bytesRead
            }
        }
    }
}
