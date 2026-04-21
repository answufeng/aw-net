package com.answufeng.net.http.model

import com.answufeng.net.http.interceptor.SuccessCodeInterceptor

/**
 * [BaseResponse] 的默认实现，使用标准的 `code` / `msg` / `data` 字段名。
 *
 * 大多数后端接口可直接使用此类作为 Retrofit 接口返回值。
 * 若后端字段名不同（如 `status` / `message` / `result`），请通过
 * [NetworkConfig.responseFieldMapping] 配置映射，或自行实现 [BaseResponse]。
 *
 * @param T 业务数据类型，由 Gson 根据泛型自动反序列化
 */
data class GlobalResponse<T>(
    override val code: Int,
    override val msg: String,
    override val data: T?
) : BaseResponse<T> {

    /**
     * 从请求 tag 中解析本次请求的成功码（由 [SuccessCodeInterceptor] 注入）。
     * 若接口标注了 `@SuccessCode`，则返回该值；否则为 null。
     */
    val resolvedSuccessCode: Int?
        get() = rawResponse?.raw()?.request?.tag(SuccessCodeInterceptor.SuccessCodeTag::class.java)?.code

    /**
     * 内部字段，用于在拦截器链中传递原始响应对象。
     * 调用方不应直接修改此字段。
     */
    @Transient
    internal var rawResponse: retrofit2.Response<*>? = null
}
