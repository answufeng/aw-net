package com.answufeng.net.http.di

import com.answufeng.net.http.annotations.AppInterceptor
import com.answufeng.net.http.annotations.NetLogger
import com.answufeng.net.http.annotations.NetTracker as NetTrackerApi
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.auth.TokenAuthenticator
import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.interceptor.DynamicBaseUrlInterceptor
import com.answufeng.net.http.interceptor.DynamicLoggingInterceptor
import com.answufeng.net.http.interceptor.DynamicTimeoutInterceptor
import com.answufeng.net.http.interceptor.ExtraHeadersInterceptor
import com.answufeng.net.http.interceptor.SuccessCodeInterceptor
import com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory
import com.answufeng.net.http.interceptor.DynamicRetryInterceptor
import com.answufeng.net.http.util.DefaultRetryStrategy
import com.answufeng.net.http.util.NetTracker
import com.answufeng.net.http.util.NetworkClientFactory
import com.answufeng.net.http.util.orDefault
import com.answufeng.net.http.util.getOrNull
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Optional
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt 网络模块，提供 OkHttpClient、Retrofit、NetworkClientFactory 的单例绑定。
 *
 * 项目层可通过 Hilt 的 `@Optional` 注入机制覆盖部分行为：
 * - [NetLogger]：自定义日志输出
 * - [TokenProvider]：Token 管理，用于自动刷新
 * - [UnauthorizedHandler]：未授权回调
 * - [Interceptor]（@AppInterceptor）：自定义应用拦截器
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 当项目层未提供 [NetLogger] 时的空实现兜底。
     */
    private val NOOP_NET_LOGGER: NetLogger = object : NetLogger {
        override fun d(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    /**
     * 提供全局共享的 OkHttpClient 实例。
     *
     * 构建时从 [NetworkConfigProvider.current] 读取连接超时、读/写超时、连接池、重试/鉴权/缓存/钉扎等**一次性**写入选项；
     * 之后仅通过 [NetworkConfigProvider] 再改**全局**的 connect/read/write 秒数、连接池参数等，**不会**让本单例
     * [OkHttpClient] 自动重配，除非应用自行在模块里提供可重建的 Client 工厂。运行时切环境/换请求级超时请依赖
     * [com.answufeng.net.http.interceptor.DynamicBaseUrlInterceptor] 与 [com.answufeng.net.http.interceptor.DynamicTimeoutInterceptor] 等，详见 [com.answufeng.net.http.config.NetworkConfig] 文档。
     *
     * 拦截器执行顺序（应用拦截器）：
     * 1. DynamicBaseUrlInterceptor：尽早确定最终 host/schema/port
     * 2. DynamicTimeoutInterceptor：基于注解覆写本次请求的超时配置
     * 3. SuccessCodeInterceptor：为 SuccessCode 注解写入 request tag
     * 4. ExtraHeadersInterceptor：补齐通用 Header
     * 5. 自定义拦截器：项目层按 key 排序后插入
     * 6. 日志拦截器：最后一环，打印最终请求信息
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        configProvider: NetworkConfigProvider,
        netLoggerOptional: Optional<NetLogger>,
        @AppInterceptor optionalCustomInterceptors: Optional<Map<Int, @JvmSuppressWildcards Interceptor>>,
        coordinator: TokenRefreshCoordinator?,
        unauthorizedHandlerOptional: Optional<UnauthorizedHandler>
    ): OkHttpClient {
        val config = configProvider.current
        val netLogger = netLoggerOptional.orDefault(NOOP_NET_LOGGER)
        val customInterceptors = optionalCustomInterceptors.orDefault(emptyMap())

        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout, TimeUnit.SECONDS)
            .readTimeout(config.readTimeout, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeout, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    config.maxIdleConnections,
                    config.keepAliveDurationSeconds,
                    TimeUnit.SECONDS
                )
            )
            .addInterceptor(DynamicBaseUrlInterceptor(configProvider))
            .addInterceptor(DynamicTimeoutInterceptor())
            .addInterceptor(SuccessCodeInterceptor())
            .addInterceptor(ExtraHeadersInterceptor(configProvider))

        customInterceptors.toSortedMap().forEach { (_, interceptor) ->
            builder.addInterceptor(interceptor)
        }

        builder.addInterceptor(DynamicLoggingInterceptor(configProvider, netLogger))

        configureRetryInterceptor(builder, config, netLogger)
        configureTokenAuthenticator(builder, coordinator, unauthorizedHandlerOptional, netLogger)
        configureCache(builder, config, netLogger)
        configureCookieJar(builder, config)
        configureCertificatePinning(builder, config)

        return builder.build()
    }

    /**
     * 提供全局 Retrofit 实例。
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        factory: NetworkClientFactory,
        configProvider: NetworkConfigProvider
    ): Retrofit {
        val config = configProvider.current
        return factory.createRetrofit(config.baseUrl)
    }

    /**
     * 默认的 Retrofit 工厂实现：复用全局 OkHttpClient + GsonConverterFactory。
     * 如需多 Retrofit 实例，项目层可以自行注入自定义实现覆盖此工厂。
     */
    @Provides
    @Singleton
    fun provideNetworkClientFactory(
        client: OkHttpClient,
        configProvider: NetworkConfigProvider
    ): NetworkClientFactory {
        val gson = GsonBuilder()
            .registerTypeAdapterFactory(
                GlobalResponseTypeAdapterFactory {
                    configProvider.current.responseFieldMapping
                }
            )
            .create()

        return object : NetworkClientFactory {
            override fun createRetrofit(baseUrl: String): Retrofit {
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
            }
        }
    }

    private fun configureRetryInterceptor(builder: OkHttpClient.Builder, config: com.answufeng.net.http.config.NetworkConfig, logger: NetLogger) {
        if (!config.enableRetryInterceptor) return
        try {
            builder.addInterceptor(
                DynamicRetryInterceptor(
                    fallbackStrategy = DefaultRetryStrategy(
                        maxRetries = config.retryMaxAttempts,
                        initialBackoffMillis = config.retryInitialBackoffMs
                    )
                )
            )
        } catch (e: Exception) {
            logger.e("NetworkModule", "DynamicRetryInterceptor setup failed, retry disabled", e)
        }
    }

    @Provides
    @Singleton
    fun provideTokenRefreshCoordinator(
        tokenProvider: Optional<TokenProvider>,
        netLoggerOptional: Optional<NetLogger>,
        configProvider: NetworkConfigProvider
    ): TokenRefreshCoordinator? {
        val tp = tokenProvider.getOrNull() ?: return null
        val logger = netLoggerOptional.orDefault(NOOP_NET_LOGGER)
        return TokenRefreshCoordinator(
            tokenProvider = tp,
            lockAcquireTimeoutMs = configProvider.current.tokenRefreshLockAcquireTimeoutMs,
            logger = logger
        )
    }

    private fun configureTokenAuthenticator(
        builder: OkHttpClient.Builder,
        coordinator: TokenRefreshCoordinator?,
        unauthorizedHandlerOptional: Optional<UnauthorizedHandler>,
        logger: NetLogger
    ) {
        if (coordinator == null) return
        try {
            val handler = unauthorizedHandlerOptional.getOrNull()
            builder.authenticator(TokenAuthenticator(coordinator, unauthorizedHandler = handler))
        } catch (t: Throwable) {
            logger.e("NetworkModule", "TokenAuthenticator setup failed, auto-refresh disabled", t)
        }
    }

    private fun configureCache(builder: OkHttpClient.Builder, config: com.answufeng.net.http.config.NetworkConfig, logger: NetLogger) {
        if (config.cacheDir == null || config.cacheSize == null || config.cacheSize <= 0) return
        try {
            builder.cache(Cache(config.cacheDir, config.cacheSize))
        } catch (e: Exception) {
            logger.e("NetworkModule", "OkHttp cache setup failed: ${config.cacheDir}", e)
        }
    }

    private fun configureCertificatePinning(builder: OkHttpClient.Builder, config: com.answufeng.net.http.config.NetworkConfig) {
        if (config.certificatePins.isEmpty()) return
        val pinnerBuilder = CertificatePinner.Builder()
        config.certificatePins.forEach { certPin ->
            certPin.pins.forEach { pin ->
                pinnerBuilder.add(certPin.pattern, pin)
            }
        }
        builder.certificatePinner(pinnerBuilder.build())
    }

    private fun configureCookieJar(builder: OkHttpClient.Builder, config: com.answufeng.net.http.config.NetworkConfig) {
        config.cookieJar?.let { builder.cookieJar(it) }
    }

    /**
     * 若应用通过 Hilt 提供了 [NetTrackerApi] 实现，则写入 [NetTracker.delegate]（推荐方式）。
     * 未提供时**不**清空已有 [NetTracker.delegate]，以便无 Hilt 的测试或手动赋值仍可用。
     */
    @Provides
    @Singleton
    fun provideNetTrackerDelegate(trackerOptional: Optional<NetTrackerApi>): NetTrackerApi? {
        val tracker = trackerOptional.orElse(null)
        if (tracker != null) {
            NetTracker.delegate = tracker
        }
        return tracker
    }
}
