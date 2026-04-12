package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.INetTracker
import com.answufeng.net.http.model.NetEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 全局网络监控分发器。
 *
 * - 默认不设置 delegate，不产生任何开销；
 * - 项目层如需监控，仅需在合适时机设置 [delegate] 为自定义的 [INetTracker] 实现。
 * - 支持同步 [track] 和异步 [trackAsync] 两种分发方式。
 *
 * 例如：
 *
 * ```kotlin
 * class AppNetTracker : INetTracker { ... }
 *
 * NetTracker.delegate = AppNetTracker()
 * ```
 * @since 1.0.0
 */object NetTracker {

    @Volatile
    var delegate: INetTracker? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 同步分发事件。在调用线程上直接执行 delegate.onEvent()。
     * @since 1.0.0
 */    fun track(event: NetEvent) {
        delegate?.onEvent(event)
    }

    /**
     * 异步分发事件。事件会在后台协程中处理，不阻塞调用线程。
     * 适用于 onEvent 中有耗时操作（如写数据库、上报埋点）的场景。
     * @since 1.0.0
 */    fun trackAsync(event: NetEvent) {
        val d = delegate ?: return
        scope.launch {
            try {
                d.onEvent(event)
            } catch (_: Exception) {
                // 埋点失败不应影响业务流程
            }
        }
    }
}
