package com.answufeng.net.http.annotations

/**
 * 用于 Retrofit 接口方法的注解，指定该接口的重试策略。
 * 优先级高于 [NetworkConfig.retryMaxAttempts] / [NetworkConfig.retryInitialBackoffMs] 中的全局配置。
 *
 * - 若 [maxAttempts] = 0，表示该接口禁止重试（即使全局开启了 RetryInterceptor）。
 * - 未标注此注解的接口使用全局策略。
 *
 * 使用示例：
 * ```kotlin
 * interface ApiService {
 *     // 指定此接口最多重试 5 次，初始退避 500ms
 *     @Retry(maxAttempts = 5, initialBackoffMs = 500)
 *     @GET("api/slow-endpoint")
 *     suspend fun slowEndpoint(): GlobalResponse<Data>
 *
 *     // 禁止重试（如非幂等 POST）
 *     @Retry(maxAttempts = 0)
 *     @POST("api/create-order")
 *     suspend fun createOrder(@Body body: OrderRequest): GlobalResponse<Order>
 * }
 * ```
 *
 * @param maxAttempts 最大重试次数（不含首次请求）。0 = 禁止重试，-1 = 使用全局配置
 * @param initialBackoffMs 初始退避毫秒数。-1 = 使用全局配置
 * @param maxBackoffMs 最大退避毫秒数。-1 = 使用全局配置
 * @param retryOnPost 是否允许 POST 请求重试。默认 false，仅在明确标注时才允许
 * @since 1.0.0
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Retry(
    val maxAttempts: Int = -1,
    val initialBackoffMs: Long = -1,
    val maxBackoffMs: Long = -1,
    val retryOnPost: Boolean = false
)
