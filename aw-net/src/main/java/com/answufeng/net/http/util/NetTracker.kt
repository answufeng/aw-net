package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetTracker
import com.answufeng.net.http.model.NetEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 网络请求监控单例，将事件转发给 [NetTracker] 接口的实现。
 *
 * 支持同步和异步两种上报方式。异步方式在独立协程中执行，带 5 秒超时保护。
 *
 * ```kotlin
 * // 设置监控实现
 * NetTracker.delegate = object : NetTracker {
 *     override fun onEvent(event: NetEvent) {
 *         analytics.track(event)
 *     }
 * }
 *
 * // 同步上报
 * NetTracker.track(event)
 *
 * // 异步上报（不阻塞网络线程）
 * NetTracker.trackAsync(event)
 * ```
 */
object NetTracker {

    private const val ASYNC_TRACK_TIMEOUT_MS = 5_000L

    @Volatile
    var delegate: NetTracker? = null

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun track(event: NetEvent) {
        delegate?.onEvent(event)
    }

    fun trackAsync(event: NetEvent) {
        val d = delegate ?: return
        scope.launch {
            try {
                withTimeout(ASYNC_TRACK_TIMEOUT_MS) { d.onEvent(event) }
            } catch (_: Exception) {
            }
        }
    }

    fun destroy() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
