package com.answufeng.net.http.model

data class GlobalResponse<T>(
    override val code: Int,
    override val msg: String,
    override val data: T?,
    val resolvedSuccessCode: Int? = null
) : IBaseResponse<T>
