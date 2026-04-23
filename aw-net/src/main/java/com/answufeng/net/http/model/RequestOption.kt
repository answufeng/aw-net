package com.answufeng.net.http.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 请求配置选项，用于简化 [com.answufeng.net.http.util.NetworkExecutor.executeRequest] 的参数传递。
 *
 * 使用示例：
 * ```kotlin
 * val result = executor.executeRequest(
 *     option = RequestOption(
 *         successCode = 200,
 *         retryOnFailure = 3,
 *         tag = "getUserInfo"
 *     )
 * ) { api.getUser() }
 * ```
 *
 * 亦可用 [requestOption] 构建等效配置。
 *
 * @param successCode 如果为 null 则使用全局配置的成功码
 * @param dispatcher 协程调度器，默认 IO
 * @param tag 可选的业务标签，会被包含到监控事件中
 * @param retryOnFailure 协程级重试次数（不含首次执行）。0 = 不重试（默认）。[RequestExecutor] 内会将负值按 0 处理，并对异常过大值做安全上限（仅 Debug 下可能打 Log）。
 * @param retryDelayMs 重试基础间隔（毫秒；退避为指数+抖动）。[RequestExecutor] 内会将小于 1ms 的值修正为 [DEFAULT_RETRY_DELAY_MS]。
 * @param retryOnTechnical 是否在技术错误（网络/解析等）时重试，默认 true
 * @param retryOnBusiness 是否在业务错误时重试，默认 false
 */
data class RequestOption(
    val successCode: Int? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val tag: String? = null,
    val retryOnFailure: Int = 0,
    val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    val retryOnTechnical: Boolean = true,
    val retryOnBusiness: Boolean = false
) {
    companion object {
        /**
         * 协程重试的默认间隔（毫秒），与 [com.answufeng.net.http.util.RequestExecutor] 参数默认值一致。
         */
        const val DEFAULT_RETRY_DELAY_MS: Long = 300L

        /**
         * 默认配置：不重试，使用全局成功码。
         */
        val DEFAULT = RequestOption()
    }
}
