package com.answufeng.net.http.annotations

/**
 * 指定**当前接口方法**的「业务成功码」，与 [com.answufeng.net.http.config.NetworkConfig.defaultSuccessCode] 可不同。
 * [com.answufeng.net.http.interceptor.SuccessCodeInterceptor] 将其写入 [okhttp3.Request] tag，供 [com.answufeng.net.http.model.GlobalResponse] 解析时读取；与 [com.answufeng.net.http.util.RequestExecutor] 显式传入成功码的优先级见 README「按接口成功码」。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuccessCode(val value: Int)
