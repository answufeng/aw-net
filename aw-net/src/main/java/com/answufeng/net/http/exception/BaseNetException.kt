package com.answufeng.net.http.exception

import com.answufeng.net.http.model.NetCode

/**
 * 基础库统一异常基类
 * 仅包含技术层面的错误描述，不涉及业务语义
 */
sealed class BaseNetException(
    val code: Int,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * 网络层异常：如断网、DNS 解析失败、连接超时等
 */
class RequestException(code: Int, message: String, cause: Throwable? = null) :
    BaseNetException(code, message, cause)

/**
 * 协议层异常：HTTP 状态码非 2xx（如 404, 500, 502 等）
 */
class ServerException(code: Int, message: String) :
    BaseNetException(code, message)

/**
 * 解析层异常：JSON 格式错误、字段类型不匹配、Null 安全校验失败等
 */
class ParseException(message: String, cause: Throwable? = null) :
    BaseNetException(NetCode.Tech.PARSE_ERROR, message, cause)

/**
 * 业务码非成功：response.code != 配置的 successCode
 * 仅携带 code/msg，无业务含义。
 * 业务码和消息可通过父类 [code] 和 [message] 获取。
 */
class BusinessFailureException(businessCode: Int, businessMsg: String) :
    BaseNetException(businessCode, businessMsg)

/**
 * 其他未知异常
 */
class UnknownNetException(message: String, cause: Throwable? = null) :
    BaseNetException(NetCode.Tech.UNKNOWN, message, cause)
