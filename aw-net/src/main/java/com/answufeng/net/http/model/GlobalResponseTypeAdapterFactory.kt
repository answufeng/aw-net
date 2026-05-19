package com.answufeng.net.http.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 为 GlobalResponse<T> 提供可配置字段映射反序列化能力。
 * 序列化 [write] 与 [read] 均走同一 `data` 类型的 [TypeAdapter]，避免 `toJsonTree` 与强类型注册器不一致。
 */
class GlobalResponseTypeAdapterFactory(
    private val mappingProvider: () -> ResponseFieldMapping,
) : TypeAdapterFactory {
    override fun <T> create(
        gson: Gson,
        type: TypeToken<T>,
    ): TypeAdapter<T>? {
        if (type.rawType != GlobalResponse::class.java) return null

        val parameterizedType = type.type as? ParameterizedType ?: return null
        val dataType = parameterizedType.actualTypeArguments[0]
        val dataAdapter = gson.getAdapter(TypeToken.get(dataType))
        val jsonElementAdapter = gson.getAdapter(JsonElement::class.java)

        val adapter =
            object : TypeAdapter<GlobalResponse<Any?>>() {
                override fun write(
                    out: JsonWriter,
                    value: GlobalResponse<Any?>?,
                ) {
                    if (value == null) {
                        out.nullValue()
                        return
                    }
                    val mapping = mappingProvider()
                    out.beginObject()
                    out.name(mapping.codeKey).value(value.code)
                    out.name(mapping.msgKey).value(value.msg)
                    out.name(mapping.dataKey)
                    if (value.data == null) {
                        out.nullValue()
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (dataAdapter as TypeAdapter<Any?>).write(out, value.data)
                    }
                    out.endObject()
                }

                override fun read(input: JsonReader): GlobalResponse<Any?> {
                    if (input.peek() == JsonToken.NULL) {
                        input.nextNull()
                        val m = mappingProvider()
                        return GlobalResponse(
                            code = m.failureCode,
                            msg = m.defaultMsg,
                            data = null,
                        )
                    }

                    val rootElement = jsonElementAdapter.read(input)
                    if (!rootElement.isJsonObject) {
                        throw JsonParseException("GlobalResponse must be a JSON object, actual=$rootElement")
                    }
                    val root = rootElement.asJsonObject
                    val mapping = mappingProvider()

                    val codeElement = findByKeys(root, listOf(mapping.codeKey) + mapping.codeFallbackKeys)
                    val msgElement = findByKeys(root, listOf(mapping.msgKey) + mapping.msgFallbackKeys)
                    val dataKeys = (listOf(mapping.dataKey) + mapping.dataFallbackKeys).distinct()

                    if (codeElement == null || codeElement.isJsonNull) {
                        throw JsonParseException(
                            "GlobalResponse missing code field. Tried keys=" +
                                (listOf(mapping.codeKey) + mapping.codeFallbackKeys).distinct(),
                        )
                    }
                    val code = mapping.resolveCode(codeElement.toRawValue())
                    val msg = msgElement?.takeIf { !it.isJsonNull }?.asString ?: mapping.defaultMsg
                    val data =
                        parseDataWithFallback(
                            root,
                            dataKeys,
                            dataAdapter,
                            dataType,
                            mapping.parseEmbeddedJsonStringData,
                        )

                    return GlobalResponse(code = code, msg = msg, data = data)
                }
            }

        @Suppress("UNCHECKED_CAST")
        return adapter as TypeAdapter<T>
    }

    private fun parseDataWithFallback(
        root: JsonObject,
        keys: List<String>,
        dataAdapter: TypeAdapter<*>,
        dataType: Type,
        parseEmbeddedJsonStringData: Boolean,
    ): Any? {
        @Suppress("UNCHECKED_CAST")
        val adapter = dataAdapter as TypeAdapter<Any?>
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
                throw JsonParseException("GlobalResponse data field '$key' parse failed", e)
            }
        }
        return null
    }

    /**
     * 后端有时将 `data` 设为 JSON 字符串（内嵌对象），Gson 无法直接把字符串 primitive 映射为 POJO。
     * 在 T 非 [String] 时，将字符串再解析一层；T 为 [String] 时保留原始字符串值。
     */
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
