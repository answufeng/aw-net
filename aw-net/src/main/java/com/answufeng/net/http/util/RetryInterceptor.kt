package com.answufeng.net.http.util

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp 重试拦截器，使用可配置的 [RetryStrategy] 决定重试策略。
 *
 * 工作流程：
 * 1. 执行请求
 * 2. 如果失败且 [RetryStrategy.shouldRetry] 返回 true，等待退避时间后重试
 * 3. 否则返回响应或抛出异常
 * @since 1.0.0
 */
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
