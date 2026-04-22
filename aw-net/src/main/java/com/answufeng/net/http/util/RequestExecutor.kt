package com.answufeng.net.http.util

import android.util.Log
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.GlobalResponse
import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.NetCode
import com.answufeng.net.http.model.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * 统一处理 API 请求执行、重试与鉴权刷新。
 *
 * - 支持业务码与技术异常分流
 * - 支持指数退避 + 抖动重试
 * - 遇到未授权时可协同 Token 刷新
 *
 * **鉴权两条路径**（勿混淆）：
 * - **HTTP 401/407**：由 OkHttp 的 [com.answufeng.net.http.auth.TokenAuthenticator] 在传输层重试，依赖 [com.answufeng.net.http.auth.TokenProvider] 与 [com.answufeng.net.http.auth.TokenRefreshCoordinator]。
 * - **业务层未授权**（[com.answufeng.net.http.model.NetCode.Business.UNAUTHORIZED] 等业务码）在 [handleUnauthorizedIfNeeded] 中触发刷新/回调；与「响应体为 JSON 但 HTTP 为 200」的接口约定强相关。
 * 请保证后端对鉴权失败的表现与上述两路径之一一致，以免「HTTP 全 200 + code 在 body」与底层 401 混用导致只走到一条刷新路径。
 */
@Singleton
class RequestExecutor @Inject constructor(
    private val configProvider: NetworkConfigProvider,
    private val refreshCoordinator: TokenRefreshCoordinator?,
    private val unauthorizedHandlerOptional: Optional<UnauthorizedHandler>
) {

    companion object {
        private const val MAX_BACKOFF_SHIFT = 5
        private const val JITTER_BASE = 0.8
        private const val JITTER_RANGE = 0.4
    }

    suspend fun <T> executeRequest(
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = 300L,
        retryOnTechnical: Boolean = true,
        retryOnBusiness: Boolean = false,
        call: suspend () -> BaseResponse<T>
    ): NetworkResult<T> = trackAndExecute("executeRequest", tag, configProvider.current.enableRequestTracking) {
        var lastResult: NetworkResult<T>? = null
        val totalAttempts = retryOnFailure + 1

        for (attempt in 0 until totalAttempts) {
            if (attempt > 0) {
                delay(calculateBackoffDelay(retryDelayMs, attempt))
            }

            val result = executeBusinessCall(dispatcher, successCode, call)
            val finalResult = handleUnauthorizedIfNeeded(result, successCode, dispatcher, call)

            lastResult = finalResult

            if (finalResult is NetworkResult.Success) break
            if (finalResult is NetworkResult.TechnicalFailure && !retryOnTechnical) break
            if (finalResult is NetworkResult.BusinessFailure && !retryOnBusiness) break
            if (finalResult is NetworkResult.BusinessFailure && finalResult.code == NetCode.Business.UNAUTHORIZED) break
        }

        resolveResult(lastResult)
    }

    suspend fun <T> executeRawRequest(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = 300L,
        call: suspend () -> T
    ): NetworkResult<T> = trackAndExecute("executeRawRequest", tag, configProvider.current.enableRequestTracking) {
        var lastResult: NetworkResult<T>? = null
        val totalAttempts = retryOnFailure + 1

        for (attempt in 0 until totalAttempts) {
            if (attempt > 0) {
                delay(calculateBackoffDelay(retryDelayMs, attempt))
            }

            val result = withContext(dispatcher) {
                try {
                    val response = call()
                    NetworkResult.Success(response)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
                }
            }

            lastResult = result
            if (result is NetworkResult.Success) break
        }

        resolveResult(lastResult)
    }

    private fun calculateBackoffDelay(baseDelayMs: Long, attempt: Int): Long {
        val shift = min(attempt - 1, MAX_BACKOFF_SHIFT)
        val exponentialDelay = baseDelayMs * (1L shl shift)
        val jitterFactor = JITTER_BASE + Random.nextDouble() * JITTER_RANGE
        return (exponentialDelay * jitterFactor).toLong()
    }

    private suspend fun <T> executeBusinessCall(
        dispatcher: CoroutineDispatcher,
        successCode: Int?,
        call: suspend () -> BaseResponse<T>
    ): NetworkResult<T> {
        return withContext(dispatcher) {
            try {
                val response = call()
                val effectiveSuccessCode = resolveSuccessCode(successCode, response)
                if (response.code == effectiveSuccessCode) {
                    NetworkResult.Success(response.data)
                } else {
                    NetworkResult.BusinessFailure(response.code, response.msg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
            }
        }
    }

    private fun resolveSuccessCode(explicitCode: Int?, response: BaseResponse<*>): Int {
        if (explicitCode != null) return explicitCode
        val annotationCode = (response as? GlobalResponse)?.resolvedSuccessCode
        if (annotationCode != null) return annotationCode
        return configProvider.current.defaultSuccessCode
    }

    private suspend fun <T> handleUnauthorizedIfNeeded(
        result: NetworkResult<T>,
        successCode: Int?,
        dispatcher: CoroutineDispatcher,
        call: suspend () -> BaseResponse<T>
    ): NetworkResult<T> {
        if (result !is NetworkResult.BusinessFailure || result.code != NetCode.Business.UNAUTHORIZED) {
            return result
        }

        val coordinator = refreshCoordinator ?: run {
            notifyUnauthorized()
            return result
        }

        val currentToken = coordinator.getAccessToken()
        val refreshed = try {
            coordinator.refreshIfNeededSuspend(currentToken) != null
        } catch (_: Exception) {
            false
        }

        if (!refreshed) {
            notifyUnauthorized()
            return result
        }

        return try {
            val afterRefresh = withContext(dispatcher) { call() }
            val effectiveSuccessCode = resolveSuccessCode(successCode, afterRefresh)
            if (afterRefresh.code == effectiveSuccessCode) {
                NetworkResult.Success(afterRefresh.data)
            } else {
                NetworkResult.BusinessFailure(afterRefresh.code, afterRefresh.msg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
        }
    }

    private fun notifyUnauthorized() {
        try {
            unauthorizedHandlerOptional.ifPresent { it.onUnauthorized() }
        } catch (e: Exception) {
            Log.w("RequestExecutor", "UnauthorizedHandler.onUnauthorized() failed", e)
        }
    }

    private fun <T> resolveResult(lastResult: NetworkResult<T>?): NetworkResult<T> {
        return lastResult
            ?: NetworkResult.TechnicalFailure(ExceptionHandle.handleException(IllegalStateException("No result produced")))
    }
}
