package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.SuccessCode
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * 根据 Retrofit 方法上的 [SuccessCode] 在 [okhttp3.Request] 上挂接成功码元数据，供 [com.answufeng.net.http.model.GlobalResponse] 等按「方法级成功码」解析；无注解时原样 [chain.proceed]。
 */
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
