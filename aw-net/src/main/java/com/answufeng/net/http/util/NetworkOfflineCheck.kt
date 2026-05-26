package com.answufeng.net.http.util

import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.NetworkResult
import java.net.UnknownHostException

internal object NetworkOfflineCheck {
    fun failureIfOffline(monitor: NetworkMonitor): NetworkResult<Nothing>? {
        if (monitor.isOnline()) return null
        return NetworkResult.TechnicalFailure(
            ExceptionHandle.handleException(UnknownHostException("Network is not available")),
        )
    }
}
