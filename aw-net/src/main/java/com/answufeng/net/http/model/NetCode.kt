package com.answufeng.net.http.model

/**
 * 统一网络状态码常量，避免散落的魔法数字。
 *
 * - [Biz]：业务层状态码（注意：以下常量值与 HTTP 状态码相同，但语义不同。
 *   HTTP 401 由 OkHttp [okhttp3.Authenticator] 处理，业务 401 由 [com.answufeng.net.http.util.RequestExecutor] 处理）
 * - [Tech]：技术层错误码（负数，由客户端定义，与后端无关）
 * @since 1.0.0
 */object NetCode {

    /**
     * 业务层状态码。
     *
     * 注意：这些常量的值恰好与常见 HTTP 状态码相同，但它们代表的是**业务层**返回的 code 字段，
     * 而非 HTTP 响应状态码。HTTP 层的 401 由 [okhttp3.Authenticator]（TokenAuthenticator）处理，
     * 业务层的 401 由 [com.answufeng.net.http.util.RequestExecutor] 处理。
     * @since 1.0.0
 */    object Biz {
        /** 请求成功 
        * @since 1.0.0
 */        const val SUCCESS = 0
        /** 未授权（Token 失效）—— 业务层返回的 401，非 HTTP 401 
        * @since 1.0.0
 */        const val UNAUTHORIZED = 401
        /** 禁止访问 
        * @since 1.0.0
 */        const val FORBIDDEN = 403
        /** 资源不存在 
        * @since 1.0.0
 */        const val NOT_FOUND = 404
    }

    /** 技术层错误码（由客户端定义，全部为负值） 
    * @since 1.0.0
 */    object Tech {
        /** 连接/读取/写入超时 
        * @since 1.0.0
 */        const val TIMEOUT = -1
        /** 无网络连接 
        * @since 1.0.0
 */        const val NO_NETWORK = -2
        /** SSL 握手失败 
        * @since 1.0.0
 */        const val SSL_ERROR = -3
        /** 请求已取消（协程 Job 取消） 
        * @since 1.0.0
 */        const val REQUEST_CANCELED = -999
        /** 未知错误 
        * @since 1.0.0
 */        const val UNKNOWN = -1000
        /** JSON 解析失败 
        * @since 1.0.0
 */        const val PARSE_ERROR = -1001
    }
}

