package com.answufeng.net.http.util

import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.RequestOption

/**
 * 执行业务包装接口：`onSuccess` 收到的是 **`data`**（不是整包 [com.answufeng.net.http.model.GlobalResponse]）。
 *
 * - `GlobalResponse<String>` → `String?`（如「密码修改成功」）
 * - `GlobalResponse<OcrResult>` → `OcrResult?`（内嵌 JSON 字符串由 Gson 自动再解析，需 aw-net ≥ 1.0.4）
 *
 * 后端 `code` 为 `200` 时请在 [RequestOption] 中设置 `successCode = 200`，或配置 [com.answufeng.net.http.config.NetworkConfig.defaultSuccessCode]。
 */
suspend fun <T> NetworkExecutor.executeDataRequest(
    option: RequestOption = RequestOption.DEFAULT,
    call: suspend () -> BaseResponse<T>,
): NetworkResult<T> = executeRequest(option, call)
