package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetLogger

/**
 * 无操作 [NetLogger]，用于 Kotlin 项目在 Hilt 中直接 `@Provides` 默认实现，
 * 避免依赖 `java.util.Optional` 的可选注入模式。
 *
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object AppNetLoggerModule {
 *     @Provides
 *     @Singleton
 *     fun provideNetLogger(): NetLogger = NoOpNetLogger
 * }
 * ```
 */
object NoOpNetLogger : NetLogger {
    override fun d(tag: String, msg: String) = Unit

    override fun e(tag: String, msg: String, throwable: Throwable?) = Unit
}
