package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.BaseUrl
import com.answufeng.net.http.config.NetworkConfigProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * 动态 BaseUrl 拦截器
 * 职责：解析 Retrofit 方法上的 [BaseUrl] 注解，或运行时配置变更，替换当前请求的 host
 * 优先级：@BaseUrl 注解 > NetworkConfigProvider.current.baseUrl > Retrofit 默认 baseUrl
 *
 * 支持两种方式切换 BaseUrl：
 * 1. 方法级注解：`@BaseUrl("https://cdn.example.com/")` — 按接口切换
 * 2. 运行时配置：通过 `NetworkConfigProvider.update { it.copy(baseUrl = newUrl) }` — 全局切换
 */
class DynamicBaseUrlInterceptor(
    private val configProvider: NetworkConfigProvider? = null,
    initialBaseUrl: String? = configProvider?.current?.baseUrl,
) : Interceptor {
    private val retrofitBaseUrl = initialBaseUrl?.toHttpUrlOrNull()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val baseUrlAnnotation = invocation?.method()?.getAnnotation(BaseUrl::class.java)

        val targetBaseUrl =
            if (baseUrlAnnotation != null) {
                baseUrlAnnotation.value.toHttpUrlOrNull()
            } else {
                resolveRuntimeBaseUrl(request)
            }

        if (targetBaseUrl == null) return chain.proceed(request)

        val originalUrl = request.url
        if (originalUrl.host == targetBaseUrl.host &&
            originalUrl.scheme == targetBaseUrl.scheme &&
            originalUrl.port == targetBaseUrl.port &&
            isSameBase(targetBaseUrl, retrofitBaseUrl)
        ) {
            return chain.proceed(request)
        }

        val relativePath = resolveRelativePath(originalUrl.encodedPath)
        val newPath = joinEncodedPath(targetBaseUrl.encodedPath, relativePath)
        val finalUrl =
            targetBaseUrl.newBuilder()
                .encodedPath(newPath)
                .encodedQuery(originalUrl.encodedQuery)
                .build()

        val newRequest = request.newBuilder().url(finalUrl).build()
        return chain.proceed(newRequest)
    }

    private fun resolveRuntimeBaseUrl(request: okhttp3.Request): okhttp3.HttpUrl? {
        val provider = configProvider ?: return null
        val currentBaseUrl = provider.current.baseUrl.toHttpUrlOrNull() ?: return null
        val requestUrl = request.url
        if (requestUrl.host != currentBaseUrl.host ||
            requestUrl.scheme != currentBaseUrl.scheme ||
            requestUrl.port != currentBaseUrl.port
        ) {
            return currentBaseUrl
        }
        return null
    }

    private fun resolveRelativePath(originalPath: String): String {
        val basePath = retrofitBaseUrl?.encodedPath ?: return originalPath.trimStart('/')
        val normalizedBase = basePath.trimEnd('/')
        return when {
            normalizedBase.isEmpty() -> originalPath.trimStart('/')
            originalPath == normalizedBase -> ""
            originalPath.startsWith("$normalizedBase/") -> originalPath.removePrefix("$normalizedBase/")
            else -> originalPath.trimStart('/')
        }
    }

    private fun joinEncodedPath(
        basePath: String,
        relativePath: String,
    ): String {
        val normalizedBase = basePath.trimEnd('/')
        val normalizedRelative = relativePath.trimStart('/')
        return when {
            normalizedBase.isEmpty() && normalizedRelative.isEmpty() -> "/"
            normalizedBase.isEmpty() -> "/$normalizedRelative"
            normalizedRelative.isEmpty() -> normalizedBase.ifEmpty { "/" }
            else -> "$normalizedBase/$normalizedRelative"
        }
    }

    private fun isSameBase(
        left: okhttp3.HttpUrl,
        right: okhttp3.HttpUrl?,
    ): Boolean {
        if (right == null) return false
        return left.scheme == right.scheme &&
            left.host == right.host &&
            left.port == right.port &&
            left.encodedPath.trimEnd('/') == right.encodedPath.trimEnd('/')
    }
}
