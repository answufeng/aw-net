package com.answufeng.net.http.util

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Deprecated(
    "使用 DynamicRetryInterceptor 替代，支持注解配置和抖动退避",
    ReplaceWith("DynamicRetryInterceptor(strategy)")
)
class RetryInterceptor(
    private val strategy: RetryStrategy = DefaultRetryStrategy()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        var attempt = 0

        while (true) {
            try {
                val response = chain.proceed(request)
                if (!strategy.shouldRetry(request, response, null, attempt)) {
                    return response
                }
                response.close()
            } catch (ioe: IOException) {
                if (!strategy.shouldRetry(request, null, ioe, attempt)) {
                    throw ioe
                }
            }

            val backoff = strategy.nextDelayMillis(attempt)
            try {
                Thread.sleep(backoff)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("retry interrupted")
            }
            attempt++
        }
    }
}
