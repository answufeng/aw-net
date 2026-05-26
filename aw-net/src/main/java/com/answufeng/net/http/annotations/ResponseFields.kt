package com.answufeng.net.http.annotations

/**
 * 为**当前 Retrofit 方法**指定响应 JSON 字段名（覆盖 [com.answufeng.net.http.config.NetworkConfig.responseFieldMapping]）。
 *
 * 示例：`{status, message, data}` 或 `{errCode, errMsg, payload}`。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ResponseFields(
    val codeKey: String = "",
    val msgKey: String = "",
    val dataKey: String = "",
    val successCode: Int = Int.MIN_VALUE,
)
