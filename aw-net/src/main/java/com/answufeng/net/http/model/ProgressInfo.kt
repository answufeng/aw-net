package com.answufeng.net.http.model

/**
 * 传输进度实体
 * @param progress 0-100 的整数百分比；当 totalSize 未知时为 -1
 * @param currentSize 当前已传输大小（字节）
 * @param totalSize 总大小（字节）；未知时为 -1
 * @param isDone 是否完成
 * @param seq 事件序号（递增），用于检测丢包或乱序；默认 0 表示未使用序号
 */
data class ProgressInfo(
    val progress: Int,
    val currentSize: Long,
    val totalSize: Long,
    val isDone: Boolean = false,
    val seq: Long = 0L
)
