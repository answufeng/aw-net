package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.NetLogger
import com.answufeng.net.http.config.NetworkConfig
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.config.NetworkLogLevel
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.atomic.AtomicReference

/**
 * 按 [NetworkConfig.networkLogLevel] 选用 [okhttp3.logging.HttpLoggingInterceptor]；内部缓存了「级别 → 拦截器」
 * 以避免每次请求分配，但 [com.answufeng.net.http.config.NetworkLogLevel] 变化时依赖 `level` 比较命中缓存。
 * 配置在其它维度变化（如 [NetworkConfig.sensitiveHeaders]）时，[PrettyNetLogger] 从 [NetworkConfigProvider] 每次读取当前值，**无需**清缓存；若你扩展为「按整份配置实例重建」的语义，可在此 [init] 中注册 [NetworkConfigProvider.registerListener] 令 [cachedEntry] 置空。
 */
class DynamicLoggingInterceptor(
    private val configProvider: NetworkConfigProvider,
    netLogger: NetLogger
) : Interceptor {

    private val logger = PrettyNetLogger(netLogger, configProvider)

    private data class CacheEntry(
        val level: HttpLoggingInterceptor.Level,
        val interceptor: HttpLoggingInterceptor
    )

    private val cachedEntry = AtomicReference<CacheEntry?>(null)

    init {
        configProvider.registerListener {
            cachedEntry.set(null)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val currentConfig = configProvider.current
        val level = resolveHttpLogLevel(currentConfig)

        if (level == HttpLoggingInterceptor.Level.NONE) {
            return chain.proceed(chain.request())
        }

        val interceptor = getOrCreateInterceptor(level)
        return interceptor.intercept(chain)
    }

    private fun getOrCreateInterceptor(level: HttpLoggingInterceptor.Level): HttpLoggingInterceptor {
        val cached = cachedEntry.get()
        if (cached != null && cached.level == level) {
            return cached.interceptor
        }

        val newInterceptor = HttpLoggingInterceptor(logger).apply {
            this.level = level
        }
        val newEntry = CacheEntry(level, newInterceptor)
        cachedEntry.set(newEntry)
        return newInterceptor
    }

    private fun resolveHttpLogLevel(config: NetworkConfig): HttpLoggingInterceptor.Level {
        return when (config.networkLogLevel) {
            NetworkLogLevel.NONE -> HttpLoggingInterceptor.Level.NONE
            NetworkLogLevel.BASIC -> HttpLoggingInterceptor.Level.BASIC
            NetworkLogLevel.HEADERS -> HttpLoggingInterceptor.Level.HEADERS
            NetworkLogLevel.BODY -> HttpLoggingInterceptor.Level.BODY
        }
    }
}
