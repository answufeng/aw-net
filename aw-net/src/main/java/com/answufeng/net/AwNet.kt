package com.answufeng.net

import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.config.NetworkConfig
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.interceptor.RequestExtraHeadersInterceptor
import com.answufeng.net.http.logging.NetLogger
import com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory
import com.answufeng.net.http.model.LenientStringTypeAdapter
import com.answufeng.net.http.util.DownloadExecutor
import com.answufeng.net.http.util.NetworkExecutor
import com.answufeng.net.http.util.NetworkMonitor
import com.answufeng.net.http.util.NoOpNetLogger
import com.answufeng.net.http.util.OkHttpClientConfigurer
import com.answufeng.net.http.util.RequestExecutor
import com.answufeng.net.http.util.UploadExecutor
import com.answufeng.net.websocket.WebSocketLogger
import com.answufeng.net.websocket.WebSocketManager
import com.answufeng.net.websocket.WebSocketManagerImpl
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Optional

object AwNet {
    fun createExecutor(
        config: NetworkConfig,
        netLogger: NetLogger = NoOpNetLogger,
        tokenProvider: TokenProvider? = null,
        unauthorizedHandler: UnauthorizedHandler? = null,
        appInterceptors: List<Interceptor> = emptyList(),
        networkMonitor: NetworkMonitor,
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
            RequestExecutor(
                configProvider,
                coordinator,
                Optional.ofNullable(unauthorizedHandler),
                requestExtraHeadersInterceptor,
                networkMonitor,
            ),
            DownloadExecutor(configProvider),
            UploadExecutor(configProvider),
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
                .registerTypeAdapter(String::class.java, LenientStringTypeAdapter())
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
            OkHttpClientConfigurer.configureBuilder(
                OkHttpClient.Builder(),
                configProvider,
                config,
                netLogger,
                requestExtraHeadersInterceptor,
                appInterceptors,
                coordinator,
                unauthorizedHandler,
            )
        builder.clientCustomizer()
        return builder.build()
    }
}
