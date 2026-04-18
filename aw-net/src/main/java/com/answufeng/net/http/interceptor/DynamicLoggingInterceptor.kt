package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.NetLogger
import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.annotations.NetworkLogLevel
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

class DynamicLoggingInterceptor(
    private val configProvider: NetworkConfigProvider,
    netLogger: NetLogger
) : Interceptor {

    private val logger = PrettyNetLogger(netLogger, configProvider)

    @Volatile
    private var cachedLevel: HttpLoggingInterceptor.Level? = null

    @Volatile
    private var cachedInterceptor: HttpLoggingInterceptor? = null

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
        val cached = cachedInterceptor
        val cachedLvl = cachedLevel
        if (cached != null && cachedLvl == level) {
            return cached
        }

        val newInterceptor = HttpLoggingInterceptor(logger).apply {
            this.level = level
        }
        cachedInterceptor = newInterceptor
        cachedLevel = level
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
