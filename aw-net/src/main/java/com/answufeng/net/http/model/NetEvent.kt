package com.answufeng.net.http.model

/**
 * 网络事件阶段：开始或结束。
 * @since 1.0.0
 */enum class NetEventStage {
    /** 请求开始 
    * @since 1.0.0
 */    START,
    /** 请求结束 
    * @since 1.0.0
 */    END
}

/**
 * 网络请求监控事件。
 *
 * @param name 可选的请求名称标识（如 "getUserInfo"），默认可为 null
 * @param stage 事件阶段：开始或结束
 * @param timestampMs 事件发生时间（毫秒时间戳）
 * @param durationMs 请求持续时间（毫秒），仅在 [NetEventStage.END] 阶段有效
 * @param resultType 结果类型标识，如 SUCCESS / TECHNICAL_FAILURE / BUSINESS_FAILURE
 * @param errorCode 技术或业务错误码，成功时为 null
 * @param tag 可选的业务上下文标识（例如："uploadUserAvatar"），便于外部监控关联请求
 * @since 1.0.0
 */data class NetEvent(
    val name: String?,
    val stage: NetEventStage,
    val timestampMs: Long,
    val durationMs: Long? = null,
    val resultType: String? = null,
    val errorCode: Int? = null,
    val tag: String? = null
)
