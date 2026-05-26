package com.answufeng.net.http.model

import com.answufeng.net.http.exception.BusinessFailureException
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * 解析 `{code,msg,data}` 类业务包装 JSON，提取 `data` 或抛出 [BusinessFailureException]。
 */
object WrappedResponseParser {
    data class ParsedEnvelope<T>(
        val code: Int,
        val msg: String,
        val data: T?,
    )

    fun <T> parseDataOrThrow(
        root: JsonObject,
        dataType: Type,
        mapping: ResponseFieldMapping,
        effectiveSuccessCode: Int,
        gson: Gson,
    ): T? {
        val envelope: ParsedEnvelope<T?> = parseEnvelope(root, dataType, mapping, gson)
        if (envelope.code != effectiveSuccessCode) {
            throw BusinessFailureException(envelope.code, envelope.msg)
        }
        @Suppress("UNCHECKED_CAST")
        return envelope.data as T?
    }

    fun <T> parseEnvelope(
        root: JsonObject,
        dataType: Type,
        mapping: ResponseFieldMapping,
        gson: Gson,
    ): ParsedEnvelope<T?> {
        val codeElement = findByKeys(root, listOf(mapping.codeKey) + mapping.codeFallbackKeys)
        val msgElement = findByKeys(root, listOf(mapping.msgKey) + mapping.msgFallbackKeys)
        val dataKeys = (listOf(mapping.dataKey) + mapping.dataFallbackKeys).distinct()

        if (codeElement == null || codeElement.isJsonNull) {
            throw JsonParseException(
                "Wrapped response missing code field. Tried keys=" +
                    (listOf(mapping.codeKey) + mapping.codeFallbackKeys).distinct(),
            )
        }
        val code = mapping.resolveCode(codeElement.toRawValue())
        val msg = msgElement?.takeIf { !it.isJsonNull }?.asString ?: mapping.defaultMsg
        val dataAdapter = gson.getAdapter(TypeToken.get(dataType))
        val data =
            parseDataWithFallback(
                root,
                dataKeys,
                dataAdapter,
                dataType,
                mapping.parseEmbeddedJsonStringData,
            )
        @Suppress("UNCHECKED_CAST")
        return ParsedEnvelope(code, msg, data as T?)
    }

    private fun parseDataWithFallback(
        root: JsonObject,
        keys: List<String>,
        dataAdapter: com.google.gson.TypeAdapter<*>,
        dataType: Type,
        parseEmbeddedJsonStringData: Boolean,
    ): Any? {
        @Suppress("UNCHECKED_CAST")
        val adapter = dataAdapter as com.google.gson.TypeAdapter<Any?>
        for (key in keys) {
            if (!root.has(key)) continue
            val element = root.get(key)
            if (element == null || element.isJsonNull) {
                return null
            }
            try {
                val resolved =
                    resolveDataElement(
                        element,
                        dataType,
                        parseEmbeddedJsonStringData,
                    )
                return adapter.fromJsonTree(resolved)
            } catch (e: Exception) {
                throw JsonParseException("Wrapped response data field '$key' parse failed", e)
            }
        }
        return null
    }

    private fun resolveDataElement(
        element: JsonElement,
        dataType: Type,
        parseEmbeddedJsonStringData: Boolean,
    ): JsonElement {
        if (!parseEmbeddedJsonStringData) return element
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return element

        val rawType = TypeToken.get(dataType).rawType
        if (rawType == String::class.java) return element

        val text = element.asString
        if (text.isBlank()) return element

        return try {
            com.google.gson.JsonParser.parseString(text)
        } catch (_: Exception) {
            element
        }
    }

    private fun findByKeys(
        obj: JsonObject,
        keys: List<String>,
    ): JsonElement? {
        for (key in keys) {
            if (obj.has(key)) return obj.get(key)
        }
        return null
    }

    private fun JsonElement?.toRawValue(): Any? {
        if (this == null || this.isJsonNull) return null
        if (!this.isJsonPrimitive) return this

        val primitive = this.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asNumber
            primitive.isString -> primitive.asString
            else -> null
        }
    }
}
