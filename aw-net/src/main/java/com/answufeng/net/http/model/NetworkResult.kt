package com.answufeng.net.http.model

import com.answufeng.net.http.exception.BaseNetException

/**
 * 基础库对外输出的统一结果包装
 *
 * T 表示服务端返回的业务数据类型。
 *
 * 注意：[Success.data] 可能为 null，这通常发生在服务端返回成功状态但无数据体的场景
 * （如删除操作、更新操作等）。调用者应使用 [onSuccessNotNull] 扩展函数来安全处理非 null 数据，
 * 或使用 [onSuccess] 并在回调中手动判空。
 * @since 1.0.0
 */
sealed class NetworkResult<out T> {

    /**
     * 请求且解析成功，且业务 code 等于配置的 successCode。
     *
     * [data] 为 null 是合法的，表示业务成功但无返回数据（如删除/更新操作）。
     * 如需仅处理非 null 数据，请使用 `onSuccessNotNull` 扩展。
     * @since 1.0.0
 */
    data class Success<out T>(val data: T?) : NetworkResult<T>()

    /**
     * 技术性失败：网络错误、服务器响应错误、解析错误
     * @since 1.0.0
 */
    data class TechnicalFailure(val exception: BaseNetException) : NetworkResult<Nothing>()

    /**
     * 业务性失败：response.code != 配置的 successCode
     * @since 1.0.0
 */
    data class BusinessFailure(val code: Int, val msg: String) : NetworkResult<Nothing>()
}
