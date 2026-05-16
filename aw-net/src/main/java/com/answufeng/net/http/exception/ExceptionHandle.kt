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
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLException

/**
 * 网络异常处理器，将各种 [Throwable] 映射为 [BaseNetException]。
 *
 * 项目层可通过注册自定义映射器来扩展异常处理逻辑，例如增加对 `SocketException`、
 * `NoRouteToHostException` 等细分类型的处理。自定义映射器优先于内置映射执行。
 *
 * 示例：
 * ```kotlin
 * ExceptionHandle.register { e ->
 *     when (e) {
 *         is java.net.NoRouteToHostException -> RequestException(
 *             code = NetCode.Technical.NO_NETWORK,
 *             message = "无法路由到主机",
 *             cause = e
 *         )
 *         else -> null
 *     }
 * }
 * ```
 */
object ExceptionHandle {
    private val customMappers = CopyOnWriteArrayList<(Throwable) -> BaseNetException?>()

    fun handleException(e: Throwable): BaseNetException {
        for (mapper in customMappers) {
            mapper(e)?.let { return it }
        }
        return defaultHandle(e)
    }

    fun register(mapper: (Throwable) -> BaseNetException?) {
        customMappers.add(mapper)
    }

    fun clearCustomMappers() {
        customMappers.clear()
    }

    private fun defaultHandle(e: Throwable): BaseNetException {
        return when (e) {
            is BaseNetException -> e

            is SocketTimeoutException -> {
                val code = NetCode.Technical.TIMEOUT
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络连接超时"),
                    cause = e,
                )
            }
            is ConnectException, is UnknownHostException -> {
                val code = NetCode.Technical.NO_NETWORK
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络连接异常，请检查网络"),
                    cause = e,
                )
            }
            is SSLException -> {
                val code = NetCode.Technical.SSL_ERROR
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "SSL 证书校验失败"),
                    cause = e,
                )
            }

            is HttpException -> {
                val code = e.code()
                ServerException(
                    code = code,
                    message = NetErrorMessage.msg(code, "服务器响应错误(${e.code()})"),
                )
            }

            is JsonParseException, is JsonSyntaxException, is JSONException -> {
                ParseException(
                    message = NetErrorMessage.msg(NetCode.Technical.PARSE_ERROR, "数据解析异常，请检查数据结构"),
                    cause = e,
                )
            }
            is ClassCastException -> {
                ParseException(
                    message = NetErrorMessage.msg(NetCode.Technical.PARSE_ERROR, "类型转换异常"),
                    cause = e,
                )
            }

            is java.io.IOException -> {
                val code = NetCode.Technical.NO_NETWORK
                RequestException(
                    code = code,
                    message = NetErrorMessage.msg(code, "网络 IO 异常：${e.message}"),
                    cause = e,
                )
            }

            else -> {
                val code = NetCode.Technical.UNKNOWN
                UnknownNetException(
                    message = NetErrorMessage.msg(code, e.message ?: "未知网络错误"),
                    cause = e,
                )
            }
        }
    }
}
