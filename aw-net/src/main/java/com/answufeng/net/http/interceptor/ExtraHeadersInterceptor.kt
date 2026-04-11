package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkConfigProvider
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 将 `NetworkConfig.extraHeaders` 注入到每次请求的 Header 中。
 *
 * 为了避免高并发下遍历变更的 map 引发并发问题，
 * 实现中对 current.extraHeaders 做了一次快照（toList）并缓存构建好的 Headers 对象以减少分配。
 * 缓存通过对 NetworkConfig 引用的恰等比较（===）实现“缓存命中”判定，
 * 变更监听器会在配置更新时清除缓存。
 */
class ExtraHeadersInterceptor(
    private val configProvider: NetworkConfigProvider
) : Interceptor {

    /**
     * 缓存快照：保存 NetworkConfig 引用和对应的 Headers。
     * 使用单个 Pair 引用确保读取的原子性：要么读到旧快照，要么读到新快照，
     * 避免之前两个独立 @Volatile 字段间的竞态条件。
     */
    @Volatile
    private var cached: Pair<NetworkConfig, Headers>? = null

    init {
        configProvider.registerListener {
            // 配置变更时清除缓存，下次请求会重新构建
            cached = null
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val currentConfig = configProvider.current
        val snapshot = cached
        val headers = if (snapshot != null && snapshot.first === currentConfig) {
            snapshot.second
        } else {
            val built = Headers.Builder().apply {
                currentConfig.extraHeaders.toList().forEach { (name, value) -> add(name, value) }
            }.build()
            cached = currentConfig to built
            built
        }

        if (headers.size == 0) return chain.proceed(chain.request())

        val rb = chain.request().newBuilder()
        for (i in 0 until headers.size) {
            rb.addHeader(headers.name(i), headers.value(i))
        }
        return chain.proceed(rb.build())
    }
}
