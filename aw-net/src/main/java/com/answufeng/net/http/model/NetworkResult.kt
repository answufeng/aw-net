package com.answufeng.net.http.model

import com.answufeng.net.http.exception.BaseNetException

/**
 * 一次 HTTP/调用路径对上层暴露的**三态**结果：成功、业务码不符、或网络/解析等技术失败。
 *
 * [Success] 携带的 `T` 为业务体类型；`data` 可为 `null`（例如仅表示成功、无 body 的删除/更新）。请用 [onSuccessNotNull] 或自行判空。
 */
sealed class NetworkResult<out T> {

    /**
     * 请求且解析成功，且业务 code 等于配置的 successCode。
     *
     * [data] 为 null 是合法的，表示业务成功但无返回数据（如删除/更新操作）。
     * 如需仅处理非 null 数据，请使用 `onSuccessNotNull` 扩展。
     */
    data class Success<out T>(val data: T?) : NetworkResult<T>()

    /**
     * 技术性失败：网络错误、服务器响应错误、解析错误。
     */
    data class TechnicalFailure(val exception: BaseNetException) : NetworkResult<Nothing>()

    /**
     * 业务性失败：response.code != 配置的 successCode。
     */
    data class BusinessFailure(val code: Int, val msg: String) : NetworkResult<Nothing>()
}
