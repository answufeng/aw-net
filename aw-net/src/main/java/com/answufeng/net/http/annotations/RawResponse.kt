package com.answufeng.net.http.annotations

/**
 * 标记 Retrofit 方法返回**裸 JSON**（无 `{code,msg,data}` 等业务包装），
 * 由 Gson 直接反序列化为声明类型。
 *
 * 第三方 API、文件元数据等场景使用；业务包装接口请勿添加本注解。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RawResponse
