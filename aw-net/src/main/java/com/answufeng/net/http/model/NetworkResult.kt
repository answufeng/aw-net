package com.answufeng.net.http.model

import com.answufeng.net.http.exception.BaseNetException

/**
 * 基础库对外输出的统一结果包装
 * T 代表解析后的实体对象类型
 */
sealed class NetworkResult<out T> {

    /**
     * 请求且解析成功，且业务 code 等于配置的 successCode
     */
    data class Success<out T>(val data: T?) : NetworkResult<T>()

    /**
     * 技术性失败：网络错误、服务器响应错误、解析错误
     */
    data class TechnicalFailure(val exception: BaseNetException) : NetworkResult<Nothing>()

    /**
     * 业务性失败：response.code != 配置的 successCode
     */
    data class BusinessFailure(val code: Int, val msg: String) : NetworkResult<Nothing>()
}
