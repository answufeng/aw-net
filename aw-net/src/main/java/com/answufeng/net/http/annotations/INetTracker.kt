package com.answufeng.net.http.annotations

import com.answufeng.net.http.model.NetEvent

/**
 * 网络请求监控接口。
 *
 * 项目层可实现此接口，收集每次请求的开始/结束事件，用于统计耗时、成功率等。
 * 基础库通过 [com.answufeng.net.http.util.NetTracker] 单例调用，不强制依赖 DI。
 * @since 1.0.0
 */
interface INetTracker {

    /**
     * 收到一个网络事件（开始或结束）。
     *
     * @param event 网络事件详情，包含阶段、耗时、结果类型等
     * @since 1.0.0
 */
    fun onEvent(event: NetEvent)
}
