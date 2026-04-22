package com.answufeng.net.demo

import android.widget.LinearLayout
import com.answufeng.net.http.annotations.BaseUrl
import com.answufeng.net.http.annotations.Retry
import com.answufeng.net.http.annotations.Timeout

class DynamicConfigActivity : BaseDemoActivity() {

    override fun getTitleText() = "动态配置"

    override fun setupContent(layout: LinearLayout) {
        addBodyText("以下注解可在 Retrofit 接口方法上声明，由 OkHttp 拦截器链在运行时读取并生效。优先级高于全局 NetworkConfig。")

        addDivider()

        addSectionTitle("@BaseUrl — 按接口切换 BaseUrl")
        addBodyText("当部分接口使用不同域名时，无需创建多个 Retrofit 实例，直接在方法上添加注解即可。")
        addCodeBlock("""
interface FileApi {
    @BaseUrl("https://cdn.example.com/")
    @GET("files/{name}")
    suspend fun downloadFile(
        @Path("name") name: String
    ): ResponseBody
}""".trimIndent())

        addDivider()

        addSectionTitle("@Timeout — 按接口配置超时")
        addBodyText("某些接口（如大文件上传、长轮询）需要更长的超时时间，使用 @Timeout 注解按接口覆盖全局配置。")
        addCodeBlock("""
interface SlowApi {
    @Timeout(read = 60, write = 60)
    @POST("heavy-task")
    suspend fun heavyTask(
        @Body body: RequestBody
    ): GlobalResponse<Result>
}""".trimIndent())

        addDivider()

        addSectionTitle("@Retry — 按接口配置重试")
        addBodyText("不同接口对重试的需求不同：查询接口可重试，创建订单不应重试。使用 @Retry 精确控制。")
        addCodeBlock("""
interface RetryApi {
    @Retry(maxAttempts = 3, initialBackoffMs = 500)
    @GET("unstable-endpoint")
    suspend fun unstableEndpoint(): GlobalResponse<Data>

    @Retry(maxAttempts = 0)
    @POST("create-order")
    suspend fun createOrder(
        @Body order: Order
    ): GlobalResponse<OrderResult>

    @Retry(maxAttempts = 5, retryOnPost = true)
    @POST("submit-form")
    suspend fun submitForm(
        @Body form: FormData
    ): GlobalResponse<FormResult>
}""".trimIndent())

        addDivider()

        addSectionTitle("@SuccessCode — 按接口配置成功码")
        addBodyText("不同后端接口的成功码可能不同，默认使用 NetworkConfig.defaultSuccessCode，也可按接口覆盖。")
        addCodeBlock("""
interface MultiCodeApi {
    @SuccessCode(200)
    @GET("legacy-api")
    suspend fun legacyApi(): GlobalResponse<Data>

    @SuccessCode(0)
    @GET("new-api")
    suspend fun newApi(): GlobalResponse<Data>
}""".trimIndent())
    }
}
