package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.SuccessCode
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

class SuccessCodeInterceptor : Interceptor {

    internal class SuccessCodeTag(val code: Int)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val successCode = invocation?.method()?.getAnnotation(SuccessCode::class.java)?.value

        val newRequest = if (successCode != null) {
            request.newBuilder().tag(SuccessCodeTag::class.java, SuccessCodeTag(successCode)).build()
        } else {
            request
        }

        return chain.proceed(newRequest)
    }
}
