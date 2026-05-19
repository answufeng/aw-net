package com.answufeng.net.http.model

import com.answufeng.net.http.config.NetworkConfigProvider

/**
 * 若 [response] 为 [BaseResponse]，按业务码拆出 `data`；否则视为原始响应体。
 */
internal fun <T> unwrapResponseOrRaw(
    response: T,
    successCode: Int?,
    configProvider: NetworkConfigProvider,
): NetworkResult<*> {
    if (response is BaseResponse<*>) {
        @Suppress("UNCHECKED_CAST")
        return (response as BaseResponse<Any?>).toNetworkResult(successCode, configProvider)
    }
    return NetworkResult.Success(response)
}
