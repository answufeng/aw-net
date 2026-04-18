package com.answufeng.net.http.model

import com.answufeng.net.http.interceptor.SuccessCodeInterceptor

data class GlobalResponse<T>(
    override val code: Int,
    override val msg: String,
    override val data: T?
) : BaseResponse<T> {

    val resolvedSuccessCode: Int?
        get() = SuccessCodeInterceptor.getAndClearSuccessCode()
}
