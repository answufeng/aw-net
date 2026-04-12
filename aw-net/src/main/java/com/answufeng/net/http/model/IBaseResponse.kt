package com.answufeng.net.http.model

/**
 * 业务响应实体的最小契约
 * 项目层实现此接口（如 DemoResponse<T>），对接后端 code/msg/data 结构
 * @since 1.0.0
 */interface IBaseResponse<out T> {
    /** 业务状态码 
    * @since 1.0.0
 */    val code: Int
    /** 业务消息 
    * @since 1.0.0
 */    val msg: String
    /** 核心数据 
    * @since 1.0.0
 */    val data: T?
}
