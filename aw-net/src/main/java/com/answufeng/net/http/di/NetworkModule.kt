package com.answufeng.net.http.di

import android.util.Log
import com.answufeng.net.http.annotations.AppInterceptor
import com.answufeng.net.http.annotations.INetLogger
import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.annotations.NetworkLogLevel
import com.answufeng.net.http.auth.TokenAuthenticator
import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.interceptor.DynamicBaseUrlInterceptor
import com.answufeng.net.http.interceptor.DynamicTimeoutInterceptor
import com.answufeng.net.http.interceptor.ExtraHeadersInterceptor
import com.answufeng.net.http.interceptor.PrettyNetLogger
import com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory
import com.answufeng.net.http.interceptor.DynamicRetryInterceptor
import com.answufeng.net.http.util.DefaultRetryStrategy
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
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Optional
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // 当项目层未提供 INetLogger 时的空实现兜底
    private val NOOP_INET_LOGGER: INetLogger = object : INetLogger {
        override fun d(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @Provides
    @Singleton
    @Suppress("NewApi") // Optional<T> 需要 API 24+；项目 minSdk=24
    fun provideOkHttpClient(
        configProvider: NetworkConfigProvider,
        netLoggerOptional: Optional<INetLogger>,
        @AppInterceptor optionalCustomInterceptors: Optional<Map<Int, @JvmSuppressWildcards Interceptor>>,
        tokenProvider: Optional<TokenProvider>,
        unauthorizedHandlerOptional: Optional<UnauthorizedHandler>
    ): OkHttpClient {
        val config = configProvider.current
        val netLogger = netLoggerOptional.orDefault(NOOP_INET_LOGGER)
        val customInterceptors = optionalCustomInterceptors.orDefault(emptyMap())

        val dynamicLoggingInterceptor = Interceptor { chain ->
            val currentConfig = configProvider.current
            val level = resolveHttpLogLevel(currentConfig)
            if (level == HttpLoggingInterceptor.Level.NONE) {
                chain.proceed(chain.request())
            } else {
                HttpLoggingInterceptor(PrettyNetLogger(netLogger, configProvider)).apply {
                    this.level = level
                }.intercept(chain)
            }
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout, TimeUnit.SECONDS)
            .readTimeout(config.readTimeout, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeout, TimeUnit.SECONDS)
            // 连接池：按 NetworkConfig 配置空闲连接数和存活时间
            .connectionPool(
                ConnectionPool(
                    config.maxIdleConnections,
                    config.keepAliveDurationSeconds,
                    TimeUnit.SECONDS
                )
            )
            // 拦截器执行顺序说明（应用拦截器）：
            // 1. 动态 BaseUrl：尽早确定最终 host/schema/port
            // 2. 动态超时：基于注解覆写本次请求的超时配置
            // 3. 公共头：在 URL/超时都确定之后，补齐通用 Header
            // 4. 自定义拦截器：项目层按 key 排序后插入
            // 5. 日志拦截器：最后一环，打印最终请求信息
            .addInterceptor(DynamicBaseUrlInterceptor())
            .addInterceptor(DynamicTimeoutInterceptor())
            .addInterceptor(ExtraHeadersInterceptor(configProvider))

        customInterceptors.toSortedMap().forEach { (_, interceptor) ->
            builder.addInterceptor(interceptor)
        }

        builder.addInterceptor(dynamicLoggingInterceptor)

        // 可选：按配置添加动态重试拦截器
        // DynamicRetryInterceptor 支持通过 @Retry 注解实现按接口重试配置
        if (config.enableRetryInterceptor) {
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
                Log.w("NetworkModule", "DynamicRetryInterceptor setup failed, retry disabled", e)
            }
        }

        // 如果项目层提供了 TokenProvider，注册 TokenAuthenticator（可选行为）
        val providedTokenProvider = tokenProvider.getOrNull()
        if (providedTokenProvider != null) {
            try {
                val handler = unauthorizedHandlerOptional.getOrNull()
                builder.authenticator(TokenAuthenticator(providedTokenProvider, unauthorizedHandler = handler))
            } catch (t: Throwable) {
                Log.w("NetworkModule", "TokenAuthenticator setup failed, auto-refresh disabled", t)
            }
        }

        // 如果提供了 cacheDir 和 cacheSize，启用 OkHttp 缓存
        if (config.cacheDir != null && config.cacheSize != null && config.cacheSize > 0) {
            try {
                builder.cache(Cache(config.cacheDir, config.cacheSize))
            } catch (e: Exception) {
                Log.w("NetworkModule", "OkHttp cache setup failed: ${config.cacheDir}", e)
            }
        }

        // SSL 证书固定：如果配置了 certificatePins，构建 CertificatePinner
        if (config.certificatePins.isNotEmpty()) {
            try {
                val pinnerBuilder = CertificatePinner.Builder()
                config.certificatePins.forEach { certPin ->
                    certPin.pins.forEach { pin ->
                        pinnerBuilder.add(certPin.pattern, pin)
                    }
                }
                builder.certificatePinner(pinnerBuilder.build())
            } catch (e: Exception) {
                Log.w("NetworkModule", "CertificatePinner setup failed, pinning disabled", e)
            }
        }

        return builder.build()
    }

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
     * @since 1.0.0
 */    @Provides
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

    private fun resolveHttpLogLevel(config: com.answufeng.net.http.annotations.NetworkConfig): HttpLoggingInterceptor.Level {
        return when (config.networkLogLevel) {
            NetworkLogLevel.AUTO -> if (config.isLogEnabled) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            NetworkLogLevel.NONE -> HttpLoggingInterceptor.Level.NONE
            NetworkLogLevel.BASIC -> HttpLoggingInterceptor.Level.BASIC
            NetworkLogLevel.HEADERS -> HttpLoggingInterceptor.Level.HEADERS
            NetworkLogLevel.BODY -> HttpLoggingInterceptor.Level.BODY
        }
    }
}
