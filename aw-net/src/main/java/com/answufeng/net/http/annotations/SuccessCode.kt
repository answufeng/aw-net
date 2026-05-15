package com.answufeng.net.http.annotations

/**
 * 指定**当前接口方法**的「业务成功码」，与 [com.answufeng.net.http.config.NetworkConfig.defaultSuccessCode] 可不同。
 *
 * [com.answufeng.net.http.interceptor.SuccessCodeInterceptor] 将其写入 [okhttp3.Request] tag。
 * 在协程层（[com.answufeng.net.http.util.RequestExecutor]），需通过 [com.answufeng.net.http.model.RequestOption.successCode] 显式传入；
 * 优先级：`RequestOption.successCode` > `@SuccessCode` > `NetworkConfig.defaultSuccessCode`。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuccessCode(val value: Int)
