package com.answufeng.net.http.annotations

/**
 * 指定**当前接口方法**的「业务成功码」，与 [com.answufeng.net.http.config.NetworkConfig.defaultSuccessCode] 可不同。
 *
 * [com.answufeng.net.http.interceptor.SuccessCodeInterceptor] 将其写入 [okhttp3.Request] tag。
 * 优先级：`RequestOption.successCode` > `@SuccessCode` > `ResponseFieldMapping.successCode` / `NetworkConfig.defaultSuccessCode`。
 * 对 `suspend fun (): T?` 接口，[com.answufeng.net.http.converter.UnwrapResponseConverterFactory] 会读取本注解。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuccessCode(val value: Int)
