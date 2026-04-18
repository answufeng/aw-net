package com.answufeng.net.http.di

import com.answufeng.net.http.annotations.AppInterceptor
import com.answufeng.net.http.annotations.NetLogger
import com.answufeng.net.http.annotations.NetTracker
import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.UnauthorizedHandler
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor

/**
 * Hilt 可选绑定模块。
 *
 * 声明项目层可按需提供的可选依赖，库内部通过 `Optional<T>` 注入。
 * 项目层只需提供实际需要的子集，无需全部实现。
 *
 * 可选扩展点：
 * - [TokenProvider]：Token 管理，用于自动刷新
 * - [NetLogger]：自定义日志输出
 * - [Interceptor]（@AppInterceptor）：自定义应用拦截器
 * - [UnauthorizedHandler]：未授权回调
 * @since 1.0.0
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OptionalBindingsModule {

    // 可选扩展点：项目层可按需提供任意子集
    // 最小必需初始化仅剩 NetworkConfig

    @BindsOptionalOf
    abstract fun optionalTokenProvider(): TokenProvider

    @BindsOptionalOf
    abstract fun optionalNetLogger(): NetLogger

    @BindsOptionalOf
    @AppInterceptor
    abstract fun optionalAppInterceptors(): Map<Int, Interceptor>

    @BindsOptionalOf
    abstract fun optionalUnauthorizedHandler(): UnauthorizedHandler

    @BindsOptionalOf
    abstract fun optionalNetTracker(): NetTracker
}
