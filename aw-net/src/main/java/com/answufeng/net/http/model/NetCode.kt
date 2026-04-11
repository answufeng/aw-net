package com.answufeng.net.http.model

/**
 * 统一网络状态码常量，避免散落的魔法数字。
 *
 * - [Biz]：业务层状态码
 * - [Tech]：技术层错误码（负数，与后端无关）
 */
object NetCode {

    /** 业务层状态码 */
    object Biz {
        /** 请求成功 */
        const val SUCCESS = 0
        /** 未授权（Token 失效） */
        const val UNAUTHORIZED = 401
        /** 禁止访问 */
        const val FORBIDDEN = 403
        /** 资源不存在 */
        const val NOT_FOUND = 404
    }

    /** 技术层错误码（由客户端定义，全部为负值） */
    object Tech {
        /** 连接/读取/写入超时 */
        const val TIMEOUT = -1
        /** 无网络连接 */
        const val NO_NETWORK = -2
        /** SSL 握手失败 */
        const val SSL_ERROR = -3
        /** 请求已取消（协程 Job 取消） */
        const val REQUEST_CANCELED = -999
        /** 未知错误 */
        const val UNKNOWN = -1000
        /** JSON 解析失败 */
        const val PARSE_ERROR = -1001
    }
}

