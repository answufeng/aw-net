package com.answufeng.net.http.util

/**
 * 网络错误文案提供器。
 *
 * - 默认直接返回基础库内部定义的中文提示；
 * - 项目层可在 Application 启动时覆盖 [provider]，实现多语言或自定义文案。
 *
 * 例如：
 *
 * ```kotlin
 * NetErrorMessage.provider = { code, defaultMsg ->
 *     when (code) {
 *         -1 -> context.getString(R.string.error_timeout)
 *         else -> defaultMsg
 *     }
 * }
 * ```
 */
object NetErrorMessage {

    @Volatile
    var provider: (code: Int, defaultMessage: String) -> String = { _, default -> default }

    fun msg(code: Int, defaultMessage: String): String = provider(code, defaultMessage)
}
