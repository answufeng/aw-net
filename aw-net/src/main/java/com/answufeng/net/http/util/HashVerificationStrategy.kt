package com.answufeng.net.http.util

/**
 * 文件下载后 Hash 校验不匹配时的处理策略。
 * @since 1.0.0
 */
enum class HashVerificationStrategy {
    /** Hash 不匹配时删除已下载文件 
    * @since 1.0.0
 */
    DELETE_ON_MISMATCH,
    /** Hash 不匹配时保留已下载文件 
    * @since 1.0.0
 */
    KEEP_ON_MISMATCH
}
