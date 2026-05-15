package com.answufeng.net.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class HeartbeatManager(
    private val config: WebSocketManager.Config,
    private val connectionId: String,
    private val wsLogger: DefaultWebSocketLogger,
    private val scopeProvider: () -> CoroutineScope,
    private val isConnected: () -> Boolean,
    private val onTimeout: () -> Unit,
    private val sendHeartbeat: () -> Unit,
) {
    @Volatile
    var lastPongTime = 0L
        private set

    private var heartbeatJob: Job? = null

    private val scope: CoroutineScope get() = scopeProvider()

    fun onPongReceived() {
        lastPongTime = System.currentTimeMillis()
    }

    fun startWithCheck() {
        stop()
        heartbeatJob =
            scope.launch {
                while (true) {
                    delay(config.heartbeatIntervalMs)
                    ensureActive()
                    if (!isConnected()) break

                    if (config.heartbeatTimeoutMs > 0 && lastPongTime > 0) {
                        val elapsed = System.currentTimeMillis() - lastPongTime
                        if (elapsed > config.heartbeatTimeoutMs) {
                            wsLogger.w(
                                connectionId,
                                "心跳超时，距上次 pong 已过 ${elapsed}ms，阈值 ${config.heartbeatTimeoutMs}ms",
                            )
                            onTimeout()
                            break
                        }
                    }
                    sendHeartbeat()
                }
            }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
