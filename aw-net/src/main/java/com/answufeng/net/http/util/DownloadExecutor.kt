package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ProgressInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 负责文件下载的执行器。职责：
 * - 从 Retrofit 返回的 [ResponseBody] 将内容写入磁盘文件（支持分段写入，避免 OOM）
 * - 通过 [ProgressResponseBody] 发射进度事件（若传入 progressFlow）
 * - 可选的下载校验（expectedHash）和校验失败策略
 * @since 1.0.0
 */
@Singleton
class DownloadExecutor @Inject constructor(
    private val configProvider: NetworkConfigProvider
) {

    companion object {
        /**
         * 下载缓冲区大小（8KB），平衡内存占用与 IO 效率。
         * @since 1.0.0
$         */
        private const val DOWNLOAD_BUFFER_SIZE = 8192L
    }

    /**
     * 下载文件并返回结果（成功返回 File，失败返回 TechnicalFailure）。
     * 详见 `NetworkExecutor.downloadFile` 的文档（此处为实现）。
     * @param targetFile 目标文件路径
     * @param progressFlow 可选的进度回调 Flow
     * @param expectedHash 可选的期望哈希值，用于下载后校验
     * @param hashAlgorithm 哈希算法，默认 SHA-256
     * @param hashStrategy 哈希校验失败时的策略
     * @param successCode 业务成功码，null 时使用全局配置
     * @param dispatcher 协程调度器
     * @param tag 监控标签
     * @param call 返回 ResponseBody 的 Retrofit suspend 接口方法
     * @since 1.0.0
 */
    suspend fun downloadFile(
        targetFile: File,
        progressFlow: MutableSharedFlow<ProgressInfo>? = null,
        expectedHash: String? = null,
        hashAlgorithm: String = "SHA-256",
        hashStrategy: HashVerificationStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH,
        @Suppress("UNUSED_PARAMETER")
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> {
        val start = System.currentTimeMillis()
        NetTracker.track(
            NetEvent(
                name = "downloadFile",
                stage = NetEventStage.START,
                timestampMs = start,
                tag = tag
            )
        )

        val result = withContext(dispatcher) {
            runDownloadFlow(targetFile, progressFlow, expectedHash, hashAlgorithm, hashStrategy, call)
        }

        val end = System.currentTimeMillis()
        val duration = end - start
        val (type, errorCode) = when (result) {
            is NetworkResult.Success -> "SUCCESS" to null
            is NetworkResult.TechnicalFailure -> "TECHNICAL_FAILURE" to result.exception.code
            is NetworkResult.BusinessFailure -> "BUSINESS_FAILURE" to result.code
        }
        NetTracker.track(
            NetEvent(
                name = "downloadFile",
                stage = NetEventStage.END,
                timestampMs = end,
                durationMs = duration,
                resultType = type,
                errorCode = errorCode,
                tag = tag
            )
        )
        return result
    }

    private suspend fun runDownloadFlow(
        targetFile: File,
        progressFlow: MutableSharedFlow<ProgressInfo>?,
        expectedHash: String?,
        hashAlgorithm: String,
        hashStrategy: HashVerificationStrategy,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> {
        return try {
            val body = call()
            val progressBody = ProgressResponseBody(body) { info -> progressFlow?.tryEmit(info) }
            val source = progressBody.source()
            val md = if (expectedHash != null) MessageDigest.getInstance(hashAlgorithm) else null

            source.use { src ->
                targetFile.parentFile?.mkdirs()
                targetFile.sink().buffer().use { sink ->
                    val buffer = okio.Buffer()
                    var totalRead = 0L
                    var readCount: Long
                    while (src.read(buffer, DOWNLOAD_BUFFER_SIZE).also { readCount = it } != -1L) {
                        currentCoroutineContext().ensureActive()
                        if (md != null) {
                            val hashBuf = okio.Buffer()
                            buffer.copyTo(hashBuf, 0, readCount)
                            md.update(hashBuf.readByteArray())
                        }
                        sink.write(buffer, readCount)
                        totalRead += readCount
                    }
                    sink.flush()
                }
            }

            if (expectedHash != null && md != null) {
                val digest = md.digest().joinToString("") { "%02x".format(it) }
                if (!digest.equals(expectedHash, ignoreCase = true)) {
                    if (hashStrategy == HashVerificationStrategy.DELETE_ON_MISMATCH) {
                        try { if (targetFile.exists()) targetFile.delete() } catch (_: SecurityException) {}
                    }
                    return NetworkResult.TechnicalFailure(
                        ExceptionHandle.handleException(
                            IllegalStateException("download hash mismatch: expected=$expectedHash, actual=$digest")
                        )
                    )
                }
            }

            NetworkResult.Success(targetFile)
        } catch (e: CancellationException) {
            try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
            NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
        }
    }
}
