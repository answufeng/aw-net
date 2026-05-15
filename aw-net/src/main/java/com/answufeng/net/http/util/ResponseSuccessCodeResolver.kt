package com.answufeng.net.http.util

import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.model.BaseResponse

internal object ResponseSuccessCodeResolver {
    fun resolve(
        explicitCode: Int?,
        response: BaseResponse<*>,
        configProvider: NetworkConfigProvider,
    ): Int {
        if (explicitCode != null) return explicitCode
        return configProvider.current.defaultSuccessCode
    }
}
