package com.answufeng.net.http.annotations

/**
 * 用于 Retrofit 接口方法的注解，指定该接口的 BaseUrl
 * 优先级高于 [com.answufeng.net.http.annotations.NetworkConfig.baseUrl]
 * 用于应对特殊接口使用不同域名的情况
 * @since 1.0.0
 */@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BaseUrl(val value: String)
