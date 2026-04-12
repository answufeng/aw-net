package com.answufeng.net.http.annotations

import javax.inject.Qualifier

/**
 * 用于标记项目层注入的自定义拦截器
 * Key 为拦截器顺序优先级，数值越小越先执行
 * @since 1.0.0
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppInterceptor
