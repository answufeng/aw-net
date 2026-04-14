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

object ExceptionHandle {

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
