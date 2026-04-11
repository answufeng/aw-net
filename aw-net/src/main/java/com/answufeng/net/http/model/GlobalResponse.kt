package com.answufeng.net.http.model

/**
 * 库内推荐的统一响应模型。
 *
 * 搭配 ResponseFieldMapping 可全局兼容不同后端字段风格，
 * 接口层统一返回 GlobalResponse<T> 即可。
 */
data class GlobalResponse<T>(
    override val code: Int,
    override val msg: String,
    override val data: T?
) : IBaseResponse<T>

