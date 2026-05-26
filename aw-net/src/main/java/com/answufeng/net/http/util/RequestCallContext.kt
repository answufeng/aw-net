package com.answufeng.net.http.util

/**
 * 单次 Retrofit/OkHttp 调用的请求级上下文（通过 [RequestCallContextHolder] 注入到 [okhttp3.Request] tag）。
 */
data class RequestCallContext(
    val extraHeaders: Map<String, String> = emptyMap(),
    val skipOkHttpRetry: Boolean = false,
    /** 覆盖业务成功码；通常来自 [com.answufeng.net.http.model.RequestOption.successCode]。 */
    val successCode: Int? = null,
)

object RequestCallContextHolder {
    private val local = ThreadLocal<RequestCallContext?>()

    fun set(context: RequestCallContext?) {
        if (context == null) {
            local.remove()
        } else {
            local.set(context)
        }
    }

    fun get(): RequestCallContext? = local.get()

    inline fun <R> withContext(
        context: RequestCallContext?,
        block: () -> R,
    ): R {
        val previous = get()
        set(context)
        return try {
            block()
        } finally {
            set(previous)
        }
    }
}
