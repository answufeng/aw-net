package com.answufeng.net

import com.answufeng.net.http.auth.TokenAuthenticator
import com.answufeng.net.http.auth.TokenProvider
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
import com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory
import com.answufeng.net.http.util.DefaultRetryStrategy
import com.answufeng.net.http.util.DownloadExecutor
import com.answufeng.net.http.util.NetworkExecutor
import com.answufeng.net.http.util.NoOpNetLogger
import com.answufeng.net.http.util.RequestExecutor
import com.answufeng.net.http.util.UploadExecutor
import com.answufeng.net.websocket.WebSocketLogger
import com.answufeng.net.websocket.WebSocketManager
import com.answufeng.net.websocket.WebSocketManagerImpl
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * 非 Hilt 入口，适用于不使用依赖注入的应用。
 */
object AwNet {
    fun createExecutor(
        config: NetworkConfig,
        netLogger: NetLogger = NoOpNetLogger,
        tokenProvider: TokenProvider? = null,
        unauthorizedHandler: UnauthorizedHandler? = null,
        appInterceptors: List<Interceptor> = emptyList(),
        clientCustomizer: OkHttpClient.Builder.() -> Unit = {},
    ): NetworkExecutor {
        val configProvider = NetworkConfigProvider(config)
        val coordinator =
            tokenProvider?.let {
                TokenRefreshCoordinator(
                    tokenProvider = it,
                    lockAcquireTimeoutMs = config.tokenRefreshLockAcquireTimeoutMs,
                    logger = netLogger,
                )
            }
        val requestExtraHeadersInterceptor = RequestExtraHeadersInterceptor()
        val client =
            createOkHttpClient(
                configProvider = configProvider,
                netLogger = netLogger,
                coordinator = coordinator,
                unauthorizedHandler = unauthorizedHandler,
                appInterceptors = appInterceptors,
                requestExtraHeadersInterceptor = requestExtraHeadersInterceptor,
                clientCustomizer = clientCustomizer,
            )
        val retrofit = createRetrofit(config.baseUrl, client, configProvider)
        return NetworkExecutor(
            RequestExecutor(configProvider, coordinator, Optional.ofNullable(unauthorizedHandler)),
            DownloadExecutor(configProvider),
            UploadExecutor(configProvider),
            requestExtraHeadersInterceptor,
            retrofit,
        )
    }

    fun createRetrofit(
        baseUrl: String,
        client: OkHttpClient,
        configProvider: NetworkConfigProvider,
    ): Retrofit {
        val gson =
            GsonBuilder()
                .registerTypeAdapterFactory(
                    GlobalResponseTypeAdapterFactory { configProvider.current.responseFieldMapping },
                )
                .create()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun createWebSocketManager(
        client: OkHttpClient = OkHttpClient.Builder().build(),
        logger: WebSocketLogger? = null,
    ): WebSocketManager = WebSocketManagerImpl(client, logger)

    fun createOkHttpClient(
        configProvider: NetworkConfigProvider,
        netLogger: NetLogger = NoOpNetLogger,
        coordinator: TokenRefreshCoordinator? = null,
        unauthorizedHandler: UnauthorizedHandler? = null,
        appInterceptors: List<Interceptor> = emptyList(),
        requestExtraHeadersInterceptor: RequestExtraHeadersInterceptor = RequestExtraHeadersInterceptor(),
        clientCustomizer: OkHttpClient.Builder.() -> Unit = {},
    ): OkHttpClient {
        val config = configProvider.current
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout, TimeUnit.SECONDS)
                .readTimeout(config.readTimeout, TimeUnit.SECONDS)
                .writeTimeout(config.writeTimeout, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(config.maxIdleConnections, config.keepAliveDurationSeconds, TimeUnit.SECONDS))
                .addInterceptor(DynamicBaseUrlInterceptor(configProvider, config.baseUrl))
                .addInterceptor(DynamicTimeoutInterceptor())
                .addInterceptor(SuccessCodeInterceptor())
                .addInterceptor(ExtraHeadersInterceptor(configProvider))
                .addInterceptor(requestExtraHeadersInterceptor)

        appInterceptors.forEach { builder.addInterceptor(it) }
        builder.addInterceptor(DynamicLoggingInterceptor(configProvider, netLogger))

        if (config.enableRetryInterceptor) {
            builder.addInterceptor(
                DynamicRetryInterceptor(
                    DefaultRetryStrategy(
                        maxRetries = config.retryMaxAttempts,
                        initialBackoffMillis = config.retryInitialBackoffMs,
                    ),
                ),
            )
        }
        coordinator?.let {
            builder.authenticator(TokenAuthenticator(it, unauthorizedHandler = unauthorizedHandler))
        }
        if (config.cacheDir != null && config.cacheSize != null && config.cacheSize > 0) {
            builder.cache(Cache(config.cacheDir, config.cacheSize))
        }
        config.cookieJar?.let { builder.cookieJar(it) }
        if (config.certificatePins.isNotEmpty()) {
            val pinnerBuilder = CertificatePinner.Builder()
            config.certificatePins.forEach { certPin ->
                certPin.pins.forEach { pin -> pinnerBuilder.add(certPin.pattern, pin) }
            }
            builder.certificatePinner(pinnerBuilder.build())
        }
        builder.clientCustomizer()
        return builder.build()
    }
}
