package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.NetLogger
import com.answufeng.net.http.annotations.NetworkConfig
import com.answufeng.net.http.annotations.NetworkConfigProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * 漂亮的日志打印器。
 *
 * 职责：将 OkHttp 的原始日志美化为易读的格式，并交给 [NetLogger] 输出。
 * 脱敏规则：匹配 [NetworkConfig.sensitiveHeaders] 中登记的 Header 名称（忽略大小写），
 * 将其值替换为 `****(masked)`，防止 Token / Cookie 等敏感信息泄露到日志中。
 *
 * @param netLogger 最终日志输出代理
 * @param configProvider 运行时网络配置提供者，用于读取可配置的脱敏 Header 列表
 */
class PrettyNetLogger(
    private val netLogger: NetLogger,
    private val configProvider: NetworkConfigProvider? = null
) : okhttp3.logging.HttpLoggingInterceptor.Logger {

    companion object {
        private const val TAG = "NetworkLog"
        private const val JSON_INDENT = 4
        private const val MAX_LOG_LENGTH = 4000
        private const val LARGE_JSON_THRESHOLD = 4000
        private const val MAX_RECURSION_DEPTH = 10
    }

    override fun log(message: String) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.length > LARGE_JSON_THRESHOLD) {
            netLogger.d(TAG, maskAndTruncate(trimmedMessage))
            return
        }
        if (trimmedMessage.startsWith("{") || trimmedMessage.startsWith("[")) {
            try {
                val maskedJson = if (trimmedMessage.startsWith("{")) {
                    val jsonObj = JSONObject(trimmedMessage)
                    maskSensitiveBodyFields(jsonObj, 0)
                    jsonObj.toString(JSON_INDENT)
                } else {
                    val jsonArr = JSONArray(trimmedMessage)
                    maskSensitiveBodyFieldsInArray(jsonArr, 0)
                    jsonArr.toString(JSON_INDENT)
                }
                maskedJson.lines().forEach { line ->
                    netLogger.d(TAG, truncateIfTooLong(line))
                }
            } catch (e: Exception) {
                netLogger.d(TAG, maskAndTruncate(trimmedMessage))
            }
        } else {
            netLogger.d(TAG, maskAndTruncate(trimmedMessage))
        }
    }

    /**
     * 对日志做两件事：
     * 1. 脱敏：对 [NetworkConfig.sensitiveHeaders] 配置的敏感 Header 进行掩码处理；
     * 2. 截断：对超长日志做截断，避免占用过多日志缓冲区。
 */
    private fun maskAndTruncate(raw: String): String {
        val masked = maskSensitiveHeader(raw)
        return truncateIfTooLong(masked)
    }

    /**
     * 检测日志行是否为敏感 Header 并脱敏。
     *
     * 匹配规则：日志行以 `HeaderName:` 开头（忽略大小写），且 HeaderName 在
     * [NetworkConfig.sensitiveHeaders] 集合中时，将值替换为 `****(masked)`。
 */
    private fun maskSensitiveHeader(message: String): String {
        val colonIndex = message.indexOf(':')
        if (colonIndex <= 0) return message

        val headerName = message.substring(0, colonIndex).trim()
        val sensitiveHeaders = configProvider?.current?.sensitiveHeaders
            ?: NetworkConfig.DEFAULT_SENSITIVE_HEADERS

        val isSensitive = sensitiveHeaders.any { it.equals(headerName, ignoreCase = true) }
        return if (isSensitive) "$headerName: ****(masked)" else message
    }

    private fun truncateIfTooLong(message: String): String {
        return if (message.length > MAX_LOG_LENGTH) {
            message.substring(0, MAX_LOG_LENGTH) + " ... (truncated)"
        } else {
            message
        }
    }

    /**
     * 递归遍历 JSONObject，将 [NetworkConfig.sensitiveBodyFields] 中的敏感字段值替换为掩码。
 */
    private fun maskSensitiveBodyFields(jsonObj: JSONObject, depth: Int) {
        if (depth >= MAX_RECURSION_DEPTH) return
        val sensitiveFields = configProvider?.current?.sensitiveBodyFields
            ?: NetworkConfig.DEFAULT_SENSITIVE_BODY_FIELDS
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (sensitiveFields.any { it.equals(key, ignoreCase = true) }) {
                jsonObj.put(key, "****(masked)")
            } else {
                val value = jsonObj.opt(key)
                if (value is JSONObject) {
                    maskSensitiveBodyFields(value, depth + 1)
                } else if (value is JSONArray) {
                    maskSensitiveBodyFieldsInArray(value, depth + 1)
                }
            }
        }
    }

    /**
     * 递归遍历 JSONArray，对其中的 JSONObject 元素进行敏感字段脱敏。
 */
    private fun maskSensitiveBodyFieldsInArray(jsonArr: JSONArray, depth: Int) {
        if (depth >= MAX_RECURSION_DEPTH) return
        for (i in 0 until jsonArr.length()) {
            when (val item = jsonArr.opt(i)) {
                is JSONObject -> maskSensitiveBodyFields(item, depth + 1)
                is JSONArray -> maskSensitiveBodyFieldsInArray(item, depth + 1)
            }
        }
    }
}
