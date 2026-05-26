package com.answufeng.net.http.util

import android.util.Log
import com.answufeng.net.BuildConfig
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.exception.BusinessFailureException
import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.NetCode
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.RequestOption
import com.answufeng.net.http.model.toNetworkResult
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

@Singleton
class RequestExecutor
    @Inject
    constructor(
        private val configProvider: NetworkConfigProvider,
        private val refreshCoordinator: TokenRefreshCoordinator?,
        private val unauthorizedHandlerOptional: Optional<UnauthorizedHandler>,
        private val networkMonitor: NetworkMonitor,
    ) {
        companion object {
            private const val TAG = "RequestExecutor"
        }

        /**
         * 默认业务请求：Retrofit 声明类型为 `T`（库内 Converter 已解包 `{code,msg,data}`），
         * 或仍使用 [BaseResponse] / 裸类型 + [@com.answufeng.net.http.annotations.RawResponse]。
         *
         * 业务 JSON 内 `code == 401` 时会尝试刷新 Token 并重试一次（与 [executeRequest] 一致）。
         */
        suspend fun <T> execute(
            successCode: Int? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            tag: String? = null,
            retryOnFailure: Int = 0,
            retryDelayMs: Long = RequestOption.DEFAULT_RETRY_DELAY_MS,
            retryOnTechnical: Boolean = true,
            retryOnBusiness: Boolean = false,
            extraHeaders: Map<String, String> = emptyMap(),
            disableOkHttpRetry: Boolean = false,
            call: suspend () -> T,
        ): NetworkResult<T> =
            executeWithUnauthorizedRetry(
                operationName = "execute",
                tag = tag,
                successCode = successCode,
                dispatcher = dispatcher,
                retryOnFailure = retryOnFailure,
                retryDelayMs = retryDelayMs,
                retryOnTechnical = retryOnTechnical,
                retryOnBusiness = retryOnBusiness,
                extraHeaders = extraHeaders,
                disableOkHttpRetry = disableOkHttpRetry,
                invoke = { invokeCall(call) },
                retryAfterRefresh = { invokeCall(call) },
            )

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
        ): NetworkResult<T> =
            executeWithUnauthorizedRetry(
                operationName = "executeRequest",
                tag = tag,
                successCode = successCode,
                dispatcher = dispatcher,
                retryOnFailure = retryOnFailure,
                retryDelayMs = retryDelayMs,
                retryOnTechnical = retryOnTechnical,
                retryOnBusiness = retryOnBusiness,
                extraHeaders = extraHeaders,
                disableOkHttpRetry = disableOkHttpRetry,
                invoke = { mapBaseResponseCall(call, successCode) },
                retryAfterRefresh = { mapBaseResponseCall(call, successCode) },
            )

        @Deprecated(
            message = "Use execute instead. @RawResponse marks the Retrofit method, not executeRawRequest.",
            replaceWith = ReplaceWith("execute(dispatcher, tag, retryOnFailure, retryDelayMs, retryOnTechnical, retryOnBusiness, extraHeaders, disableOkHttpRetry, call)"),
        )
        suspend fun <T> executeRawRequest(
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            tag: String? = null,
            retryOnFailure: Int = 0,
            retryDelayMs: Long = RequestOption.DEFAULT_RETRY_DELAY_MS,
            retryOnTechnical: Boolean = true,
            retryOnBusiness: Boolean = false,
            extraHeaders: Map<String, String> = emptyMap(),
            disableOkHttpRetry: Boolean = false,
            call: suspend () -> T,
        ): NetworkResult<T> =
            execute(
                successCode = null,
                dispatcher = dispatcher,
                tag = tag,
                retryOnFailure = retryOnFailure,
                retryDelayMs = retryDelayMs,
                retryOnTechnical = retryOnTechnical,
                retryOnBusiness = retryOnBusiness,
                extraHeaders = extraHeaders,
                disableOkHttpRetry = disableOkHttpRetry,
                call = call,
            )

        private suspend fun <T> mapBaseResponseCall(
            call: suspend () -> BaseResponse<T>,
            successCode: Int?,
        ): NetworkResult<T> {
            return try {
                call().toNetworkResult(successCode, configProvider, successCode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mapExceptionToResult(e)
            }
        }

        private suspend fun <T> executeWithUnauthorizedRetry(
            operationName: String,
            tag: String?,
            successCode: Int?,
            dispatcher: CoroutineDispatcher,
            retryOnFailure: Int,
            retryDelayMs: Long,
            retryOnTechnical: Boolean,
            retryOnBusiness: Boolean,
            extraHeaders: Map<String, String>,
            disableOkHttpRetry: Boolean,
            invoke: suspend () -> NetworkResult<T>,
            retryAfterRefresh: suspend () -> NetworkResult<T>,
        ): NetworkResult<T> {
            val (safeRetry, safeDelay) = CoroutineRetryDefaults.normalize(retryOnFailure, retryDelayMs)
            val shouldSkipOkHttpRetry = disableOkHttpRetry || safeRetry > 0
            if (!shouldSkipOkHttpRetry) {
                warnIfLayeredCoroutineRetry(safeRetry)
            }
            checkNetworkOrReturnFailure()?.let { return it }
            val cfg = configProvider.current
            val callContext =
                RequestCallContext(
                    extraHeaders = extraHeaders,
                    skipOkHttpRetry = shouldSkipOkHttpRetry,
                    successCode = successCode,
                )
            return trackAndExecute(operationName, tag, cfg.enableRequestTracking, cfg.slowRequestThresholdMs) {
                var lastResult: NetworkResult<T>? = null
                val totalAttempts = (safeRetry.toLong() + 1L).toInt()

                for (attempt in 0 until totalAttempts) {
                    if (attempt > 0) {
                        delay(calculateBackoffDelay(safeDelay, attempt))
                    }

                    val result =
                        withContext(dispatcher) {
                            RequestCallContextHolder.withContext(callContext) {
                                invoke()
                            }
                        }
                    val finalResult =
                        handleUnauthorizedIfNeeded(result) {
                            withContext(dispatcher) {
                                RequestCallContextHolder.withContext(callContext) {
                                    retryAfterRefresh()
                                }
                            }
                        }

                    lastResult = finalResult

                    if (shouldStopRetry(finalResult, retryOnTechnical, retryOnBusiness, stopOnUnauthorized = true)) {
                        break
                    }
                }

                resolveResult(lastResult)
            }
        }

        private fun shouldStopRetry(
            result: NetworkResult<*>?,
            retryOnTechnical: Boolean,
            retryOnBusiness: Boolean,
            stopOnUnauthorized: Boolean = true,
        ): Boolean {
            if (result == null) return false
            return when (result) {
                is NetworkResult.Success -> true
                is NetworkResult.TechnicalFailure -> !retryOnTechnical
                is NetworkResult.BusinessFailure -> {
                    if (stopOnUnauthorized && result.code == NetCode.Business.UNAUTHORIZED) true
                    else !retryOnBusiness
                }
            }
        }

        private suspend fun <T> invokeCall(call: suspend () -> T): NetworkResult<T> {
            return try {
                val response = call()
                if (BuildConfig.DEBUG && response is BaseResponse<*>) {
                    Log.w(
                        TAG,
                        "Received BaseResponse in execute(); prefer suspend fun(): T or executeRequest { }. ",
                    )
                }
                NetworkResult.Success(response)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mapExceptionToResult(e)
            }
        }

        private fun <T> mapExceptionToResult(e: Exception): NetworkResult<T> {
            if (e is BusinessFailureException) {
                return NetworkResult.BusinessFailure(e.code, e.message ?: "")
            }
            return NetworkResult.TechnicalFailure(ExceptionHandle.handleException(e))
        }

        private fun warnIfLayeredCoroutineRetry(retryOnFailure: Int) {
            if (!BuildConfig.DEBUG || retryOnFailure <= 0) return
            if (!configProvider.current.enableRetryInterceptor) return
            Log.w(
                TAG,
                "Layered retry: OkHttp DynamicRetryInterceptor is enabled AND coroutine " +
                    "retryOnFailure=$retryOnFailure. Prefer only one layer — see README.",
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

        private suspend fun <T> handleUnauthorizedIfNeeded(
            result: NetworkResult<T>,
            retry: suspend () -> NetworkResult<T>,
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
                retry()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mapExceptionToResult(e)
            }
        }

        private fun checkNetworkOrReturnFailure(): NetworkResult<Nothing>? =
            NetworkOfflineCheck.failureIfOffline(networkMonitor)

        private fun notifyUnauthorized() {
            try {
                unauthorizedHandlerOptional.ifPresent { it.onUnauthorized() }
            } catch (e: Exception) {
                Log.w(TAG, "UnauthorizedHandler.onUnauthorized() failed", e)
            }
        }

        private fun <T> resolveResult(lastResult: NetworkResult<T>?): NetworkResult<T> {
            return lastResult
                ?: NetworkResult.TechnicalFailure(
                    ExceptionHandle.handleException(IllegalStateException("No result produced")),
                )
        }
    }
