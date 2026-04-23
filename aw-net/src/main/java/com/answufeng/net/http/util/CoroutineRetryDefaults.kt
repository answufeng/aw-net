package com.answufeng.net.http.util

import android.util.Log
import com.answufeng.net.BuildConfig
import com.answufeng.net.http.model.RequestOption
import kotlin.math.max
import kotlin.math.min

/**
 * 协程层重试（[RequestExecutor]）的指数退避与 [retryOnFailure] / [retryDelayMs] 归一化。
 * 与 OkHttp [com.answufeng.net.http.interceptor.DynamicRetryInterceptor] 的全局重试**独立**配置，勿混用为同一套常数。
 */
internal object CoroutineRetryDefaults {
    const val MAX_BACKOFF_SHIFT = 5
    const val JITTER_BASE = 0.8
    const val JITTER_RANGE = 0.4

    private const val MIN_RETRY_DELAY_MS = 1L

    /**
     * 协程重试次数（不含首次）的硬上限，避免误传极大值导致长时间循环或整型溢出。
     */
    const val MAX_RETRY_ON_FAILURE = 10_000

    /**
     * 将 [retryOnFailure]、[retryDelayMs] 规范为可安全用于循环与退避的值。
     * Debug 下若发生修正会打一条 [Log]。
     */
    fun normalize(retryOnFailure: Int, retryDelayMs: Long): Pair<Int, Long> {
        var r = min(max(0, retryOnFailure), MAX_RETRY_ON_FAILURE)
        if (r != retryOnFailure) {
            logIfDebug("retryOnFailure was $retryOnFailure, coerced to $r")
        }

        val d = if (retryDelayMs < MIN_RETRY_DELAY_MS) {
            logIfDebug("retryDelayMs was $retryDelayMs, coerced to ${RequestOption.DEFAULT_RETRY_DELAY_MS}")
            RequestOption.DEFAULT_RETRY_DELAY_MS
        } else {
            retryDelayMs
        }
        return r to d
    }

    private fun logIfDebug(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.w("CoroutineRetry", msg)
        }
    }
}
