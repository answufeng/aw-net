package com.answufeng.net.http.util

import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ProgressInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.CancellationException

/**
 * 负责文件下载的执行器。职责：
 * - 从 Retrofit 返回的 [ResponseBody] 将内容写入磁盘文件（支持分段写入，避免 OOM）
 * - 通过 [ProgressResponseBody] 发射进度事件（若传入 progressFlow）
 * - 可选的下载校验（expectedHash）和校验失败策略
 */
@Singleton
class DownloadExecutor @Inject constructor() {

    /**
     * 下载文件并返回结果（成功返回 File，失败返回 TechnicalFailure）。
     * 详见 `NetworkExecutor.downloadFile` 的文档（此处为实现）。
     */
    suspend fun downloadFile(
        targetFile: File,
        progressFlow: MutableSharedFlow<ProgressInfo>? = null,
        expectedHash: String? = null,
        hashAlgorithm: String = "SHA-256",
        hashStrategy: HashVerificationStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH,
        cancelJob: Job? = null,
        lifecycleScope: CoroutineScope? = null,
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

        val result = when {
            lifecycleScope != null -> {
                // run within provided lifecycleScope so cancelling that scope cancels the child
                lifecycleScope.async(Dispatchers.IO) {
                    runDownloadFlow(targetFile, progressFlow, expectedHash, hashAlgorithm, hashStrategy, cancelJob, call)
                }.await()
            }
            else -> {
                withContext(Dispatchers.IO) {
                    runDownloadFlow(targetFile, progressFlow, expectedHash, hashAlgorithm, hashStrategy, cancelJob, call)
                }
            }
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
        cancelJob: Job?,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> {
        return try {
            val body = call()
            // Always wrap the response with ProgressResponseBody to keep progress calculation in a single place.
            val progressBody = ProgressResponseBody(body) { info -> progressFlow?.tryEmit(info) }
            val source = progressBody.source()

            // 边写边计算摘要，避免写完后二次读取整个文件导致 I/O 翻倍
            val md = if (expectedHash != null) MessageDigest.getInstance(hashAlgorithm) else null

            source.use { src ->
                targetFile.parentFile?.mkdirs()
                targetFile.sink().buffer().use { sink ->
                    val buffer = okio.Buffer()
                    var totalRead = 0L
                    var readCount: Long
                    while (src.read(buffer, 8192).also { readCount = it } != -1L) {
                        // check external cancel Job
                        if (cancelJob != null && !cancelJob.isActive) {
                            throw CancellationException("download cancelled")
                        }
                        // 将数据写入磁盘的同时同步更新摘要
                        val bytes = buffer.readByteArray(readCount)
                        md?.update(bytes)
                        sink.write(bytes)
                        totalRead += readCount
                    }
                    sink.flush()
                }
            }

            if (expectedHash != null && md != null) {
                val digest = md.digest().joinToString("") { "%02x".format(it) }
                if (!digest.equals(expectedHash, ignoreCase = true)) {
                    if (hashStrategy == HashVerificationStrategy.DELETE_ON_MISMATCH) {
                        try {
                            if (targetFile.exists()) targetFile.delete()
                        } catch (_: SecurityException) {
                            // 权限不足时忽略删除失败，下方仍会返回 hash 不匹配错误
                        }
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
            // 必须重新抛出 CancellationException 以保证协程取消正常传播
            throw e
        } catch (e: Exception) {
            NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
        }
    }
}
