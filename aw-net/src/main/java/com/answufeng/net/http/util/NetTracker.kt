package com.answufeng.net.http.util

import android.util.Log
import com.answufeng.net.http.annotations.NetTracker
import com.answufeng.net.http.model.NetEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 网络请求监控单例，将事件转发给 [NetTracker] 接口的实现。
 *
 * **推荐（Hilt）**：在应用模块中 `@Provides @Singleton` 注入 [com.answufeng.net.http.annotations.NetTracker]；
 * [com.answufeng.net.http.di.NetworkModule] 会将该实现赋给 [delegate]。不要同时再在 `Application` 里手动赋值 [delegate]，以免行为难测。
 *
 * **无 Hilt / 测试**：可直接设置 [delegate]。若仅想减少库内请求埋点开销，优先通过 [com.answufeng.net.http.config.NetworkConfig.enableRequestTracking] 关闭（仍保留你自行调用 [track] 的能力）。
 *
 * 支持同步和异步两种上报方式。异步方式在独立协程中执行，带 5 秒超时保护。
 *
 * 进程或测试结束时若需释放协程作用域，可调用 [destroy]（一般单进程 App 可不调用）。
 *
 * ```kotlin
 * NetTracker.delegate = object : NetTracker {
 *     override fun onEvent(event: NetEvent) { analytics.track(event) }
 * }
 * NetTracker.track(event)
 * NetTracker.trackAsync(event)
 * ```
 */
object NetTracker {

    private const val TAG = "NetTracker"
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
            } catch (e: Exception) {
                when (e) {
                    is TimeoutCancellationException -> {
                        Log.w(TAG, "trackAsync: delegate onEvent timed out after ${ASYNC_TRACK_TIMEOUT_MS}ms, event=${event.name}")
                    }
                    is CancellationException -> throw e
                    else -> {
                        Log.w(TAG, "trackAsync: delegate onEvent failed, event=${event.name}", e)
                    }
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
