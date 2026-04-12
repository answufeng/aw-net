package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.IBaseResponse
import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ProgressInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 负责文件上传的执行器，提供单文件与多 Part 上传支持。
 * - 单文件上传通过将 [File] 封装为带进度的 [ProgressRequestBody]
 * - 多 Part 上传直接接受 [MultipartBody.Part] 列表与可选的表单字段
 * @since 1.0.0
 */
@Singleton
class UploadExecutor @Inject constructor(
    private val configProvider: NetworkConfigProvider
) {

    /**
     * 构造一个带进度回调的 Multipart part，适用于单文件上传场景。
     * @since 1.0.0
 */
    fun createProgressPart(
        partName: String,
        file: File,
        progressFlow: MutableSharedFlow<ProgressInfo>?
    ): MultipartBody.Part {
        val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val progressBody = ProgressRequestBody(requestFile) { info ->
            progressFlow?.tryEmit(info)
        }
        return MultipartBody.Part.createFormData(partName, file.name, progressBody)
    }

    /**
     * 单文件上传，内部会把 file 包装为带进度的 part，并调用给定的 Retrofit 接口。
     * 返回值采用 [NetworkResult] 统一封装。
     * @since 1.0.0
 */
    suspend fun <T> uploadFile(
        file: File,
        partName: String,
        progressFlow: MutableSharedFlow<ProgressInfo>? = null,
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend (MultipartBody.Part) -> IBaseResponse<T>
    ): NetworkResult<T> {
        val part = createProgressPart(partName, file, progressFlow)
        return executeUpload("uploadFile", successCode, dispatcher, tag) {
            call(part)
        }
    }

    /**
     * 多 Part 上传实现。
     * @param parts 已构造的 MultipartBody.Part 列表
     * @param formFields 可选的表单字段，通常作为 @PartMap 提供
     * @param call 接收 parts 与 formFields 的 Retrofit 接口
     * @since 1.0.0
 */
    suspend fun <T> uploadParts(
        parts: List<MultipartBody.Part>,
        formFields: Map<String, RequestBody> = emptyMap(),
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend (List<MultipartBody.Part>, Map<String, RequestBody>) -> IBaseResponse<T>
    ): NetworkResult<T> {
        return executeUpload("uploadParts", successCode, dispatcher, tag) {
            call(parts, formFields)
        }
    }

    private suspend fun <T> executeUpload(
        eventName: String,
        successCode: Int?,
        dispatcher: CoroutineDispatcher,
        tag: String?,
        call: suspend () -> IBaseResponse<T>
    ): NetworkResult<T> {
        val start = System.currentTimeMillis()
        NetTracker.track(
            NetEvent(
                name = eventName,
                stage = NetEventStage.START,
                timestampMs = start,
                tag = tag
            )
        )

        val result = withContext(dispatcher) {
            try {
                val response = call()
                val effectiveSuccessCode = successCode ?: configProvider.current.defaultSuccessCode
                if (response.code == effectiveSuccessCode) {
                    NetworkResult.Success(response.data)
                } else {
                    NetworkResult.BusinessFailure(response.code, response.msg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
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
                name = eventName,
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
}
