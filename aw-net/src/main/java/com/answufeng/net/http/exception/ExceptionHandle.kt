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
 */
object ExceptionHandle {

    /**
     * 将异常转换为技术层面的 BaseNetException
     */
    fun handleException(e: Throwable): BaseNetException {
        return when (e) {
            // 已经是基础库定义的异常，直接返回
            is BaseNetException -> e

            is SocketTimeoutException -> {
                val code = NetCode.Tech.TIMEOUT
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络连接超时"),
                    cause = e
                )
            }
            is ConnectException, is UnknownHostException -> {
                val code = NetCode.Tech.NO_NETWORK
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络连接异常，请检查网络"),
                    cause = e
                )
            }
            is SSLException -> {
                val code = NetCode.Tech.SSL_ERROR
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "SSL 证书校验失败"),
                    cause = e
                )
            }
            is java.util.concurrent.CancellationException -> {
                val code = NetCode.Tech.REQUEST_CANCELED
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "请求已取消"),
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
                    message = NetErrorMessage.msg(NetCode.Tech.PARSE_ERROR, "数据解析异常，请检查数据结构"),
                    cause = e
                )
            }
            is ClassCastException -> {
                ParseException(
                    message = NetErrorMessage.msg(NetCode.Tech.PARSE_ERROR, "类型转换异常"),
                    cause = e
                )
            }

            is java.io.IOException -> {
                val code = NetCode.Tech.NO_NETWORK
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络 IO 异常：${e.message}"),
                    cause = e
                )
            }

            else -> {
                val code = NetCode.Tech.UNKNOWN
                UnknownNetException(
                    message = NetErrorMessage.msg(code, e.message ?: "未知网络错误"),
                    cause = e
                )
            }
        }
    }
}
