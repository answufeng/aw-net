package com.answufeng.net.demo

import com.answufeng.net.http.annotations.BaseUrl
import com.answufeng.net.http.model.GlobalResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface OcrApi {

    @FormUrlEncoded
    @BaseUrl("http://139.199.221.131:8086/")
    @POST("glass/ai/ocr-container")
    suspend fun recognizeContainer(
        @Field("base64Image") base64Image: String,
    ): GlobalResponse<String>
}
