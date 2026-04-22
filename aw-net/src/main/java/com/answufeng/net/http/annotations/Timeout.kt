package com.answufeng.net.http.annotations

import java.util.concurrent.TimeUnit

/**
 * 标注在 **单个** Retrofit 方法上，仅对该次 [okhttp3.Interceptor.Chain] 改连接/读/写超时时长（[com.answufeng.net.http.interceptor.DynamicTimeoutInterceptor]）。
 *
 * 与 [com.answufeng.net.http.config.NetworkConfig] 里「**构建 OkHttpClient 时**的全局秒数」是两套东西：改全局配置不会重配已存在的 Client；**仅**有本注解、且对应项 `> 0` 的维度会覆盖链上超时。未标或 ≤0 的项继续沿用 [okhttp3.OkHttpClient] 的当前值。时间单位由 [unit] 指定，默认 [java.util.concurrent.TimeUnit.SECONDS]。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Timeout(
    val connect: Int = -1,
    val read: Int = -1,
    val write: Int = -1,
    val unit: TimeUnit = TimeUnit.SECONDS
)
