package com.answufeng.net.http.model

/**
 * 业务响应的最小契约：`code` / `msg` / `data`。
 *
 * 项目层可令数据类实现本接口，或使用库内 [GlobalResponse] 与 [com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory] 的默认 Gson 配置。
 */
interface BaseResponse<out T> {
    /** 业务状态码。 */
    val code: Int
    /** 业务提示或错误信息。 */
    val msg: String
    /** 成功时的载荷；无体时可为 `null`。 */
    val data: T?
}
