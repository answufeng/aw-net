package com.answufeng.net.http.interceptor

import com.answufeng.net.http.annotations.SuccessCode
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import retrofit2.Invocation

class SuccessCodeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val successCode = invocation?.method()?.getAnnotation(SuccessCode::class.java)?.value
        val response = chain.proceed(request)

        if (successCode == null || !response.isSuccessful || response.body == null) {
            return response
        }

        return try {
            val originalBody = response.body!!
            val contentType = originalBody.contentType()
            val originalJson = originalBody.string()

            if (!originalJson.trimStart().startsWith("{")) {
                return response.newBuilder()
                    .body(originalJson.toResponseBody(contentType))
                    .build()
            }

            val json = JSONObject(originalJson)
            json.put(RESOLVED_SUCCESS_CODE_KEY, successCode)
            val modifiedJson = json.toString()

            response.newBuilder()
                .body(modifiedJson.toResponseBody(contentType))
                .build()
        } catch (_: Exception) {
            response
        }
    }

    companion object {
        const val RESOLVED_SUCCESS_CODE_KEY = "_resolvedSuccessCode"
    }
}
