package com.answufeng.net.http.annotations

import java.util.concurrent.TimeUnit

/**
 * 用于 Retrofit 接口方法的注解，动态设置该请求的超时时间
 * 优先级高于 [NetworkConfig] 中的全局配置
 * 单位默认为秒，未设置的项使用全局或默认配置
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Timeout(
    val connect: Int = -1,
    val read: Int = -1,
    val write: Int = -1,
    val unit: TimeUnit = TimeUnit.SECONDS
)
