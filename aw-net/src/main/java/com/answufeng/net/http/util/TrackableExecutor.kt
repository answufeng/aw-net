package com.answufeng.net.http.util

import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage
import com.answufeng.net.http.model.NetworkResult

internal suspend fun <T> trackAndExecute(
    name: String,
    tag: String?,
    block: suspend () -> NetworkResult<T>
): NetworkResult<T> {
    val start = System.currentTimeMillis()
    NetTracker.track(
        NetEvent(
            name = name,
            stage = NetEventStage.START,
            timestampMs = start,
            tag = tag
        )
    )

    val result = block()

    val end = System.currentTimeMillis()
    val (type, errorCode) = when (result) {
        is NetworkResult.Success -> "SUCCESS" to null
        is NetworkResult.TechnicalFailure -> "TECHNICAL_FAILURE" to result.exception.code
        is NetworkResult.BusinessFailure -> "BUSINESS_FAILURE" to result.code
    }
    NetTracker.track(
        NetEvent(
            name = name,
            stage = NetEventStage.END,
            timestampMs = end,
            durationMs = end - start,
            resultType = type,
            errorCode = errorCode,
            tag = tag
        )
    )
    return result
}
