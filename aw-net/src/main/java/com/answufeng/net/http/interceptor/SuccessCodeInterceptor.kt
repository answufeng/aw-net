package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.SuccessCode
import com.answufeng.net.http.util.RequestCallContext
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * 读取 Retrofit 方法上的 [@SuccessCode]，写入 [SuccessCodeTag] 与 [RequestCallContext]（若存在）。
 *
 * 对 `suspend fun (): T?` 接口，[com.answufeng.net.http.converter.UnwrapResponseConverterFactory]
 * 会直接从方法注解读取成功码；本拦截器同时写入 Request 元数据供其它链路使用。
 */
class SuccessCodeInterceptor : Interceptor {
    internal class SuccessCodeTag(val code: Int)

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val annotationCode = invocation?.method()?.getAnnotation(SuccessCode::class.java)?.value

        if (annotationCode != null) {
            val builder = request.newBuilder().tag(SuccessCodeTag::class.java, SuccessCodeTag(annotationCode))
            val ctx = request.tag(RequestCallContext::class.java)
            if (ctx != null) {
                builder.tag(
                    RequestCallContext::class.java,
                    ctx.copy(successCode = ctx.successCode ?: annotationCode),
                )
            }
            request = builder.build()
        }

        return chain.proceed(request)
    }
}
