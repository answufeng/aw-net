package com.answufeng.net.http.annotations

/**
 * 网络日志输出接口
 * 职责：由项目层实现并注入，基础库只负责调用
 */
interface NetLogger {
    /**
     * 输出调试级别日志。
     * @param tag 日志标签
     * @param msg 日志消息
     */
    fun d(tag: String, msg: String)

    /**
     * 输出错误级别日志。
     * @param tag 日志标签
     * @param msg 日志消息
     * @param throwable 可选的异常对象
     */
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}
