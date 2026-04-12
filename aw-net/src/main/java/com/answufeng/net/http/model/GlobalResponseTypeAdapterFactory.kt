package com.answufeng.net.http.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.lang.reflect.ParameterizedType

/**
 * 为 GlobalResponse<T> 提供可配置字段映射反序列化能力。
 * @since 1.0.0
 */class GlobalResponseTypeAdapterFactory(
    private val mappingProvider: () -> ResponseFieldMapping
) : TypeAdapterFactory {

    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != GlobalResponse::class.java) return null

        val parameterizedType = type.type as? ParameterizedType ?: return null
        val dataType = parameterizedType.actualTypeArguments[0]
        val dataAdapter = gson.getAdapter(TypeToken.get(dataType))
        val jsonElementAdapter = gson.getAdapter(JsonElement::class.java)

        val adapter = object : TypeAdapter<GlobalResponse<Any?>>() {
            override fun write(out: JsonWriter, value: GlobalResponse<Any?>?) {
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
                    jsonElementAdapter.write(out, gson.toJsonTree(value.data))
                }
                out.endObject()
            }

            override fun read(input: JsonReader): GlobalResponse<Any?> {
                if (input.peek() == JsonToken.NULL) {
                    input.nextNull()
                    val mapping = mappingProvider()
                    return GlobalResponse(
                        code = mapping.failureCode,
                        msg = mapping.defaultMsg,
                        data = null
                    )
                }

                val root = jsonElementAdapter.read(input).asJsonObject
                val mapping = mappingProvider()

                val codeElement = findByKeys(root, listOf(mapping.codeKey) + mapping.codeFallbackKeys)
                val msgElement = findByKeys(root, listOf(mapping.msgKey) + mapping.msgFallbackKeys)
                val dataKeys = (listOf(mapping.dataKey) + mapping.dataFallbackKeys).distinct()

                val code = mapping.resolveCode(codeElement.toRawValue())
                val msg = msgElement?.takeIf { !it.isJsonNull }?.asString ?: mapping.defaultMsg
                val data = parseDataWithFallback(root, dataKeys, dataAdapter)

                return GlobalResponse(code = code, msg = msg, data = data)
            }
        }

        @Suppress("UNCHECKED_CAST") // 安全：adapter 处理 GlobalResponse<Any?>，运行时与 T 匹配
        return adapter as TypeAdapter<T>
    }

    private fun parseDataWithFallback(
        root: JsonObject,
        keys: List<String>,
        dataAdapter: TypeAdapter<*>
    ): Any? {
        @Suppress("UNCHECKED_CAST") // 安全：dataAdapter 通过 Gson.getAdapter(TypeToken.get(dataType)) 获取
        val adapter = dataAdapter as TypeAdapter<Any?>
        for (key in keys) {
            if (!root.has(key)) continue
            val element = root.get(key)
            if (element == null || element.isJsonNull) {
                return null
            }
            try {
                return adapter.fromJsonTree(element)
            } catch (_: Throwable) {
                // 当前 key 解析失败时继续尝试回退 key，提升异构响应兼容性
            }
        }
        return null
    }

    private fun findByKeys(
        obj: JsonObject,
        keys: List<String>
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
