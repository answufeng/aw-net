package com.answufeng.net.http.util

import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.exception.ExceptionHandle
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
import okio.appendingSink
import okio.buffer
import okio.sink
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
/**
 * 封装文件下载能力，提供普通下载与断点续传下载。
 *
 * 支持进度回调、摘要校验与失败后文件处理策略。
 */
class DownloadExecutor @Inject constructor(
    private val configProvider: NetworkConfigProvider
) {

    companion object {
        private const val DOWNLOAD_BUFFER_SIZE = 8192L
    }

    suspend fun downloadFile(
        targetFile: File,
        progressFlow: MutableSharedFlow<ProgressInfo>? = null,
        expectedHash: String? = null,
        hashAlgorithm: String = "SHA-256",
        hashStrategy: HashVerificationStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> = trackAndExecute("downloadFile", tag) {
        withContext(dispatcher) {
            runDownloadFlow(targetFile, progressFlow, expectedHash, hashAlgorithm, hashStrategy, call)
        }
    }

    /**
     * 断点续传下载。如果目标文件已存在且 [resumeFromExisting] 为 true，
     * 会从文件末尾继续下载（使用 Range 请求头）。
     *
     * 调用方需要在 [call] 中添加 `Range` 请求头，例如：
     * ```kotlin
     * executor.downloadFileResumable(
     *     targetFile = file,
     *     existingFileSize = file.length(),
     *     call = {
     *         // Retrofit 接口需支持 @Header("Range") 参数
     *         api.downloadFile("bytes=${file.length()}-")
     *     }
     * )
     * ```
     *
     * @param targetFile 目标保存文件
     * @param existingFileSize 已有文件大小（通常为 targetFile.length()），0 表示从头下载
     * @param progressFlow 进度流
     * @param expectedHash 预期文件摘要（完整文件的摘要，非部分）
     * @param hashAlgorithm 摘要算法
     * @param hashStrategy 校验失败策略
     * @param dispatcher 协程调度器
     * @param tag 监控标签
     * @param call 返回 ResponseBody 的 suspend 方法（需自行添加 Range 头）
     */
    suspend fun downloadFileResumable(
        targetFile: File,
        existingFileSize: Long = 0,
        progressFlow: MutableSharedFlow<ProgressInfo>? = null,
        expectedHash: String? = null,
        hashAlgorithm: String = "SHA-256",
        hashStrategy: HashVerificationStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> = trackAndExecute("downloadFileResumable", tag) {
        withContext(dispatcher) {
            runResumableDownloadFlow(targetFile, existingFileSize, progressFlow, expectedHash, hashAlgorithm, hashStrategy, call)
        }
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
                    val reuseBuf = if (md != null) ByteArray(DOWNLOAD_BUFFER_SIZE.toInt()) else null
                    var totalRead = 0L
                    var readCount: Long
                    while (src.read(buffer, DOWNLOAD_BUFFER_SIZE).also { readCount = it } != -1L) {
                        currentCoroutineContext().ensureActive()
                        if (md != null && reuseBuf != null) {
                            buffer.read(reuseBuf, 0, readCount.toInt())
                            md.update(reuseBuf, 0, readCount.toInt())
                            sink.write(reuseBuf, 0, readCount.toInt())
                        } else {
                            sink.write(buffer, readCount)
                        }
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

    private suspend fun runResumableDownloadFlow(
        targetFile: File,
        existingFileSize: Long,
        progressFlow: MutableSharedFlow<ProgressInfo>?,
        expectedHash: String?,
        hashAlgorithm: String,
        hashStrategy: HashVerificationStrategy,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> {
        return try {
            val body = call()
            val progressBody = ProgressResponseBody(body) { info ->
                val adjusted = info.copy(totalSize = info.totalSize + existingFileSize)
                progressFlow?.tryEmit(adjusted)
            }
            val source = progressBody.source()
            val md = if (expectedHash != null) MessageDigest.getInstance(hashAlgorithm) else null

            if (existingFileSize > 0 && md != null) {
                targetFile.inputStream().buffered().use { fis ->
                    val buf = ByteArray(DOWNLOAD_BUFFER_SIZE.toInt())
                    var len: Int
                    while (fis.read(buf).also { len = it } != -1) {
                        md.update(buf, 0, len)
                    }
                }
            }

            source.use { src ->
                targetFile.parentFile?.mkdirs()
                val sink = if (existingFileSize > 0 && targetFile.exists()) {
                    targetFile.appendingSink().buffer()
                } else {
                    targetFile.sink().buffer()
                }
                sink.use { out ->
                    val buffer = okio.Buffer()
                    val reuseBuf = if (md != null) ByteArray(DOWNLOAD_BUFFER_SIZE.toInt()) else null
                    var readCount: Long
                    while (src.read(buffer, DOWNLOAD_BUFFER_SIZE).also { readCount = it } != -1L) {
                        currentCoroutineContext().ensureActive()
                        if (md != null && reuseBuf != null) {
                            buffer.read(reuseBuf, 0, readCount.toInt())
                            md.update(reuseBuf, 0, readCount.toInt())
                            out.write(reuseBuf, 0, readCount.toInt())
                        } else {
                            out.write(buffer, readCount)
                        }
                    }
                    out.flush()
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
            throw e
        } catch (e: Exception) {
            NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
        }
    }
}
