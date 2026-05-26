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
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 封装文件下载能力，提供普通下载与断点续传下载。
 *
 * 支持进度回调、摘要校验与失败后文件处理策略。
 */
@Singleton
class DownloadExecutor
    @Inject
    constructor(
        private val configProvider: NetworkConfigProvider,
        private val networkMonitor: NetworkMonitor,
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
            failureStrategy: DownloadFailureStrategy = DownloadFailureStrategy.DELETE_PARTIAL,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            tag: String? = null,
            call: suspend () -> ResponseBody,
        ): NetworkResult<File> {
            NetworkOfflineCheck.failureIfOffline(networkMonitor)?.let { @Suppress("UNCHECKED_CAST") return it as NetworkResult<File> }
            val cfg = configProvider.current
            return trackAndExecute("downloadFile", tag, cfg.enableRequestTracking, cfg.slowRequestThresholdMs) {
                withContext(dispatcher) {
                    runDownloadFlow(targetFile, progressFlow, expectedHash, hashAlgorithm, hashStrategy, failureStrategy, call)
                }
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
            failureStrategy: DownloadFailureStrategy = DownloadFailureStrategy.KEEP_PARTIAL,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            tag: String? = null,
            call: suspend () -> ResponseBody,
        ): NetworkResult<File> {
            require(existingFileSize >= 0L) { "existingFileSize must be >= 0, actual: $existingFileSize" }
            require(existingFileSize == 0L || targetFile.exists()) {
                "targetFile must exist when existingFileSize > 0: ${targetFile.absolutePath}"
            }
            require(existingFileSize == 0L || targetFile.length() >= existingFileSize) {
                "existingFileSize is larger than targetFile.length(): existingFileSize=$existingFileSize, fileLength=${targetFile.length()}"
            }
            NetworkOfflineCheck.failureIfOffline(networkMonitor)?.let { @Suppress("UNCHECKED_CAST") return it as NetworkResult<File> }
            val cfg = configProvider.current
            return trackAndExecute("downloadFileResumable", tag, cfg.enableRequestTracking, cfg.slowRequestThresholdMs) {
                withContext(dispatcher) {
                    runResumableDownloadFlow(
                        targetFile,
                        existingFileSize,
                        progressFlow,
                        expectedHash,
                        hashAlgorithm,
                        hashStrategy,
                        failureStrategy,
                        call,
                    )
                }
            }
        }

        private suspend fun runDownloadFlow(
            targetFile: File,
            progressFlow: MutableSharedFlow<ProgressInfo>?,
            expectedHash: String?,
            hashAlgorithm: String,
            hashStrategy: HashVerificationStrategy,
            failureStrategy: DownloadFailureStrategy,
            call: suspend () -> ResponseBody,
        ): NetworkResult<File> {
            return try {
                val body = call()
                val progressBody = ProgressResponseBody(body) { info -> progressFlow?.tryEmit(info) }
                val md = if (expectedHash != null) MessageDigest.getInstance(hashAlgorithm) else null

                writeSourceToFile(progressBody.source(), targetFile, append = false, md)

                verifyHashOrDelete(targetFile, expectedHash, md, hashStrategy) ?: NetworkResult.Success(targetFile)
            } catch (e: CancellationException) {
                cleanupPartialFile(targetFile, failureStrategy)
                throw e
            } catch (e: Exception) {
                cleanupPartialFile(targetFile, failureStrategy)
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
            failureStrategy: DownloadFailureStrategy,
            call: suspend () -> ResponseBody,
        ): NetworkResult<File> {
            return try {
                val body = call()
                if (existingFileSize > 0L && body.contentLength() == 0L) {
                    return NetworkResult.TechnicalFailure(
                        ExceptionHandle.handleException(
                            IllegalStateException("resumable download returned empty body for existingFileSize=$existingFileSize"),
                        ),
                    )
                }
                val progressBody =
                    ProgressResponseBody(body) { info ->
                        if (existingFileSize <= 0L) {
                            progressFlow?.tryEmit(info)
                        } else {
                            val newCurrent = info.currentSize + existingFileSize
                            val newTotal =
                                when {
                                    info.totalSize > 0L -> info.totalSize + existingFileSize
                                    else -> info.totalSize
                                }
                            val newProgress =
                                when {
                                    newTotal > 0L -> (100L * newCurrent / newTotal).toInt().coerceIn(0, 100)
                                    else -> info.progress
                                }
                            progressFlow?.tryEmit(
                                info.copy(
                                    currentSize = newCurrent,
                                    totalSize = newTotal,
                                    progress = newProgress,
                                ),
                            )
                        }
                    }
                val md = if (expectedHash != null) MessageDigest.getInstance(hashAlgorithm) else null

                if (existingFileSize > 0 && md != null) {
                    digestExistingFile(targetFile, md)
                }

                writeSourceToFile(progressBody.source(), targetFile, append = existingFileSize > 0, md)

                verifyHashOrDelete(targetFile, expectedHash, md, hashStrategy) ?: NetworkResult.Success(targetFile)
            } catch (e: CancellationException) {
                cleanupPartialFile(targetFile, failureStrategy)
                throw e
            } catch (e: Exception) {
                cleanupPartialFile(targetFile, failureStrategy)
                NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
            }
        }

        private suspend fun writeSourceToFile(
            source: okio.Source,
            targetFile: File,
            append: Boolean,
            md: MessageDigest?,
        ) {
            source.use { src ->
                targetFile.parentFile?.mkdirs()
                val sink =
                    if (append && targetFile.exists()) {
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
        }

        private fun digestExistingFile(
            file: File,
            md: MessageDigest,
        ) {
            file.inputStream().buffered().use { fis ->
                val buf = ByteArray(DOWNLOAD_BUFFER_SIZE.toInt())
                var len: Int
                while (fis.read(buf).also { len = it } != -1) {
                    md.update(buf, 0, len)
                }
            }
        }

        private fun verifyHashOrDelete(
            targetFile: File,
            expectedHash: String?,
            md: MessageDigest?,
            hashStrategy: HashVerificationStrategy,
        ): NetworkResult.TechnicalFailure? {
            if (expectedHash == null || md == null) return null
            val digest = md.digest().joinToString("") { "%02x".format(it) }
            if (digest.equals(expectedHash, ignoreCase = true)) return null
            if (hashStrategy == HashVerificationStrategy.DELETE_ON_MISMATCH) {
                try {
                    if (targetFile.exists()) targetFile.delete()
                } catch (_: SecurityException) {
                }
            }
            return NetworkResult.TechnicalFailure(
                ExceptionHandle.handleException(
                    IllegalStateException("download hash mismatch: expected=$expectedHash, actual=$digest"),
                ),
            )
        }

        private fun cleanupPartialFile(
            targetFile: File,
            failureStrategy: DownloadFailureStrategy,
        ) {
            if (failureStrategy != DownloadFailureStrategy.DELETE_PARTIAL) return
            try {
                if (targetFile.exists()) targetFile.delete()
            } catch (_: Exception) {
            }
        }
    }
