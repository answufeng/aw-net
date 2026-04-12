package com.answufeng.net.http.exception

import com.answufeng.net.http.model.NetCode
import com.answufeng.net.http.util.NetErrorMessage
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import org.json.JSONException
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 异常处理转换器
 * 职责：将各种原始 Throwable 转换为基础库定义的 [BaseNetException]
 *
 * 说明：
 * - 推荐上层使用 NetworkResult.BusinessFailure 表达业务失败场景；
 * - 若业务代码显式抛出 [BusinessFailureException]，会在上层 as BaseNetException 路径返回。
 * - 调用方应在调用 [handleException] 前先捕获 kotlinx.coroutines.CancellationException 并重新抛出，
 *   以保证协程取消语义的正确传播（库内所有 Executor 已遵循此约定）。
 * @since 1.0.0
 */
object ExceptionHandle {

    /**
     * 将异常转换为技术层面的 BaseNetException。
     *
     * 注意：调用方应在调用此方法前先捕获 [kotlinx.coroutines.CancellationException] 并重新抛出，
     * 以保证协程取消语义的正确传播。示例：
     * ```
     * try {
     *     ...
     * } catch (e: CancellationException) {
     *     throw e
     * } catch (e: Exception) {
     *     ExceptionHandle.handleException(e)
     * }
     * ```
     * @param e 原始异常
     * @return 转换后的 BaseNetException
     * @since 1.0.0
$     */
    fun handleException(e: Throwable): BaseNetException {
        return when (e) {
            is BaseNetException -> e

            is SocketTimeoutException -> {
                val code = NetCode.Technical.TIMEOUT
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络连接超时"),
                    cause = e
                )
            }
            is ConnectException, is UnknownHostException -> {
                val code = NetCode.Technical.NO_NETWORK
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络连接异常，请检查网络"),
                    cause = e
                )
            }
            is SSLException -> {
                val code = NetCode.Technical.SSL_ERROR
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "SSL 证书校验失败"),
                    cause = e
                )
            }
            is java.util.concurrent.CancellationException -> {
                val code = NetCode.Technical.REQUEST_CANCELED
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "请求已取消（非协程取消）"),
                    cause = e
                )
            }

            is HttpException -> {
                val code = e.code()
                ServerException(
                    code = code,
                    message = NetErrorMessage.msg(code, "服务器响应错误(${e.code()})")
                )
            }

            is JsonParseException, is JsonSyntaxException, is JSONException -> {
                ParseException(
                    message = NetErrorMessage.msg(NetCode.Technical.PARSE_ERROR, "数据解析异常，请检查数据结构"),
                    cause = e
                )
            }
            is ClassCastException -> {
                ParseException(
                    message = NetErrorMessage.msg(NetCode.Technical.PARSE_ERROR, "类型转换异常"),
                    cause = e
                )
            }

            is java.io.IOException -> {
                val code = NetCode.Technical.NO_NETWORK
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络 IO 异常：${e.message}"),
                    cause = e
                )
            }

            else -> {
                val code = NetCode.Technical.UNKNOWN
                UnknownNetException(
                    message = NetErrorMessage.msg(code, e.message ?: "未知网络错误"),
                    cause = e
                )
            }
        }
    }
}
