package com.answufeng.net.http.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 以构建器风格组装 [RequestOption]，减少多参构造调用。
 *
 * ```kotlin
 * val result = executor.executeRequest(
 *     option = requestOption {
 *         tag = "getUser"
 *         retryOnFailure = 2
 *         retryDelayMs = 400L
 *     }
 * ) { api.getUser(1) }
 * ```
 */
inline fun requestOption(block: RequestOptionBuilder.() -> Unit): RequestOption {
    val b = RequestOptionBuilder()
    b.block()
    return b.build()
}

class RequestOptionBuilder {
    var successCode: Int? = null
    var dispatcher: CoroutineDispatcher = Dispatchers.IO
    var tag: String? = null
    var retryOnFailure: Int = 0
    var retryDelayMs: Long = RequestOption.DEFAULT_RETRY_DELAY_MS
    var retryOnTechnical: Boolean = true
    var retryOnBusiness: Boolean = false

    fun build(): RequestOption = RequestOption(
        successCode = successCode,
        dispatcher = dispatcher,
        tag = tag,
        retryOnFailure = retryOnFailure,
        retryDelayMs = retryDelayMs,
        retryOnTechnical = retryOnTechnical,
        retryOnBusiness = retryOnBusiness
    )
}
