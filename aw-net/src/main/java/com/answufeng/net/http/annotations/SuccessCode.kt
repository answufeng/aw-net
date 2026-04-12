package com.answufeng.net.http.annotations

/**
 * 用于 Retrofit 接口方法的注解，指定该接口的业务成功码
 * 优先级高于 [NetworkConfig.defaultSuccessCode]
 * 仅当 response.code == 注解值时视为业务成功，返回 Success(data)
 * @since 1.0.0
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuccessCode(val value: Int)
