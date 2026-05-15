package com.answufeng.net.http.util

/**
 * 控制下载失败或取消时部分写入文件的处理策略。
 */
enum class DownloadFailureStrategy {
    DELETE_PARTIAL,
    KEEP_PARTIAL,
}
