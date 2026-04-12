package com.answufeng.net.http.di

import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.annotations.AppInterceptor
import com.answufeng.net.http.annotations.INetLogger
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor

@Module
@InstallIn(SingletonComponent::class)
abstract class OptionalBindingsModule {

    // 可选扩展点：项目层可按需提供任意子集
    // 最小必需初始化仅剩 NetworkConfig

    @BindsOptionalOf
    abstract fun optionalTokenProvider(): TokenProvider

    @BindsOptionalOf
    abstract fun optionalNetLogger(): INetLogger

    @BindsOptionalOf
    @AppInterceptor
    abstract fun optionalAppInterceptors(): Map<Int, Interceptor>

    @BindsOptionalOf
    abstract fun optionalUnauthorizedHandler(): UnauthorizedHandler
}
