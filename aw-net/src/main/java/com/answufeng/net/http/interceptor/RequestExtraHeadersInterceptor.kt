package com.answufeng.net.http.interceptor

import com.answufeng.net.http.util.RequestCallContext
import okhttp3.Interceptor
import okhttp3.Response

class RequestExtraHeadersInterceptor : Interceptor {
    @Deprecated("Use RequestCallContext on Request tag", level = DeprecationLevel.WARNING)
    internal class ExtraHeadersTag(val headers: Map<String, String>)

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val context = request.tag(RequestCallContext::class.java)

        if (context?.skipOkHttpRetry == true &&
            request.tag(DynamicRetryInterceptor.SkipRetry::class.java) == null
        ) {
            request =
                request.newBuilder()
                    .tag(DynamicRetryInterceptor.SkipRetry::class.java, DynamicRetryInterceptor.SkipRetry())
                    .build()
        }

        val extraHeaders = context?.extraHeaders.orEmpty()
        if (extraHeaders.isEmpty()) return chain.proceed(request)

        val rb = request.newBuilder()
        extraHeaders.forEach { (name, value) -> rb.header(name, value) }
        return chain.proceed(rb.build())
    }
}
