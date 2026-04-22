package com.answufeng.net.http.model

/**
 * [NetEvent] 所处的阶段：一次受追踪的请求对应一对 `START` 与 `END`。
 */
enum class NetEventStage {
    /** 开始执行（发请求/上传/下载前）。 */
    START,
    /** 结束（已得到 [com.answufeng.net.http.model.NetworkResult] 或失败）。 */
    END
}

/**
 * 经 [com.answufeng.net.http.util.NetTracker] 上报的埋点；**不包含**请求 URL 等，避免在日志/埋点中重复大段网络信息，请结合自有 APM/埋点体系扩展维度。
 *
 * @param name 与 [com.answufeng.net.http.util.RequestExecutor] / [com.answufeng.net.http.util.NetworkExecutor] 上 `name` 参数或内部约定名一致
 * @param stage 开始或结束
 * @param timestampMs 事件发生时间（墙钟，`System.currentTimeMillis`）
 * @param durationMs 仅 [END] 时有值；耗时（ms），由 END 与 START 的墙钟差近似得到
 * @param resultType 库内用字符串，如 `SUCCESS` / `TECHNICAL_FAILURE` / `BUSINESS_FAILURE`
 * @param errorCode 技术异常码或业务 `code`；成功时常为 `null`
 * @param tag 与执行 API 的 `tag` 参数一致，便于在监控侧按业务线筛选
 */
data class NetEvent(
    val name: String?,
    val stage: NetEventStage,
    val timestampMs: Long,
    val durationMs: Long? = null,
    val resultType: String? = null,
    val errorCode: Int? = null,
    val tag: String? = null
)
