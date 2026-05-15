package com.answufeng.net.http.util

import retrofit2.Converter

/**
 * Converter.Factory 提供器接口，用于抽离 Gson 依赖。
 *
 * 默认实现使用 Gson，项目层可注入自定义实现以替换为 Moshi、Kotlin Serialization 等。
 *
 * 示例：
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object MoshiModule {
 *     @Provides
 *     @Singleton
 *     fun provideConverterFactoryProvider(): ConverterFactoryProvider {
 *         return ConverterFactoryProvider { moshiConverterFactory }
 *     }
 * }
 * ```
 */
fun interface ConverterFactoryProvider {
    fun provide(): Converter.Factory
}
