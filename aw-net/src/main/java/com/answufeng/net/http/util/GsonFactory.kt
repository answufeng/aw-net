package com.answufeng.net.http.util

import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.converter.UnwrapResponseConverterFactory
import com.answufeng.net.http.model.GlobalResponseTypeAdapterFactory
import com.answufeng.net.http.model.LenientStringTypeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type

/**
 * 统一 Gson / Retrofit Converter 装配（Hilt 与 [com.answufeng.net.AwNet] 共用）。
 */
object GsonFactory {
    fun createGson(configProvider: NetworkConfigProvider): Gson {
        return GsonBuilder()
            .registerTypeAdapter(String::class.java, LenientStringTypeAdapter())
            .registerTypeAdapterFactory(
                GlobalResponseTypeAdapterFactory {
                    configProvider.current.responseFieldMapping
                },
            )
            .create()
    }

    fun createConverterFactory(configProvider: NetworkConfigProvider): Converter.Factory {
        val gson = createGson(configProvider)
        return object : Converter.Factory() {
            private val unwrap = UnwrapResponseConverterFactory(gson, configProvider)
            private val gsonFactory = GsonConverterFactory.create(gson)

            override fun responseBodyConverter(
                type: Type,
                annotations: Array<out Annotation>,
                retrofit: Retrofit,
            ): Converter<okhttp3.ResponseBody, *>? {
                return unwrap.responseBodyConverter(type, annotations, retrofit)
                    ?: gsonFactory.responseBodyConverter(type, annotations, retrofit)
            }

            override fun requestBodyConverter(
                type: Type,
                parameterAnnotations: Array<out Annotation>,
                methodAnnotations: Array<out Annotation>,
                retrofit: Retrofit,
            ): Converter<*, okhttp3.RequestBody>? {
                return gsonFactory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
            }

            override fun stringConverter(
                type: Type,
                annotations: Array<out Annotation>,
                retrofit: Retrofit,
            ): Converter<*, String>? {
                return gsonFactory.stringConverter(type, annotations, retrofit)
            }
        }
    }
}
