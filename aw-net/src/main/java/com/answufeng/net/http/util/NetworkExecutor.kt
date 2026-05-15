package com.answufeng.net.http.util

import com.answufeng.net.http.interceptor.RequestExtraHeadersInterceptor
import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.DownloadOption
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ProgressInfo
import com.answufeng.net.http.model.RequestOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import java.io.File
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP API、原始 Retrofit 调用、上传和下载的公共入口。
 *
 * aw-net 2.0 仅保留 option-object 请求 API。旧的多参数重载和重复的 Flow 别名已移除，
 * 以确保重试、调度器和成功码行为显式可控。
 */
@Singleton
@Suppress("unused", "MemberVisibilityCanBePrivate")
class NetworkExecutor
    @Inject
    constructor(
        private val requestExecutor: RequestExecutor,
        private val downloadExecutor: DownloadExecutor,
        private val uploadExecutor: UploadExecutor,
        private val requestExtraHeadersInterceptor: RequestExtraHeadersInterceptor,
        @PublishedApi internal val retrofit: Retrofit,
    ) {
        companion object {
            private const val PROGRESS_FLOW_BUFFER_CAPACITY = 64

            fun createDefaultProgressFlow(): MutableSharedFlow<ProgressInfo> =
                MutableSharedFlow(replay = 1, extraBufferCapacity = PROGRESS_FLOW_BUFFER_CAPACITY)
        }

        inline fun <reified T> createApi(): T = retrofit.create(T::class.java)

        suspend fun <T> executeRequest(
            option: RequestOption = RequestOption.DEFAULT,
            call: suspend () -> BaseResponse<T>,
        ): NetworkResult<T> {
            val block: suspend () -> NetworkResult<T> = {
                requestExecutor.executeRequest(
                    option.successCode,
                    option.dispatcher,
                    option.tag,
                    option.retryOnFailure,
                    option.retryDelayMs,
                    option.retryOnTechnical,
                    option.retryOnBusiness,
                    call,
                )
            }
            return withExtraHeaders(option.extraHeaders) {
                withTotalTimeout(option.totalTimeoutMs, block)
            }
        }

        suspend fun <T> executeRawRequest(
            option: RequestOption = RequestOption.DEFAULT,
            call: suspend () -> T,
        ): NetworkResult<T> {
            val block: suspend () -> NetworkResult<T> = {
                requestExecutor.executeRawRequest(
                    option.dispatcher,
                    option.tag,
                    option.retryOnFailure,
                    option.retryDelayMs,
                    call,
                )
            }
            return withExtraHeaders(option.extraHeaders) {
                withTotalTimeout(option.totalTimeoutMs, block)
            }
        }

        private suspend fun <R> withExtraHeaders(
            headers: Map<String, String>,
            block: suspend () -> R,
        ): R {
            if (headers.isNotEmpty()) {
                requestExtraHeadersInterceptor.threadLocalHeaders.set(headers)
            }
            return try {
                block()
            } finally {
                requestExtraHeadersInterceptor.threadLocalHeaders.remove()
            }
        }

        private suspend fun <T> withTotalTimeout(
            totalTimeoutMs: Long?,
            block: suspend () -> NetworkResult<T>,
        ): NetworkResult<T> {
            if (totalTimeoutMs == null) return block()
            return try {
                withTimeout(totalTimeoutMs) { block() }
            } catch (e: TimeoutCancellationException) {
                NetworkResult.TechnicalFailure(
                    com.answufeng.net.http.exception.ExceptionHandle.handleException(
                        SocketTimeoutException("请求整体超时（${totalTimeoutMs}ms）"),
                    ),
                )
            }
        }

        fun <T> requestResultFlow(
            option: RequestOption = RequestOption.DEFAULT,
            call: suspend () -> BaseResponse<T>,
        ): Flow<NetworkResult<T>> =
            flow {
                emit(executeRequest(option, call))
            }

        fun <T> rawRequestResultFlow(
            option: RequestOption = RequestOption.DEFAULT,
            call: suspend () -> T,
        ): Flow<NetworkResult<T>> =
            flow {
                emit(executeRawRequest(option, call))
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
            return downloadExecutor.downloadFile(
                targetFile,
                progressFlow,
                expectedHash,
                hashAlgorithm,
                hashStrategy,
                failureStrategy,
                dispatcher,
                tag,
                call,
            )
        }

        suspend fun downloadFile(
            targetFile: File,
            option: DownloadOption = DownloadOption.DEFAULT,
            call: suspend () -> ResponseBody,
        ): NetworkResult<File> {
            return downloadExecutor.downloadFile(
                targetFile,
                option.progressFlow,
                option.expectedHash,
                option.hashAlgorithm,
                option.hashStrategy,
                option.failureStrategy,
                option.dispatcher,
                option.tag,
                call,
            )
        }

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
            return downloadExecutor.downloadFileResumable(
                targetFile,
                existingFileSize,
                progressFlow,
                expectedHash,
                hashAlgorithm,
                hashStrategy,
                failureStrategy,
                dispatcher,
                tag,
                call,
            )
        }

        suspend fun downloadFileResumable(
            targetFile: File,
            existingFileSize: Long = 0,
            option: DownloadOption = DownloadOption.DEFAULT,
            call: suspend () -> ResponseBody,
        ): NetworkResult<File> {
            return downloadExecutor.downloadFileResumable(
                targetFile,
                existingFileSize,
                option.progressFlow,
                option.expectedHash,
                option.hashAlgorithm,
                option.hashStrategy,
                option.failureStrategy,
                option.dispatcher,
                option.tag,
                call,
            )
        }

        fun createProgressPart(
            partName: String,
            file: File,
            progressFlow: MutableSharedFlow<ProgressInfo>?,
        ): MultipartBody.Part {
            return uploadExecutor.createProgressPart(partName, file, progressFlow)
        }

        suspend fun <T> uploadFile(
            file: File,
            partName: String,
            progressFlow: MutableSharedFlow<ProgressInfo>? = null,
            successCode: Int? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            tag: String? = null,
            call: suspend (MultipartBody.Part) -> BaseResponse<T>,
        ): NetworkResult<T> {
            return uploadExecutor.uploadFile(file, partName, progressFlow, successCode, dispatcher, tag, call)
        }

        suspend fun <T> uploadParts(
            parts: List<MultipartBody.Part>,
            formFields: Map<String, RequestBody> = emptyMap(),
            successCode: Int? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            tag: String? = null,
            call: suspend (List<MultipartBody.Part>, Map<String, RequestBody>) -> BaseResponse<T>,
        ): NetworkResult<T> {
            return uploadExecutor.uploadParts(parts, formFields, successCode, dispatcher, tag, call)
        }
    }
