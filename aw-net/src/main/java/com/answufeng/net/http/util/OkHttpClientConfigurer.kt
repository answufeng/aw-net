package com.answufeng.net.http.util

import com.answufeng.net.http.auth.TokenAuthenticator
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.config.NetworkConfig
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.interceptor.DynamicBaseUrlInterceptor
import com.answufeng.net.http.interceptor.DynamicLoggingInterceptor
import com.answufeng.net.http.interceptor.DynamicRetryInterceptor
import com.answufeng.net.http.interceptor.DynamicTimeoutInterceptor
import com.answufeng.net.http.interceptor.ExtraHeadersInterceptor
import com.answufeng.net.http.interceptor.RequestExtraHeadersInterceptor
import com.answufeng.net.http.interceptor.SuccessCodeInterceptor
import com.answufeng.net.http.logging.NetLogger
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OkHttpClientConfigurer {
    fun configureBuilder(
        builder: OkHttpClient.Builder,
        configProvider: NetworkConfigProvider,
        config: NetworkConfig,
        netLogger: NetLogger,
        requestExtraHeadersInterceptor: RequestExtraHeadersInterceptor,
        customInterceptors: List<Interceptor> = emptyList(),
        coordinator: TokenRefreshCoordinator? = null,
        unauthorizedHandler: UnauthorizedHandler? = null,
    ): OkHttpClient.Builder {
        builder
            .connectTimeout(config.connectTimeout, TimeUnit.SECONDS)
            .readTimeout(config.readTimeout, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeout, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    config.maxIdleConnections,
                    config.keepAliveDurationSeconds,
                    TimeUnit.SECONDS,
                ),
            )
            .addInterceptor(DynamicBaseUrlInterceptor(configProvider, config.baseUrl))
            .addInterceptor(DynamicTimeoutInterceptor())
            .addInterceptor(SuccessCodeInterceptor())
            .addInterceptor(ExtraHeadersInterceptor(configProvider))
            .addInterceptor(requestExtraHeadersInterceptor)

        customInterceptors.forEach { builder.addInterceptor(it) }
        builder.addInterceptor(DynamicLoggingInterceptor(configProvider, netLogger))

        configureRetryInterceptor(builder, config, netLogger)
        configureTokenAuthenticator(builder, coordinator, unauthorizedHandler, netLogger)
        configureCache(builder, config, netLogger)
        configureCookieJar(builder, config)
        configureCertificatePinning(builder, config)

        return builder
    }

    private fun configureRetryInterceptor(
        builder: OkHttpClient.Builder,
        config: NetworkConfig,
        logger: NetLogger,
    ) {
        if (!config.enableRetryInterceptor) return
        try {
            builder.addInterceptor(
                DynamicRetryInterceptor(
                    fallbackStrategy =
                        DefaultRetryStrategy(
                            maxRetries = config.retryMaxAttempts,
                            initialBackoffMillis = config.retryInitialBackoffMs,
                        ),
                ),
            )
        } catch (e: Exception) {
            logger.e("OkHttpClientConfigurer", "DynamicRetryInterceptor setup failed, retry disabled", e)
        }
    }

    private fun configureTokenAuthenticator(
        builder: OkHttpClient.Builder,
        coordinator: TokenRefreshCoordinator?,
        unauthorizedHandler: UnauthorizedHandler?,
        logger: NetLogger,
    ) {
        if (coordinator == null) return
        try {
            builder.authenticator(TokenAuthenticator(coordinator, unauthorizedHandler = unauthorizedHandler))
        } catch (t: Throwable) {
            logger.e("OkHttpClientConfigurer", "TokenAuthenticator setup failed, auto-refresh disabled", t)
        }
    }

    private fun configureCache(
        builder: OkHttpClient.Builder,
        config: NetworkConfig,
        logger: NetLogger,
    ) {
        if (config.cacheDir == null || config.cacheSize == null || config.cacheSize <= 0) return
        try {
            builder.cache(Cache(config.cacheDir, config.cacheSize))
        } catch (e: Exception) {
            logger.e("OkHttpClientConfigurer", "OkHttp cache setup failed: ${config.cacheDir}", e)
        }
    }

    private fun configureCertificatePinning(
        builder: OkHttpClient.Builder,
        config: NetworkConfig,
    ) {
        if (config.certificatePins.isEmpty()) return
        val pinnerBuilder = CertificatePinner.Builder()
        config.certificatePins.forEach { certPin ->
            certPin.pins.forEach { pin ->
                pinnerBuilder.add(certPin.pattern, pin)
            }
        }
        builder.certificatePinner(pinnerBuilder.build())
    }

    private fun configureCookieJar(
        builder: OkHttpClient.Builder,
        config: NetworkConfig,
    ) {
        config.cookieJar?.let { builder.cookieJar(it) }
    }
}
