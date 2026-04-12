package com.answufeng.net.http.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * 轮询工具：周期性执行 [block] 并发射结果。
 *
 * 当 [stopWhen] 返回 true 或达到 [maxAttempts] 次数时停止。
 *
 * @param periodMillis 轮询间隔（毫秒）
 * @param maxAttempts 最大轮询次数，默认无限
 * @param stopWhen 停止条件判断函数
 * @param block 每次轮询执行的挂起代码块
 * @return 发射结果的 Flow
 * @since 1.0.0
 */
fun <T> pollingFlow(
    periodMillis: Long,
    maxAttempts: Long = Long.MAX_VALUE,
    stopWhen: suspend (T) -> Boolean = { false },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> T
): Flow<T> {
    require(periodMillis > 0) { "periodMillis must be positive" }
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    return flow {
        var count = 0L
        while (count < maxAttempts) {
            val value = block()
            emit(value)
            if (stopWhen(value)) break
            count++
            if (count < maxAttempts) delay(periodMillis)
        }
    }.flowOn(dispatcher)
}
