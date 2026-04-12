package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.BaseUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * 动态 BaseUrl 拦截器
 * 职责：解析 Retrofit 方法上的 [BaseUrl] 注解，替换当前请求的 host
 * 优先级：注解 > [NetworkConfig.baseUrl]
 *
 * 支持在 Retrofit 的方法上使用 `@BaseUrl("https://.../")` 注解替换请求的 host/schema/port。
 * 同时保留注解中配置的路径前缀（例如 CDN 前缀），以便拼接请求具体 path。
 * @since 1.0.0
 */class DynamicBaseUrlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val baseUrlAnnotation = invocation?.method()?.getAnnotation(BaseUrl::class.java) ?: return chain.proceed(request)

        val newBaseUrl = baseUrlAnnotation.value.toHttpUrlOrNull() ?: return chain.proceed(request)
        val originalUrl = request.url

        // 合并路径前缀与原路径
        val newPath = newBaseUrl.encodedPath.trimEnd('/') + originalUrl.encodedPath
        val finalUrl = newBaseUrl.newBuilder()
            .encodedPath(newPath)
            .encodedQuery(originalUrl.encodedQuery)
            .build()

        val newRequest = request.newBuilder().url(finalUrl).build()
        return chain.proceed(newRequest)
    }
}
