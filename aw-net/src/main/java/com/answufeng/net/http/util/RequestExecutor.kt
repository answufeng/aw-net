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
 */@Singleton
class RequestExecutor @Inject constructor(
    private val configProvider: NetworkConfigProvider,
    private val tokenProviderOptional: Optional<TokenProvider>,
    private val unauthorizedHandlerOptional: Optional<UnauthorizedHandler>
) {

    /** Mutex 保证同一时刻只有一个协程执行 Token 刷新，其余协程在锁释放后直接重试 
    * @since 1.0.0
 */    private val refreshMutex = Mutex()

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
     * @since 1.0.0
 */    suspend fun <T> executeRequest(
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
        NetTracker.track(
            NetEvent(
                name = "executeRequest",
                stage = NetEventStage.START,
                timestampMs = start,
                tag = tag
            )
        )

        var lastResult: NetworkResult<T>? = null
        val totalAttempts = retryOnFailure + 1 // 1(首次) + retryOnFailure(重试次数)

        for (attempt in 0 until totalAttempts) {
            if (attempt > 0) {
                delay(retryDelayMs)
            }

            val result = withContext(dispatcher) {
            try {
                val response = call()
                val effectiveSuccessCode = successCode ?: configProvider.current.defaultSuccessCode
                if (response.code == effectiveSuccessCode) {
                    NetworkResult.Success(response.data)
                } else {
                    NetworkResult.BusinessFailure(response.code, response.msg)
                }
            } catch (e: Exception) {
                NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
            }
        }

        // 如果业务失败表示 Token 过期（code==401），执行刷新/重试或通知未授权
        val finalResult = if (result is NetworkResult.BusinessFailure && result.code == NetCode.Biz.UNAUTHORIZED) {
            if (!tokenProviderOptional.isPresent) {
                // 未配置 TokenProvider：通知未授权回调（如有）并返回原始业务失败
                try {
                    unauthorizedHandlerOptional.ifPresent { it.onUnauthorized() }
                } catch (_: Exception) {}
                result
            } else {
                // TokenProvider 已配置：尝试串行刷新 + 重试
                refreshMutex.withLock {
                    val effectiveCode = successCode ?: configProvider.current.defaultSuccessCode

                    // 步骤 1：重试 —— 等锁期间其他协程可能已刷新了 token
                    val retryResponse = try {
                        withContext(dispatcher) { call() }
                    } catch (e: Exception) {
                        return@withLock NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
                    }
                    if (retryResponse.code == effectiveCode) {
                        return@withLock NetworkResult.Success(retryResponse.data)
                    }
                    if (retryResponse.code != NetCode.Biz.UNAUTHORIZED) {
                        return@withLock NetworkResult.BusinessFailure(retryResponse.code, retryResponse.msg)
                    }

                    // 步骤 2：仍然 401 —— 执行实际的 token 刷新
                    val refreshed = try {
                        tokenProviderOptional.get().refreshTokenSuspend()
                    } catch (_: Throwable) {
                        false
                    }
                    if (!refreshed) {
                        try {
                            unauthorizedHandlerOptional.ifPresent { it.onUnauthorized() }
                        } catch (_: Exception) {}
                        return@withLock NetworkResult.BusinessFailure(result.code, result.msg)
                    }

                    // 步骤 3：使用刷新后的 token 重试一次
                    try {
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
            }
        } else {
            result
        }

            lastResult = finalResult

            // 如果成功或不满足重试条件，提前退出循环
            if (finalResult is NetworkResult.Success) break
            if (finalResult is NetworkResult.TechnicalFailure && !retryOnTechnical) break
            if (finalResult is NetworkResult.BusinessFailure && !retryOnBusiness) break
            // 401 不通过协程级重试（已有专门的 Token 刷新机制）
            if (finalResult is NetworkResult.BusinessFailure && finalResult.code == NetCode.Biz.UNAUTHORIZED) break
        } // end retry loop

        @Suppress("UNCHECKED_CAST")
        val resolvedResult = lastResult as NetworkResult<T>

        val end = System.currentTimeMillis()
        val duration = end - start
        val (type, errorCode) = when (resolvedResult) {
            is NetworkResult.Success -> "SUCCESS" to null
            is NetworkResult.TechnicalFailure -> "TECHNICAL_FAILURE" to resolvedResult.exception.code
            is NetworkResult.BusinessFailure -> "BUSINESS_FAILURE" to resolvedResult.code
        }
        NetTracker.track(
            NetEvent(
                name = "executeRequest",
                stage = NetEventStage.END,
                timestampMs = end,
                durationMs = duration,
                resultType = type,
                errorCode = errorCode,
                tag = tag
            )
        )
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
 */    suspend fun <T> executeRawRequest(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = 300L,
        call: suspend () -> T
    ): NetworkResult<T> {
        val start = System.currentTimeMillis()
        NetTracker.track(
            NetEvent(
                name = "executeRawRequest",
                stage = NetEventStage.START,
                timestampMs = start,
                tag = tag
            )
        )

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
                } catch (e: Exception) {
                    NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
                }
            }

            lastResult = result
            if (result is NetworkResult.Success) break
        }

        @Suppress("UNCHECKED_CAST")
        val resolvedResult = lastResult as NetworkResult<T>

        val end = System.currentTimeMillis()
        val duration = end - start
        val (type, errorCode) = when (resolvedResult) {
            is NetworkResult.Success -> "SUCCESS" to null
            is NetworkResult.TechnicalFailure -> "TECHNICAL_FAILURE" to resolvedResult.exception.code
            is NetworkResult.BusinessFailure -> "BUSINESS_FAILURE" to resolvedResult.code
        }
        NetTracker.track(
            NetEvent(
                name = "executeRawRequest",
                stage = NetEventStage.END,
                timestampMs = end,
                durationMs = duration,
                resultType = type,
                errorCode = errorCode,
                tag = tag
            )
        )
        return resolvedResult
    }
}
