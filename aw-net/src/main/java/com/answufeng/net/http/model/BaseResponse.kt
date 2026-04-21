package com.answufeng.net.http.model

/**
 * 业务响应实体的最小契约
 * 项目层实现此接口（如 DemoResponse<T>），对接后端 code/msg/data 结构
 */
interface BaseResponse<out T> {
    /** 业务状态码 
 */
    val code: Int
    /** 业务消息 
 */
    val msg: String
    /** 核心数据 
 */
    val data: T?
}
