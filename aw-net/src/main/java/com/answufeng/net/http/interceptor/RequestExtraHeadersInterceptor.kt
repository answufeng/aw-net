package com.answufeng.net.http.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class RequestExtraHeadersInterceptor : Interceptor {
    internal val threadLocalHeaders = ThreadLocal<Map<String, String>>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val extraHeaders = threadLocalHeaders.get()
        threadLocalHeaders.remove()

        if (extraHeaders.isNullOrEmpty()) return chain.proceed(chain.request())

        val rb = chain.request().newBuilder()
        extraHeaders.forEach { (name, value) -> rb.addHeader(name, value) }
        return chain.proceed(rb.build())
    }
}
