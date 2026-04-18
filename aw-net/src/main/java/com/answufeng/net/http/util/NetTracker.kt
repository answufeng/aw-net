package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetTracker
import com.answufeng.net.http.model.NetEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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
