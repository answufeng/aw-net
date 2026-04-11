package com.answufeng.net.http.annotations

/**
 * 网络日志输出接口
 * 职责：由项目层实现并注入，基础库只负责调用
 */
interface INetLogger {
    fun d(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}
