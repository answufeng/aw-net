package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.Timeout
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * 动态超时拦截器
 * 职责：解析 Retrofit 方法上的 [Timeout] 注解，并应用于当前请求
 * 优先级：注解 > 全局配置 > 基础库默认
 */
class DynamicTimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val timeout = invocation?.method()?.getAnnotation(Timeout::class.java)

        return if (timeout != null) {
            var newChain = chain
            if (timeout.connect > 0) {
                newChain = newChain.withConnectTimeout(timeout.connect, timeout.unit)
            }
            if (timeout.read > 0) {
                newChain = newChain.withReadTimeout(timeout.read, timeout.unit)
            }
            if (timeout.write > 0) {
                newChain = newChain.withWriteTimeout(timeout.write, timeout.unit)
            }
            newChain.proceed(request)
        } else {
            chain.proceed(request)
        }
    }
}
