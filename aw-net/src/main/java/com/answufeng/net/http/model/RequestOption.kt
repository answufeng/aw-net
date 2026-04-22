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
 * @param retryOnFailure 协程级重试次数（不含首次执行）。0 = 不重试（默认）
 * @param retryDelayMs 重试间隔毫秒数，默认 300ms
 * @param retryOnTechnical 是否在技术错误（网络/解析等）时重试，默认 true
 * @param retryOnBusiness 是否在业务错误时重试，默认 false
 */
data class RequestOption(
    val successCode: Int? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val tag: String? = null,
    val retryOnFailure: Int = 0,
    val retryDelayMs: Long = 300L,
    val retryOnTechnical: Boolean = true,
    val retryOnBusiness: Boolean = false
) {
    companion object {
        /**
         * 默认配置：不重试，使用全局成功码。
         */
        val DEFAULT = RequestOption()
    }
}
