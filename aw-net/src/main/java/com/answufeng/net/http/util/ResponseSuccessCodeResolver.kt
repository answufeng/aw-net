package com.answufeng.net.http.util

import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.interceptor.SuccessCodeInterceptor
import com.answufeng.net.http.model.ResponseFieldMapping
import okhttp3.Request

/**
 * 业务成功码解析优先级：
 * explicit（[com.answufeng.net.http.model.RequestOption.successCode]）>
 * requestContext（[RequestCallContext.successCode]）>
 * annotation（[@com.answufeng.net.http.annotations.SuccessCode]）>
 * [SuccessCodeInterceptor.SuccessCodeTag] on [Request] >
 * [ResponseFieldMapping.successCode] >
 * [NetworkConfig.defaultSuccessCode]
 */
internal object ResponseSuccessCodeResolver {
    fun resolve(
        explicitCode: Int?,
        configProvider: NetworkConfigProvider,
        mapping: ResponseFieldMapping = configProvider.current.responseFieldMapping,
        requestSuccessCode: Int? = null,
        annotationSuccessCode: Int? = null,
        okhttpRequest: Request? = null,
    ): Int {
        if (explicitCode != null) return explicitCode
        if (requestSuccessCode != null) return requestSuccessCode
        if (annotationSuccessCode != null) return annotationSuccessCode
        okhttpRequest?.tag(SuccessCodeInterceptor.SuccessCodeTag::class.java)?.code?.let { return it }
        return configProvider.current.defaultSuccessCode
    }
}
