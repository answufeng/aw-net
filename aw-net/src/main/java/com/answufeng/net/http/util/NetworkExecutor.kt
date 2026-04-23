package com.answufeng.net.http.util

import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ProgressInfo
import com.answufeng.net.http.model.RequestOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NetworkExecutor 是对外的统一执行入口。它将具体执行逻辑委托给更细粒度的执行器：
 * - [RequestExecutor]：普通业务请求（BaseResponse<T>）
 * - [DownloadExecutor]：文件下载（ResponseBody -> File）
 * - [UploadExecutor]：文件上传（Multipart）
 *
 * 目的：对上层暴露简单、可观测、可配置的 API，同时内部统一上报监控事件与异常处理。
 *
 * 类中向宿主暴露的 API 以 DI 与反射方式使用较多，**勿**因「库内无引用」删减方法。
 */
@Singleton
@Suppress("unused", "MemberVisibilityCanBePrivate")
class NetworkExecutor @Inject constructor(
    private val requestExecutor: RequestExecutor,
    private val downloadExecutor: DownloadExecutor,
    private val uploadExecutor: UploadExecutor,
    @PublishedApi internal val retrofit: Retrofit
) {

    companion object {
        /**
         * 进度 Flow 的额外缓冲容量，避免快速发射导致挂起。
         */
        private const val PROGRESS_FLOW_BUFFER_CAPACITY = 64

        /**
         * 创建默认的进度 Flow：replay=1 保证晚订阅者也能拿到完成事件(done=true)。
         */
        fun createDefaultProgressFlow(): MutableSharedFlow<ProgressInfo> =
            MutableSharedFlow(replay = 1, extraBufferCapacity = PROGRESS_FLOW_BUFFER_CAPACITY)
    }

    /**
     * 使用内联泛型创建 Retrofit API 接口实例，简化 `retrofit.create(XxxApi::class.java)` 的调用。
     *
     * 使用示例：
     * ```kotlin
     * val userApi: UserApi = executor.createApi()
     * val result = executor.executeRequest { userApi.getUser() }
     * ```
     * @param T Retrofit API 接口类型
     * @return API 接口实例
     */
    inline fun <reified T> createApi(): T = retrofit.create(T::class.java)

    /**
     * 执行带标准返回结构的业务请求（BaseResponse<T>）。
     * @param successCode 如果为 null 则使用全局配置的成功码
     * @param dispatcher 协程调度器，默认 IO
     * @param tag 可选的业务标签，会被包含到监控事件中
     * @param retryOnFailure 协程级重试次数（不含首次执行）。0 = 不重试（默认）
     * @param retryDelayMs 重试间隔基准毫秒数，默认 [RequestOption.DEFAULT_RETRY_DELAY_MS]
     * @param retryOnTechnical 是否在技术错误（网络/解析等）时重试，默认 true
     * @param retryOnBusiness 是否在业务错误时重试，默认 false
     * @param call Retrofit 的 suspend 接口方法，返回 [BaseResponse<T>]
     */
    @Deprecated(
        message = "推荐使用 executeRequest(option, call) 替代多参数版本，代码更清晰",
        level = DeprecationLevel.WARNING
    )
    suspend fun <T> executeRequest(
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = RequestOption.DEFAULT_RETRY_DELAY_MS,
        retryOnTechnical: Boolean = true,
        retryOnBusiness: Boolean = false,
        call: suspend () -> BaseResponse<T>
    ): NetworkResult<T> {
        return requestExecutor.executeRequest(successCode, dispatcher, tag, retryOnFailure, retryDelayMs, retryOnTechnical, retryOnBusiness, call)
    }

    /**
     * 使用 [RequestOption] 执行带标准返回结构的业务请求（BaseResponse<T>）。
     *
     * 推荐使用此方法替代多参数版本，代码更清晰：
     * ```kotlin
     * val result = executor.executeRequest(
     *     option = RequestOption(retryOnFailure = 3, tag = "getUserInfo")
     * ) { api.getUser() }
     * ```
     * @param option 请求配置选项
     * @param call Retrofit 的 suspend 接口方法，返回 [BaseResponse<T>]
     */
    suspend fun <T> executeRequest(
        option: RequestOption,
        call: suspend () -> BaseResponse<T>
    ): NetworkResult<T> {
        return requestExecutor.executeRequest(
            option.successCode, option.dispatcher, option.tag,
            option.retryOnFailure, option.retryDelayMs,
            option.retryOnTechnical, option.retryOnBusiness, call
        )
    }

    /**
     * 执行原始的 Retrofit suspend 调用（接口直接返回 T 而非 BaseResponse）。
     * 返回值同样用 [NetworkResult] 包装并做统一异常处理。
     * @param retryOnFailure 协程级重试次数（不含首次执行）。0 = 不重试（默认）
     * @param retryDelayMs 重试间隔基准毫秒数
     */
    @Suppress("unused")
    suspend fun <T> executeRawRequest(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = RequestOption.DEFAULT_RETRY_DELAY_MS,
        call: suspend () -> T
    ): NetworkResult<T> {
        return requestExecutor.executeRawRequest(dispatcher, tag, retryOnFailure, retryDelayMs, call)
    }

    /**
     * 使用 [RequestOption] 执行原始的 Retrofit suspend 调用。
     * @param option 请求配置选项
     * @param call Retrofit 的 suspend 接口方法
     */
    suspend fun <T> executeRawRequest(
        option: RequestOption,
        call: suspend () -> T
    ): NetworkResult<T> {
        return requestExecutor.executeRawRequest(
            option.dispatcher, option.tag,
            option.retryOnFailure, option.retryDelayMs, call
        )
    }

    /**
     * 以 [Flow] 形式执行带标准返回结构的业务请求。
     *
     * **行为**：**冷流**且仅 [kotlinx.coroutines.flow.emit] 一次 [NetworkResult]（与单次 [executeRequest] 等价），便于在 ViewModel 中配合 `stateIn` / `map` 等使用；**不是**多事件流。
     *
     * @param option 请求配置选项
     * @param call Retrofit 的 suspend 接口方法，返回 [BaseResponse<T>]
     */
    fun <T> requestResultFlow(
        option: RequestOption = RequestOption.DEFAULT,
        call: suspend () -> BaseResponse<T>
    ): Flow<NetworkResult<T>> = flow {
        emit(executeRequest(option, call))
    }

    /**
     * 与 [requestResultFlow] 行为相同；请迁移至 [requestResultFlow]。
     */
    @Deprecated(
        message = "请使用 requestResultFlow（单结果冷流，语义相同）",
        replaceWith = ReplaceWith("requestResultFlow(option, call)"),
        level = DeprecationLevel.WARNING
    )
    fun <T> executeRequestFlow(
        option: RequestOption = RequestOption.DEFAULT,
        call: suspend () -> BaseResponse<T>
    ): Flow<NetworkResult<T>> = requestResultFlow(option, call)

    /**
     * 以 [Flow] 形式执行原始的 Retrofit suspend 调用。仅 [emit] 一次（同 [requestResultFlow] 的“单发”约定）。
     * @param option 请求配置选项
     * @param call Retrofit 的 suspend 接口方法
     */
    fun <T> rawRequestResultFlow(
        option: RequestOption = RequestOption.DEFAULT,
        call: suspend () -> T
    ): Flow<NetworkResult<T>> = flow {
        emit(executeRawRequest(option, call))
    }

    /**
     * 与 [rawRequestResultFlow] 行为相同。请迁移至 [rawRequestResultFlow]。
     */
    @Deprecated(
        message = "请使用 rawRequestResultFlow",
        replaceWith = ReplaceWith("rawRequestResultFlow(option, call)"),
        level = DeprecationLevel.WARNING
    )
    fun <T> executeRawRequestFlow(
        option: RequestOption = RequestOption.DEFAULT,
        call: suspend () -> T
    ): Flow<NetworkResult<T>> = rawRequestResultFlow(option, call)

    /**
     * 下载文件到本地。
     * @param targetFile 目标保存文件（包含路径），方法会确保父目录存在。
     * @param progressFlow 可选的进度流，使用 [NetworkExecutor.createDefaultProgressFlow] 推荐；可为 null。
     * @param expectedHash 可选：预期的文件摘要（hex 小写/大写均支持）。若提供，会在写入完成后校验。
     * @param hashAlgorithm 摘要算法，默认 SHA-256
     * @param hashStrategy 校验失败时的行为（删除或保留）
     * @param dispatcher 协程调度器
     * @param tag 可选监控标签
     * @param call 返回 [ResponseBody] 的 suspend Retrofit 方法（注意使用 @Streaming）
     */
    suspend fun downloadFile(
        targetFile: File,
        progressFlow: MutableSharedFlow<ProgressInfo>? = null,
        expectedHash: String? = null,
        hashAlgorithm: String = "SHA-256",
        hashStrategy: HashVerificationStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> {
        return downloadExecutor.downloadFile(targetFile, progressFlow, expectedHash, hashAlgorithm, hashStrategy, dispatcher, tag, call)
    }

    /**
     * 向后兼容的简写方法（仅保留最常用参数）。
     */
    suspend fun downloadFile(
        targetFile: File,
        progressFlow: MutableSharedFlow<ProgressInfo>? = null,
        call: suspend () -> ResponseBody
    ): NetworkResult<File> {
        return downloadFile(
            targetFile = targetFile,
            progressFlow = progressFlow,
            expectedHash = null,
            hashAlgorithm = "SHA-256",
            hashStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH,
            dispatcher = Dispatchers.IO,
            tag = null,
            call = call
        )
    }

    /**
     * 断点续传下载。从已有文件末尾继续下载。
     *
     * 调用方需在 [call] 中添加 `Range` 请求头：
     * ```kotlin
     * executor.downloadFileResumable(
     *     targetFile = file,
     *     existingFileSize = file.length(),
     *     call = { api.downloadFile("bytes=${file.length()}-") }
     * )
     * ```
     * @param targetFile 目标保存文件
     * @param existingFileSize 已有文件大小，0 表示从头下载
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
    ): NetworkResult<File> {
        return downloadExecutor.downloadFileResumable(
            targetFile, existingFileSize, progressFlow,
            expectedHash, hashAlgorithm, hashStrategy,
            dispatcher, tag, call
        )
    }

    /**
     * 快速构造单个带进度的 Multipart part。
     * @param partName 表单字段名
     * @param file 待上传文件
     * @param progressFlow 可选的进度流
     */
    @Suppress("unused")
    fun createProgressPart(
        partName: String,
        file: File,
        progressFlow: MutableSharedFlow<ProgressInfo>?
    ): MultipartBody.Part {
        return uploadExecutor.createProgressPart(partName, file, progressFlow)
    }

    /**
     * 单文件上传快捷方法，内部会把文件封装为带进度的 Part 并调用传入的 Retrofit 接口。
     * @param call 接收一个 MultipartBody.Part 并返回 BaseResponse<T>
     */
    @Suppress("unused") // 公开 API — 单文件上传快捷方法
    suspend fun <T> uploadFile(
        file: File,
        partName: String,
        progressFlow: MutableSharedFlow<ProgressInfo>? = null,
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend (MultipartBody.Part) -> BaseResponse<T>
    ): NetworkResult<T> {
        return uploadExecutor.uploadFile(file, partName, progressFlow, successCode, dispatcher, tag, call)
    }

    /**
     * 多文件 / 多 Part 上传接口。
     * @param parts 由调用方构造的 MultipartBody.Part 列表
     * @param formFields 可选的额外表单字段（@PartMap）
     * @param call 接收 parts 与 formFields 的 Retrofit 方法，返回 BaseResponse<T>
     */
    @Suppress("unused") // 公开 API — 多 Part 上传接口
    suspend fun <T> uploadParts(
        parts: List<MultipartBody.Part>,
        formFields: Map<String, RequestBody> = emptyMap(),
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        call: suspend (List<MultipartBody.Part>, Map<String, RequestBody>) -> BaseResponse<T>
    ): NetworkResult<T> {
        return uploadExecutor.uploadParts(parts, formFields, successCode, dispatcher, tag, call)
    }
}
