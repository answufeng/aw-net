package com.answufeng.net.http.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class RequestExtraHeadersInterceptor : Interceptor {
    internal class ExtraHeadersTag(val headers: Map<String, String>)

    internal val threadLocalHeaders = ThreadLocal<Map<String, String>>()
    internal val threadLocalSkipRetry = ThreadLocal<Boolean>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val skipRetry = threadLocalSkipRetry.get()
        threadLocalSkipRetry.remove()

        val extraHeaders = threadLocalHeaders.get()
        threadLocalHeaders.remove()

        val request = chain.request()
        val newRequest =
            if (skipRetry == true && request.tag(DynamicRetryInterceptor.SkipRetry::class.java) == null) {
                request.newBuilder()
                    .tag(DynamicRetryInterceptor.SkipRetry::class.java, DynamicRetryInterceptor.SkipRetry())
                    .build()
            } else {
                request
            }

        if (extraHeaders.isNullOrEmpty()) return chain.proceed(newRequest)

        val rb = newRequest.newBuilder()
        extraHeaders.forEach { (name, value) -> rb.addHeader(name, value) }
        return chain.proceed(rb.build())
    }
}
