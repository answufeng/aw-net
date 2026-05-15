package com.answufeng.net.websocket

/**
 * WebSocket 重连策略接口，允许自定义重连延迟和是否继续重试的逻辑。
 *
 * 库内默认提供 [ExponentialBackoffStrategy]（指数退避+抖动）实现。
 * 项目层可实现此接口以定制自己的重连策略（如固定间隔、斐波那契退避等），
 * 并通过 [WebSocketManager.Config.reconnectStrategy] 注入。
 */
interface ReconnectStrategy {
    /**
     * 根据当前重连次数计算下次重连的延迟时间（毫秒）。
     *
     * @param attempt 即将执行的重连次数（从 1 开始）
     * @return 延迟毫秒数
     */
    fun computeDelayMs(attempt: Int): Long

    /**
     * 判断是否应该继续重试。
     *
     * @param attempt 即将执行的重连次数（从 1 开始）
     * @return true 表示可以继续重试
     */
    fun shouldRetry(attempt: Int): Boolean

    companion object {
        /** 默认重连策略：指数退避 + 抖动 */
        val DEFAULT = ExponentialBackoffStrategy()
    }
}

/**
 * 指数退避重连策略，带随机抖动以避免惊群效应。
 *
 * 延迟公式：`baseDelayMs * 2^(attempt-1) * jitter`，结果限制在 [1_000, maxDelayMs] 范围内。
 *
 * @param maxReconnectAttempts 最大重连次数，0 表示无限次（默认 0）
 * @param baseDelayMs 基础延迟毫秒数（默认 1000）
 * @param maxDelayMs 最大延迟毫秒数（默认 30000）
 * @param maxBackoffShift 退避位移上限，防止溢出（默认 10，即最多 2^10 = 1024 倍）
 * @param jitterBase 抖动基数（默认 0.9）
 * @param jitterRange 抖动范围（默认 0.2，最终因子在 [0.9, 1.1] 之间）
 */
class ExponentialBackoffStrategy(
    private val maxReconnectAttempts: Int = 0,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L,
    private val maxBackoffShift: Int = 10,
    private val jitterBase: Double = 0.9,
    private val jitterRange: Double = 0.2,
) : ReconnectStrategy {
    override fun computeDelayMs(attempt: Int): Long {
        val baseDelay = baseDelayMs * (1L shl minOf(attempt - 1, maxBackoffShift))
        val jitterFactor = jitterBase + kotlin.random.Random.nextDouble() * jitterRange
        val delayWithJitter = (baseDelay * jitterFactor).toLong()
        return delayWithJitter.coerceIn(1_000L, maxDelayMs)
    }

    override fun shouldRetry(attempt: Int): Boolean {
        if (maxReconnectAttempts <= 0) return true
        return attempt < maxReconnectAttempts
    }
}
