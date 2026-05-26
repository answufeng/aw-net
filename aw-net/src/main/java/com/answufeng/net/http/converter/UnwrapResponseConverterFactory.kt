package com.answufeng.net.http.converter

import com.answufeng.net.http.annotations.RawResponse
import com.answufeng.net.http.annotations.ResponseFields
import com.answufeng.net.http.annotations.SuccessCode
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.exception.BusinessFailureException
import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.GlobalResponse
import com.answufeng.net.http.model.ResponseFieldMapping
import com.answufeng.net.http.model.WrappedResponseParser
import com.answufeng.net.http.model.toMapping
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 将业务包装 JSON 自动解包为 Retrofit 声明类型（如 `User`、`List<User>`）。
 *
 * - 带 [RawResponse] 的方法交给后续 Gson Converter 直接解析。
 * - [GlobalResponse] / [BaseResponse] 由 [GlobalResponseTypeAdapterFactory] 处理。
 */
class UnwrapResponseConverterFactory(
    private val gson: Gson,
    private val configProvider: NetworkConfigProvider,
) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (annotations.any { it is RawResponse }) return null
        if (isWrappedResponseType(type)) return null
        if (type == ResponseBody::class.java) return null
        if (type == Unit::class.java || type == Void::class.java) return null

        val globalMapping = configProvider.current.responseFieldMapping
        val mapping =
            annotations.filterIsInstance<ResponseFields>().firstOrNull()?.toMapping(globalMapping)
                ?: globalMapping
        val fieldsAnnotation = annotations.filterIsInstance<ResponseFields>().firstOrNull()
        val successAnnotation = annotations.filterIsInstance<SuccessCode>().firstOrNull()?.value
        val effectiveSuccessCode =
            when {
                fieldsAnnotation != null && fieldsAnnotation.successCode != Int.MIN_VALUE ->
                    mapping.successCode
                successAnnotation != null -> successAnnotation
                else -> configProvider.current.defaultSuccessCode
            }

        return object : Converter<ResponseBody, Any?> {
            override fun convert(value: ResponseBody): Any? {
                val body = value.string()
                if (body.isBlank()) {
                    throw JsonParseException("Empty response body for $type")
                }
                val root =
                    gson.fromJson(body, JsonElement::class.java)
                        ?: throw JsonParseException("Response is not valid JSON")
                if (!root.isJsonObject) {
                    throw JsonParseException("Wrapped response must be a JSON object, actual=$root")
                }
                return try {
                    WrappedResponseParser.parseDataOrThrow(
                        root = root.asJsonObject,
                        dataType = type,
                        mapping = mapping,
                        effectiveSuccessCode = effectiveSuccessCode,
                        gson = gson,
                    )
                } catch (e: BusinessFailureException) {
                    throw e
                }
            }
        }
    }

    private fun rawClass(type: Type): Class<*>? =
        when (type) {
            is ParameterizedType -> type.rawType as? Class<*>
            is Class<*> -> type
            else -> null
        }

    private fun isWrappedResponseType(type: Type): Boolean {
        val raw = rawClass(type) ?: return false
        if (raw == Response::class.java || raw == ResponseBody::class.java) return true
        return BaseResponse::class.java.isAssignableFrom(raw) ||
            GlobalResponse::class.java.isAssignableFrom(raw)
    }
}
