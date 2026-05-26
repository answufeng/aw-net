package com.answufeng.net.http.model

import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.util.ResponseSuccessCodeResolver

fun <T> BaseResponse<T>.toNetworkResult(
    successCode: Int?,
    configProvider: NetworkConfigProvider,
    requestSuccessCode: Int? = null,
): NetworkResult<T> {
    val effectiveSuccessCode =
        ResponseSuccessCodeResolver.resolve(
            explicitCode = successCode,
            configProvider = configProvider,
            requestSuccessCode = requestSuccessCode,
        )
    return if (code == effectiveSuccessCode) {
        NetworkResult.Success(data)
    } else {
        NetworkResult.BusinessFailure(code, msg)
    }
}
