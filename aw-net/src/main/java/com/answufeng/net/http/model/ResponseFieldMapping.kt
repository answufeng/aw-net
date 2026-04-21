package com.answufeng.net.http.model

/**
 * 全局响应字段映射配置。
 *
 * 默认零配置即按 {code,msg,data} 解析。
 * 对于后端字段名不统一的项目，可在 NetworkConfig 中全局设置，不需要逐接口适配。
 */
data class ResponseFieldMapping(
    val codeKey: String = "code",
    val msgKey: String = "msg",
    val dataKey: String = "data",
    val codeFallbackKeys: List<String> = emptyList(),
    val msgFallbackKeys: List<String> = emptyList(),
    val dataFallbackKeys: List<String> = emptyList(),
    val successCode: Int = 0,
    val failureCode: Int = -1,
    val defaultMsg: String = "",
    val codeValueConverter: ((rawCode: Any?, mapping: ResponseFieldMapping) -> Int)? = null
) {
    /**
     * 将原始 code 值（可能是 Number/Boolean/String/null）解析为 Int。
     * 优先使用 [codeValueConverter]；否则按类型自动转换。
     * @param rawCode 原始 code 值
     * @return 解析后的 Int 值
 */
    fun resolveCode(rawCode: Any?): Int {
        codeValueConverter?.let { return it(rawCode, this) }
        return when (rawCode) {
            is Number -> rawCode.toInt()
            is Boolean -> if (rawCode) successCode else failureCode
            is String -> {
                rawCode.toIntOrNull()
                    ?: when {
                        rawCode.equals("true", ignoreCase = true) -> successCode
                        rawCode.equals("false", ignoreCase = true) -> failureCode
                        else -> failureCode
                    }
            }
            null -> failureCode
            else -> failureCode
        }
    }
}

