package com.answufeng.net.http.util

import android.util.Log
import com.answufeng.net.BuildConfig
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.interceptor.RequestExtraHeadersInterceptor
import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.NetCode
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.RequestOption
import com.answufeng.net.http.model.toNetworkResult
import com.answufeng.net.http.model.unwrapResponseOrRaw
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
class RequestExecutor
    @Inject
    constructor(
        private val configProvider: NetworkConfigProvider,
        private val refreshCoordinator: TokenRefreshCoordinator?,
        private val unauthorizedHandlerOptional: Optional<UnauthorizedHandler>,
        private val requestExtraHeadersInterceptor: RequestExtraHeadersInterceptor,
        private val networkMonitor: NetworkMonitor,
    ) {
        companion object {
            private const val TAG = "RequestExecutor"
        }

        suspend fun <T> executeRequest(
            successCode: Int? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            tag: String? = null,
            retryOnFailure: Int = 0,
            retryDelayMs: Long = RequestOption.DEFAULT_RETRY_DELAY_MS,
            retryOnTechnical: Boolean = true,
            retryOnBusiness: Boolean = false,
            extraHeaders: Map<String, String> = emptyMap(),
            disableOkHttpRetry: Boolean = false,
            call: suspend () -> BaseResponse<T>,
        ): NetworkResult<T> {
            val (safeRetry, safeDelay) = CoroutineRetryDefaults.normalize(retryOnFailure, retryDelayMs)
            val shouldSkipOkHttpRetry = disableOkHttpRetry || safeRetry > 0
            if (!shouldSkipOkHttpRetry) {
                warnIfLayeredCoroutineRetry(safeRetry)
            }
            checkNetworkOrThrow()
            val cfg = configProvider.current
            return trackAndExecute(
                "executeRequest",
                tag,
                cfg.enableRequestTracking,
                cfg.slowRequestThresholdMs,
            ) {
                var lastResult: NetworkResult<T>? = null
                val totalAttempts = (safeRetry.toLong() + 1L).toInt()

                for (attempt in 0 until totalAttempts) {
                    if (attempt > 0) {
                        delay(calculateBackoffDelay(safeDelay, attempt))
                    }

                    val result = executeBusinessCall(dispatcher, successCode, extraHeaders, shouldSkipOkHttpRetry, call)
                    val finalResult = handleUnauthorizedIfNeeded(result, successCode, dispatcher, extraHeaders, shouldSkipOkHttpRetry, call)

                    lastResult = finalResult

                    if (finalResult is NetworkResult.Success) break
                    if (finalResult is NetworkResult.TechnicalFailure && !retryOnTechnical) break
                    if (finalResult is NetworkResult.BusinessFailure && !retryOnBusiness) break
                    if (finalResult is NetworkResult.BusinessFailure && finalResult.code == NetCode.Business.UNAUTHORIZED) break
                }

                resolveResult(lastResult)
            }
        }

        /**
         * 执行 Retrofit 调用。若返回值实现 [BaseResponse]（如 [com.answufeng.net.http.model.GlobalResponse]），
         * 会自动校验业务码并拆出 `data`（与 [executeRequest] 一致）；否则 HTTP 成功即 [NetworkResult.Success]。
         *
         * 需要类型安全的 `onSuccess { ocr: OcrResult? -> }` 时，请优先使用 [executeRequest]。
         */
        suspend fun <T> executeRawRequest(
            successCode: Int? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            tag: String? = null,
            retryOnFailure: Int = 0,
            retryDelayMs: Long = RequestOption.DEFAULT_RETRY_DELAY_MS,
            extraHeaders: Map<String, String> = emptyMap(),
            disableOkHttpRetry: Boolean = false,
            call: suspend () -> T,
        ): NetworkResult<T> {
            val (safeRetry, safeDelay) = CoroutineRetryDefaults.normalize(retryOnFailure, retryDelayMs)
            val shouldSkipOkHttpRetry = disableOkHttpRetry || safeRetry > 0
            checkNetworkOrThrow()
            val cfg = configProvider.current
            return trackAndExecute(
                "executeRawRequest",
                tag,
                cfg.enableRequestTracking,
                cfg.slowRequestThresholdMs,
            ) {
                var lastResult: NetworkResult<T>? = null
                val totalAttempts = (safeRetry.toLong() + 1L).toInt()

                for (attempt in 0 until totalAttempts) {
                    if (attempt > 0) {
                        delay(calculateBackoffDelay(safeDelay, attempt))
                    }

                    val result =
                        withContext(dispatcher) {
                            applyExtraHeaders(extraHeaders, shouldSkipOkHttpRetry) {
                                try {
                                    val response = call()
                                    @Suppress("UNCHECKED_CAST")
                                    unwrapResponseOrRaw(response, successCode, configProvider) as NetworkResult<T>
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
                                }
                            }
                        }

                    lastResult = result
                    if (result is NetworkResult.Success) break
                    if (result is NetworkResult.BusinessFailure) break
                }

                resolveResult(lastResult)
            }
        }

        /**
         * Debug 构建下，若同时开启 OkHttp [com.answufeng.net.http.interceptor.DynamicRetryInterceptor]
         * 与协程层 [retryOnFailure]，退避会叠加放大；Release 不输出。
         */
        private fun warnIfLayeredCoroutineRetry(retryOnFailure: Int) {
            if (!BuildConfig.DEBUG || retryOnFailure <= 0) return
            if (!configProvider.current.enableRetryInterceptor) return
            Log.w(
                TAG,
                "Layered retry: OkHttp DynamicRetryInterceptor is enabled AND RequestExecutor " +
                    "retryOnFailure=$retryOnFailure. Prefer only one layer — see README «重试只开一层».",
            )
        }

        private fun calculateBackoffDelay(
            baseDelayMs: Long,
            attempt: Int,
        ): Long {
            val shift = min(attempt - 1, CoroutineRetryDefaults.MAX_BACKOFF_SHIFT)
            val exponentialDelay = baseDelayMs * (1L shl shift)
            val jitterFactor =
                CoroutineRetryDefaults.JITTER_BASE + Random.nextDouble() * CoroutineRetryDefaults.JITTER_RANGE
            return (exponentialDelay * jitterFactor).toLong()
        }

        private suspend fun <T> executeBusinessCall(
            dispatcher: CoroutineDispatcher,
            successCode: Int?,
            extraHeaders: Map<String, String>,
            skipRetry: Boolean,
            call: suspend () -> BaseResponse<T>,
        ): NetworkResult<T> {
            return withContext(dispatcher) {
                applyExtraHeaders(extraHeaders, skipRetry) {
                    try {
                        call().toNetworkResult(successCode, configProvider)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
                    }
                }
            }
        }

        private suspend fun <T> handleUnauthorizedIfNeeded(
            result: NetworkResult<T>,
            successCode: Int?,
            dispatcher: CoroutineDispatcher,
            extraHeaders: Map<String, String>,
            skipRetry: Boolean,
            call: suspend () -> BaseResponse<T>,
        ): NetworkResult<T> {
            if (result !is NetworkResult.BusinessFailure || result.code != NetCode.Business.UNAUTHORIZED) {
                return result
            }

            val coordinator =
                refreshCoordinator ?: run {
                    notifyUnauthorized()
                    return result
                }

            val currentToken = coordinator.getAccessToken()
            val refreshed =
                try {
                    coordinator.refreshIfNeededSuspend(currentToken) != null
                } catch (_: Exception) {
                    false
                }

            if (!refreshed) {
                notifyUnauthorized()
                return result
            }

            return try {
                withContext(dispatcher) {
                    applyExtraHeaders(extraHeaders, skipRetry) {
                        call().toNetworkResult(successCode, configProvider)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
            }
        }

        private inline fun <R> applyExtraHeaders(
            headers: Map<String, String>,
            skipRetry: Boolean = false,
            block: () -> R,
        ): R {
            if (headers.isNotEmpty()) {
                requestExtraHeadersInterceptor.threadLocalHeaders.set(headers)
            }
            if (skipRetry) {
                requestExtraHeadersInterceptor.threadLocalSkipRetry.set(true)
            }
            return try {
                block()
            } finally {
                if (headers.isNotEmpty()) {
                    requestExtraHeadersInterceptor.threadLocalHeaders.remove()
                }
                if (skipRetry) {
                    requestExtraHeadersInterceptor.threadLocalSkipRetry.remove()
                }
            }
        }

        private fun checkNetworkOrThrow() {
            if (!networkMonitor.isOnline()) {
                throw java.net.UnknownHostException("Network is not available")
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
