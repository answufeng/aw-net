package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.answufeng.net.http.auth.TokenRefreshCoordinator
import com.answufeng.net.http.auth.UnauthorizedHandler
import com.answufeng.net.http.exception.ExceptionHandle
import com.answufeng.net.http.model.GlobalResponse
import com.answufeng.net.http.model.IBaseResponse
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

@Singleton
class RequestExecutor @Inject constructor(
    private val configProvider: NetworkConfigProvider,
    private val refreshCoordinator: TokenRefreshCoordinator?,
    private val unauthorizedHandlerOptional: Optional<UnauthorizedHandler>
) {

    suspend fun <T> executeRequest(
        successCode: Int? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = 300L,
        retryOnTechnical: Boolean = true,
        retryOnBusiness: Boolean = false,
        call: suspend () -> IBaseResponse<T>
    ): NetworkResult<T> = trackAndExecute("executeRequest", tag) {
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

        resolveResult(lastResult)
    }

    suspend fun <T> executeRawRequest(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        tag: String? = null,
        retryOnFailure: Int = 0,
        retryDelayMs: Long = 300L,
        call: suspend () -> T
    ): NetworkResult<T> = trackAndExecute("executeRawRequest", tag) {
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

        resolveResult(lastResult)
    }

    private suspend fun <T> executeBusinessCall(
        dispatcher: CoroutineDispatcher,
        successCode: Int?,
        call: suspend () -> IBaseResponse<T>
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

    private fun resolveSuccessCode(explicitCode: Int?, response: IBaseResponse<*>): Int {
        if (explicitCode != null) return explicitCode
        val annotationCode = (response as? GlobalResponse)?.resolvedSuccessCode
        if (annotationCode != null) return annotationCode
        return configProvider.current.defaultSuccessCode
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
        } catch (_: Exception) {
        }
    }

    private fun <T> resolveResult(lastResult: NetworkResult<T>?): NetworkResult<T> {
        return lastResult
            ?: NetworkResult.TechnicalFailure(ExceptionHandle.handleException(IllegalStateException("No result produced")))
    }
}
