package com.answufeng.net.http.annotations

/**
 * 标注在 **单个** Retrofit 方法上，将本次请求的基地址切换为 [value]（在 [com.answufeng.net.http.interceptor.DynamicBaseUrlInterceptor] 中解析）。
 *
 * 优先级高于 [com.answufeng.net.http.config.NetworkConfigProvider] 中当前的 [com.answufeng.net.http.config.NetworkConfig.baseUrl]；`value` 须为可解析的 http(s) 基址（习惯上与全局 baseUrl 一样带尾 `/`），用于 CDN/网关等分域名场景。
 */@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BaseUrl(val value: String)
