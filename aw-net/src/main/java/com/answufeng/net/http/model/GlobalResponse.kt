package com.answufeng.net.http.model

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
    override val data: T?,
) : BaseResponse<T>
