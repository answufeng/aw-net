package com.answufeng.net.http.util

import android.util.Log
import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage
import com.answufeng.net.http.model.NetworkResult

private const val TRACKABLE_TAG = "AwNetTrackable"

/**
 * @param shouldTrack 为 false 时仅执行 [block]，不上报 [NetTracker] 起止事件（受 [com.answufeng.net.http.config.NetworkConfig.enableRequestTracking] 控制）。
 * @param slowRequestThresholdMs 与 [com.answufeng.net.http.config.NetworkConfig.slowRequestThresholdMs] 一致；非 null 时无论是否上报都会对超时的单次调用打日志。
 */
internal suspend fun <T> trackAndExecute(
    name: String,
    tag: String?,
    shouldTrack: Boolean,
    slowRequestThresholdMs: Long? = null,
    block: suspend () -> NetworkResult<T>
): NetworkResult<T> {
    val start = System.currentTimeMillis()
    if (shouldTrack) {
        NetTracker.trackAsync(
            NetEvent(
                name = name,
                stage = NetEventStage.START,
                timestampMs = start,
                tag = tag
            )
        )
    }

    val result = block()

    val end = System.currentTimeMillis()
    val duration = end - start
    val threshold = slowRequestThresholdMs
    if (threshold != null && duration > threshold) {
        Log.w(
            TRACKABLE_TAG,
            "Slow network op: name=$name tag=$tag durationMs=$duration thresholdMs=$threshold"
        )
    }

    if (shouldTrack) {
        val (type, errorCode) = when (result) {
            is NetworkResult.Success -> "SUCCESS" to null
            is NetworkResult.TechnicalFailure -> "TECHNICAL_FAILURE" to result.exception.code
            is NetworkResult.BusinessFailure -> "BUSINESS_FAILURE" to result.code
        }
        NetTracker.trackAsync(
            NetEvent(
                name = name,
                stage = NetEventStage.END,
                timestampMs = end,
                durationMs = duration,
                resultType = type,
                errorCode = errorCode,
                tag = tag
            )
        )
    }
    return result
}
