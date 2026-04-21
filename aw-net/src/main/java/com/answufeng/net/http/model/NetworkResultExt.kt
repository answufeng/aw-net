package com.answufeng.net.http.model

import com.answufeng.net.http.exception.BaseNetException

/**
 * 成功时执行回调，失败时跳过。支持链式调用。
 * @param action 成功回调，参数为 data（可能为 null）
 * @return 当前 NetworkResult 实例，便于链式调用
 */
inline fun <T> NetworkResult<T>.onSuccess(action: (T?) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success) action(data)
    return this
}

/**
 * 仅在成功且数据非 null 时执行回调。
 *
 * 当接口可能返回 `data = null` 的成功响应时，用此方法可避免手动判空：
 * ```kotlin
 * result.onSuccessNotNull { data ->
 *     updateUI(data)  // data: T（非 null）
 * }
 * ```
 * @param action 成功回调，参数为非 null 的 data
 * @return 当前 NetworkResult 实例，便于链式调用
 */
inline fun <T> NetworkResult<T>.onSuccessNotNull(action: (T) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success && data != null) action(data)
    return this
}

/**
 * 技术失败时执行回调（网络异常、解析异常等），成功或业务失败时跳过。
 * @param action 技术失败回调，参数为 [BaseNetException]
 * @return 当前 NetworkResult 实例，便于链式调用
 */
inline fun <T> NetworkResult<T>.onTechnicalFailure(action: (BaseNetException) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.TechnicalFailure) action(exception)
    return this
}

/**
 * 业务失败时执行回调（服务端返回非成功 code），成功或技术失败时跳过。
 * @param action 业务失败回调，参数为 (code, msg)
 * @return 当前 NetworkResult 实例，便于链式调用
 */
inline fun <T> NetworkResult<T>.onBusinessFailure(action: (code: Int, msg: String) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.BusinessFailure) action(code, msg)
    return this
}

/**
 * 任意失败（技术或业务）时执行回调，成功时跳过。
 * @param action 失败回调，参数为当前 NetworkResult（可智能转换为具体子类）
 * @return 当前 NetworkResult 实例，便于链式调用
 */
inline fun <T> NetworkResult<T>.onFailure(action: (NetworkResult<T>) -> Unit): NetworkResult<T> {
    when (this) {
        is NetworkResult.Success -> {}
        else -> action(this)
    }
    return this
}

/**
 * 将 [NetworkResult]<T> 映射为 [NetworkResult]<R>，仅对 Success 分支做转换，
 * TechnicalFailure 和 BusinessFailure 原样保留。
 * @param transform 转换函数，将 T? 映射为 R
 * @return 映射后的 [NetworkResult]<R>
 */
inline fun <T, R> NetworkResult<T>.map(transform: (T?) -> R): NetworkResult<R> {
    return when (this) {
        is NetworkResult.Success -> NetworkResult.Success(transform(data))
        is NetworkResult.TechnicalFailure -> NetworkResult.TechnicalFailure(exception)
        is NetworkResult.BusinessFailure -> NetworkResult.BusinessFailure(code, msg)
    }
}

/**
 * 折叠三种结果分支，统一返回类型 R。
 * @param onSuccess 成功分支处理
 * @param onTechnicalFailure 技术失败分支处理
 * @param onBusinessFailure 业务失败分支处理
 * @return 三种分支的统一返回值
 */
inline fun <T, R> NetworkResult<T>.fold(
    onSuccess: (T?) -> R,
    onTechnicalFailure: (BaseNetException) -> R,
    onBusinessFailure: (code: Int, msg: String) -> R
): R {
    return when (this) {
        is NetworkResult.Success -> onSuccess(data)
        is NetworkResult.TechnicalFailure -> onTechnicalFailure(exception)
        is NetworkResult.BusinessFailure -> onBusinessFailure(code, msg)
    }
}

/**
 * 仅在成功时返回数据，否则返回 null。
 * @return 成功时的 data（可能为 null），失败时为 null
 */
fun <T> NetworkResult<T>.getOrNull(): T? {
    return when (this) {
        is NetworkResult.Success -> data
        is NetworkResult.TechnicalFailure,
        is NetworkResult.BusinessFailure -> null
    }
}

/**
 * 成功时返回数据（可能为 null），失败时抛出异常。
 *
 * - TechnicalFailure：直接抛出内部的 [BaseNetException]。
 * - BusinessFailure：抛出 IllegalStateException，携带业务 code/msg。
 * @return 成功时的 data
 * @throws BaseNetException 技术失败时
 * @throws IllegalStateException 业务失败时
 */
fun <T> NetworkResult<T>.getOrThrow(): T? {
    return when (this) {
        is NetworkResult.Success -> data
        is NetworkResult.TechnicalFailure -> throw exception
        is NetworkResult.BusinessFailure -> throw IllegalStateException(
            "Business failure, code=$code, msg=$msg"
        )
    }
}

/**
 * 成功返回数据，若数据为 null 或失败则返回给定默认值。
 * @param defaultValue 默认值
 * @return 成功时的 data 或默认值
 */
fun <T> NetworkResult<T>.getOrDefault(defaultValue: T): T {
    return when (this) {
        is NetworkResult.Success -> data ?: defaultValue
        is NetworkResult.TechnicalFailure,
        is NetworkResult.BusinessFailure -> defaultValue
    }
}

/**
 * 失败时提供备选值，将失败转换为 [NetworkResult.Success]。
 *
 * 用于为失败结果提供默认值或降级策略：
 * ```kotlin
 * val result = executor.executeRequest { api.getUser(1) }
 *     .recover { defaultUser }
 * ```
 * @param transform 接收当前失败结果并返回备选值的函数
 * @return 成功时原样返回；失败时包装为 Success
 */
inline fun <T> NetworkResult<T>.recover(transform: (NetworkResult<T>) -> T): NetworkResult<T> {
    return when (this) {
        is NetworkResult.Success -> this
        else -> NetworkResult.Success(transform(this))
    }
}

/**
 * 失败时执行另一个返回 [NetworkResult] 的操作（如降级请求）。
 *
 * ```kotlin
 * val result = executor.executeRequest { api.getUser(1) }
 *     .recoverWith { executor.executeRequest { api.getUserFromCache(1) } }
 * ```
 * @param transform 接收当前失败结果并返回新 [NetworkResult] 的挂起函数
 * @return 成功时原样返回；失败时返回 transform 的结果
 */
suspend inline fun <T> NetworkResult<T>.recoverWith(
    crossinline transform: suspend (NetworkResult<T>) -> NetworkResult<T>
): NetworkResult<T> {
    return when (this) {
        is NetworkResult.Success -> this
        else -> transform(this)
    }
}

/**
 * 是否为成功结果。
 * @return true 表示成功
 */
fun <T> NetworkResult<T>.isSuccess(): Boolean = this is NetworkResult.Success<T>
