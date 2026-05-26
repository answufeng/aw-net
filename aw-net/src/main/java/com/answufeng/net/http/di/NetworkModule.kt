package com.answufeng.net.http.di

import com.answufeng.net.http.annotations.AppInterceptor
import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.interceptor.RequestExtraHeadersInterceptor
import com.answufeng.net.http.logging.NetLogger
import com.answufeng.net.http.util.ConverterFactoryProvider
import com.answufeng.net.http.util.GsonFactory
import com.answufeng.net.http.util.NetEventDispatcher
import com.answufeng.net.http.util.NetworkClientFactory
import com.answufeng.net.http.util.NoOpNetLogger
import com.answufeng.net.http.util.OkHttpClientConfigurer
import com.answufeng.net.http.util.getOrNull
import com.answufeng.net.http.util.orDefault
import com.answufeng.net.http.util.withRequestCallContextInjection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.Optional
import javax.inject.Singleton
import com.answufeng.net.http.tracking.NetTracker as NetTrackerApi

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
     * 提供全局共享的 OkHttpClient 实例。
     *
     * 构建时从 [NetworkConfigProvider.current] 读取连接超时、读/写超时、连接池、重试/鉴权/缓存/钉扎等**一次性**写入选项；
     * 之后仅通过 [NetworkConfigProvider] 再改**全局**的 connect/read/write 秒数、连接池参数等，**不会**让本单例
     * [OkHttpClient] 自动重配，除非应用自行在模块里提供可重建的 Client 工厂。运行时切环境/换请求级超时请依赖
     * [com.answufeng.net.http.interceptor.DynamicBaseUrlInterceptor] 与 [com.answufeng.net.http.interceptor.DynamicTimeoutInterceptor] 等，详见 [com.answufeng.net.http.config.NetworkConfig] 文档。
     *
     * 拦截器执行顺序（应用拦截器）：
     * 0. AuthHeaderInterceptor（若提供 TokenProvider）：自动附加 Authorization
     * 1. DynamicBaseUrlInterceptor：尽早确定最终 host/schema/port
     * 2. DynamicTimeoutInterceptor：基于注解覆写本次请求的超时配置
     * 3. SuccessCodeInterceptor：为 SuccessCode 注解写入 request tag
     * 4. ExtraHeadersInterceptor：补齐通用 Header
     * 5. RequestExtraHeadersInterceptor：注入请求级额外 Header（来自 RequestOption.extraHeaders）
     * 6. 自定义拦截器：项目层按 key 排序后插入
     * 7. 日志拦截器：最后一环，打印最终请求信息
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        configProvider: NetworkConfigProvider,
        netLoggerOptional: Optional<NetLogger>,
        @AppInterceptor optionalCustomInterceptors: Optional<Map<Int, @JvmSuppressWildcards Interceptor>>,
        coordinator: TokenRefreshCoordinator?,
        unauthorizedHandlerOptional: Optional<UnauthorizedHandler>,
        requestExtraHeadersInterceptor: RequestExtraHeadersInterceptor,
        tokenProviderOptional: Optional<TokenProvider>,
    ): OkHttpClient {
        val config = configProvider.current
        val netLogger = netLoggerOptional.orDefault(NoOpNetLogger)
        val customInterceptors = optionalCustomInterceptors.orDefault(emptyMap())
        val sortedInterceptors = customInterceptors.toSortedMap().values.toList()

        val builder =
            OkHttpClientConfigurer.configureBuilder(
                OkHttpClient.Builder(),
                configProvider,
                config,
                netLogger,
                requestExtraHeadersInterceptor,
                sortedInterceptors,
                coordinator,
                unauthorizedHandlerOptional.orElse(null),
                tokenProviderOptional.orElse(null),
            )

        return builder.build().withRequestCallContextInjection()
    }

    /**
     * 提供全局 Retrofit 实例。
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        factory: NetworkClientFactory,
        configProvider: NetworkConfigProvider,
    ): Retrofit {
        val config = configProvider.current
        return factory.createRetrofit(config.baseUrl)
    }

    /**
     * 默认的 Retrofit 工厂实现：复用全局 OkHttpClient + [ConverterFactoryProvider]。
     * 如需多 Retrofit 实例，项目层可以自行注入自定义实现覆盖此工厂。
     */
    @Provides
    @Singleton
    fun provideNetworkClientFactory(
        client: OkHttpClient,
        configProvider: NetworkConfigProvider,
        converterFactoryProvider: ConverterFactoryProvider,
    ): NetworkClientFactory {
        return object : NetworkClientFactory {
            override fun createRetrofit(baseUrl: String): Retrofit {
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(converterFactoryProvider.provide())
                    .build()
            }
        }
    }

    /**
     * 默认的 [ConverterFactoryProvider]，使用 Gson + [GlobalResponseTypeAdapterFactory]。
     * 项目层可注入自定义实现以替换为 Moshi、Kotlin Serialization 等。
     */
    @Provides
    @Singleton
    fun provideConverterFactoryProvider(configProvider: NetworkConfigProvider): ConverterFactoryProvider {
        return ConverterFactoryProvider { GsonFactory.createConverterFactory(configProvider) }
    }

    @Provides
    @Singleton
    fun provideTokenRefreshCoordinator(
        tokenProvider: Optional<TokenProvider>,
        netLoggerOptional: Optional<NetLogger>,
        configProvider: NetworkConfigProvider,
    ): TokenRefreshCoordinator? {
        val tp = tokenProvider.getOrNull() ?: return null
        val logger = netLoggerOptional.orDefault(NoOpNetLogger)
        return TokenRefreshCoordinator(
            tokenProvider = tp,
            lockAcquireTimeoutMs = configProvider.current.tokenRefreshLockAcquireTimeoutMs,
            logger = logger,
        )
    }

    @Provides
    @Singleton
    fun provideNetTrackerDelegate(trackerOptional: Optional<NetTrackerApi>): NetTrackerApi? {
        val tracker = trackerOptional.orElse(null)
        if (tracker != null) {
            NetEventDispatcher.delegate = tracker
        }
        return tracker
    }

    @Provides
    @Singleton
    fun provideRequestExtraHeadersInterceptor(): RequestExtraHeadersInterceptor {
        return RequestExtraHeadersInterceptor()
    }
}
