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

    /**
     * 自定义文案提供器。项目层可覆盖此字段以实现多语言或自定义文案。
 */
    @Volatile
    var provider: (code: Int, defaultMessage: String) -> String = { _, default -> default }

    /**
     * 根据错误码获取文案。优先使用 [provider] 的返回值。
     * @param code 错误码
     * @param defaultMessage 默认文案
     * @return 最终文案
 */
    fun msg(code: Int, defaultMessage: String): String = provider(code, defaultMessage)
}
