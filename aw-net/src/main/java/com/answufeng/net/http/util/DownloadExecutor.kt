package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetworkConfigProvider
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
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
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
        @Suppress("UNUSED_PARAMETER")
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> = trackAndExecute("downloadFile", tag) {
        withContext(dispatcher) {
            runDownloadFlow(targetFile, progressFlow, expectedHash, hashAlgorithm, hashStrategy, call)
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
                    var totalRead = 0L
                    var readCount: Long
                    while (src.read(buffer, DOWNLOAD_BUFFER_SIZE).also { readCount = it } != -1L) {
                        currentCoroutineContext().ensureActive()
                        if (md != null) {
                            md.update(buffer.snapshot().toByteArray())
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
