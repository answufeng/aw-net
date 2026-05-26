package com.answufeng.net.http.util

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * 在发起请求前把 [RequestCallContextHolder] 写入 [okhttp3.Request] tag，
 * 避免协程 IO 线程与 OkHttp 拦截器线程不一致导致 ThreadLocal 丢失。
 */
fun OkHttpClient.withRequestCallContextInjection(): OkHttpClient {
    val injectionInterceptor =
        Interceptor { chain ->
            val ctx = RequestCallContextHolder.get()
            val request = chain.request()
            val tagged =
                if (ctx != null && request.tag(RequestCallContext::class.java) == null) {
                    request.newBuilder()
                        .tag(RequestCallContext::class.java, ctx)
                        .build()
                } else {
                    request
                }
            chain.proceed(tagged)
        }
    return newBuilder()
        .addInterceptor(injectionInterceptor)
        .build()
}
