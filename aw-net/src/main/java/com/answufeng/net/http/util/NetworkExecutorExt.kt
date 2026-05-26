package com.answufeng.net.http.util

import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.RequestOption

/**
 * 执行业务包装接口（进阶）：Retrofit 仍返回 [com.answufeng.net.http.model.GlobalResponse] 时使用。
 *
 * 常规项目请使用 [NetworkExecutor.execute] + `suspend fun api(): T?`。
 */
@Deprecated(
    message = "Use NetworkExecutor.execute { } with suspend fun (): T? instead",
    replaceWith = ReplaceWith("execute(option, call)", "com.answufeng.net.http.util.NetworkExecutor"),
)
suspend fun <T> NetworkExecutor.executeDataRequest(
    option: RequestOption = RequestOption.DEFAULT,
    call: suspend () -> BaseResponse<T>,
): NetworkResult<T> = executeRequest(option, call)
