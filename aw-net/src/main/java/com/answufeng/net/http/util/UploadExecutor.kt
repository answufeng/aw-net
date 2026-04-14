package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.IBaseResponse
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

@Singleton
class UploadExecutor @Inject constructor(
    private val configProvider: NetworkConfigProvider
) {

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
    ): NetworkResult<T> = trackAndExecute(eventName, tag) {
        withContext(dispatcher) {
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
    }
}
