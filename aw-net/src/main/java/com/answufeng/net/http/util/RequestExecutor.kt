package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.auth.TokenProvider
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.IBaseResponse
import com.answufeng.net.http.model.NetCode
import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage
import com.answufeng.net.http.model.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 负责普通请求的执行，包含以下职责：
 * - **executeRequest**：执行业务层请求并映射为 [NetworkResult]，支持自定义成功码与事件追踪。
 *   若服务端返回 401，会自动通过 [TokenProvider] 进行 Token 串行刷新 + 重试（最多一次）。
 * - **executeRawRequest**：执行无业务协议约束的原始请求，直接将返回值封装为 [NetworkResult.Success]。
 *
 * @param configProvider 提供当前 [com.answufeng.net.http.annotations.NetworkConfig] 实例
 * @param tokenProviderOptional 可选的 Token 管理器，缺省时 401 仅触发 [UnauthorizedHandler]
 * @param unauthorizedHandlerOptional 可选的未授权回调，当刷新失败或无 TokenProvider 时触发
 * @since 1.0.0
 */
@Singleton
class RequestExecutor @Inject constructor(
    private val configProvider: NetworkConfigProvider,
    private val tokenProviderOptional: Optional<TokenProvider>,
    private val unauthorizedHandlerOptional: Optional<UnauthorizedHandler>
) {

    /**
     * Mutex 保证同一时刻只有一个协程执行 Token 刷新，其余协程在锁释放后直接重试
     * @since 1.0.0
$     */
    private val refreshMutex = Mutex()

    /**
     * 执行带标准返回结构的业务请求（IBaseResponse<T>），支持可选的协程级重试。
     *
     * 协程级重试与 OkHttp 的 [com.answufeng.net.http.interceptor.DynamicRetryInterceptor] 不同：
     * - OkHttp 级：仅重试 HTTP 传输层错误（5xx、IO 异常）
     * - 协程级：可重试业务失败、解析异常等所有异常类型
     *
     * @param successCode 业务成功码，null 时使用全局配置
     * @param dispatcher 协程调度器
     * @param tag 监控标签
     * @param retryOnFailure 协程级重试次数（不含首次执行）。0 = 不重试（默认）
     * @param retryDelayMs 重试间隔毫秒数，默认 300ms
     * @param retryOnTechnical 是否在技术错误（网络/解析等）时重试，默认 true
     * @param retryOnBusiness 是否在业务错误时重试，默认 false
     * @param call Retrofit suspend 接口方法
     *
     * **特殊行为**：当业务返回 code=401（UNAUTHORIZED）时，无论 `retryOnBusiness` 是否为 true，
     * 都不会进入重试循环，而是直接走 Token 刷新流程（若 [NetworkConfig.enableCoroutineLevelTokenRefresh]
     * 为 true）或触发 [UnauthorizedHandler]。这是因为 401 不属于可重试的业务错误，而需要鉴权介入。
     * @since 1.0.0
$     */
    suspend fun <T> executeRequest(
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = 300L,
        retryOnTechnical: Boolean = true,
        retryOnBusiness: Boolean = false,
        call: suspend () -> IBaseResponse<T>
    ): NetworkResult<T> {
        val start = System.currentTimeMillis()
        trackStart("executeRequest", start, tag)

        var lastResult: NetworkResult<T>? = null
        val totalAttempts = retryOnFailure + 1

        for (attempt in 0 until totalAttempts) {
            if (attempt > 0) {
                delay(retryDelayMs)
            }

            val result = executeBusinessCall(dispatcher, successCode, call)
            val finalResult = handleUnauthorizedIfNeeded(result, successCode, dispatcher, call)

            lastResult = finalResult

            if (finalResult is NetworkResult.Success) break
            if (finalResult is NetworkResult.TechnicalFailure && !retryOnTechnical) break
            if (finalResult is NetworkResult.BusinessFailure && !retryOnBusiness) break
            if (finalResult is NetworkResult.BusinessFailure && finalResult.code == NetCode.Business.UNAUTHORIZED) break
        }

        val resolvedResult = resolveResult(lastResult)
        trackEnd("executeRequest", start, resolvedResult, tag)
        return resolvedResult
    }

    /**
     * 执行原始请求（接口直接返回 T 而非 IBaseResponse），支持可选的协程级重试。
     *
     * @param dispatcher 协程调度器
     * @param tag 监控标签
     * @param retryOnFailure 协程级重试次数（不含首次执行）。0 = 不重试（默认）
     * @param retryDelayMs 重试间隔毫秒数，默认 300ms
     * @param call Retrofit suspend 接口方法
     * @since 1.0.0
$     */
    suspend fun <T> executeRawRequest(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = 300L,
        call: suspend () -> T
    ): NetworkResult<T> {
        val start = System.currentTimeMillis()
        trackStart("executeRawRequest", start, tag)

        var lastResult: NetworkResult<T>? = null
        val totalAttempts = retryOnFailure + 1

        for (attempt in 0 until totalAttempts) {
            if (attempt > 0) {
                delay(retryDelayMs)
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

        val resolvedResult = resolveResult(lastResult)
        trackEnd("executeRawRequest", start, resolvedResult, tag)
        return resolvedResult
    }

    private suspend fun <T> executeBusinessCall(
        dispatcher: CoroutineDispatcher,
        successCode: Int?,
        call: suspend () -> IBaseResponse<T>
    ): NetworkResult<T> {
        return withContext(dispatcher) {
            try {
                val response = call()
                val effectiveSuccessCode = successCode ?: configProvider.current.defaultSuccessCode
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

    private suspend fun <T> handleUnauthorizedIfNeeded(
        result: NetworkResult<T>,
        successCode: Int?,
        dispatcher: CoroutineDispatcher,
        call: suspend () -> IBaseResponse<T>
    ): NetworkResult<T> {
        if (result !is NetworkResult.BusinessFailure || result.code != NetCode.Business.UNAUTHORIZED) {
            return result
        }

        if (configProvider.current.enableCoroutineLevelTokenRefresh && tokenProviderOptional.isPresent) {
            return refreshMutex.withLock {
                performTokenRefreshAndRetry(result, successCode, dispatcher, call)
            }
        }

        notifyUnauthorized()
        return result
    }

    private suspend fun <T> performTokenRefreshAndRetry(
        originalResult: NetworkResult.BusinessFailure,
        successCode: Int?,
        dispatcher: CoroutineDispatcher,
        call: suspend () -> IBaseResponse<T>
    ): NetworkResult<T> {
        val tokenProvider = tokenProviderOptional.get()
        val effectiveCode = successCode ?: configProvider.current.defaultSuccessCode

        val currentToken = tokenProvider.getAccessToken()
        val retryWithCurrentToken = try {
            withContext(dispatcher) { call() }
        } catch (e: Exception) {
            return NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
        }

        if (retryWithCurrentToken.code == effectiveCode) {
            return NetworkResult.Success(retryWithCurrentToken.data)
        }
        if (retryWithCurrentToken.code != NetCode.Business.UNAUTHORIZED) {
            return NetworkResult.BusinessFailure(retryWithCurrentToken.code, retryWithCurrentToken.msg)
        }

        val refreshed = try {
            tokenProvider.refreshTokenSuspend()
        } catch (_: Exception) {
            false
        }
        if (!refreshed) {
            notifyUnauthorized()
            return NetworkResult.BusinessFailure(originalResult.code, originalResult.msg)
        }

        return try {
            val afterRefresh = withContext(dispatcher) { call() }
            if (afterRefresh.code == effectiveCode) {
                NetworkResult.Success(afterRefresh.data)
            } else {
                NetworkResult.BusinessFailure(afterRefresh.code, afterRefresh.msg)
            }
        } catch (e: Exception) {
            NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
        }
    }

    private fun notifyUnauthorized() {
        try {
            unauthorizedHandlerOptional.ifPresent { it.onUnauthorized() }
        } catch (_: Exception) {
        }
    }

    private fun <T> resolveResult(lastResult: NetworkResult<T>?): NetworkResult<T> {
        return lastResult
            ?: NetworkResult.TechnicalFailure(ExceptionHandle.handleException(IllegalStateException("No result produced")))
    }

    private fun trackStart(name: String, timestampMs: Long, tag: String?) {
        NetTracker.track(
            NetEvent(
                name = name,
                stage = NetEventStage.START,
                timestampMs = timestampMs,
                tag = tag
            )
        )
    }

    private fun <T> trackEnd(name: String, startMs: Long, result: NetworkResult<T>, tag: String?) {
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
                durationMs = end - startMs,
                resultType = type,
                errorCode = errorCode,
                tag = tag
            )
        )
    }
}
