package com.answufeng.net.demo

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.net.http.annotations.BaseUrl
import com.answufeng.net.http.annotations.Retry
import com.answufeng.net.http.annotations.Timeout

class DynamicConfigActivity : AppCompatActivity() {

    private val tv by lazy { TextView(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Dynamic Config"
        val scrollView = ScrollView(this)
        scrollView.setPadding(24, 24, 24, 24)
        scrollView.addView(tv)
        setContentView(scrollView)

        val sb = StringBuilder()
        sb.appendLine("=== Dynamic Config Demo ===")
        sb.appendLine()
        sb.appendLine("1. @BaseUrl - Override base URL per API method:")
        sb.appendLine("""
            |interface FileApi {
            |    @BaseUrl("https://cdn.example.com/")
            |    @GET("files/{name}")
            |    suspend fun downloadFile(@Path("name") name: String): ResponseBody
            |}
        """.trimMargin())
        sb.appendLine()
        sb.appendLine("2. @Timeout - Override timeout per API method:")
        sb.appendLine("""
            |interface SlowApi {
            |    @Timeout(read = 60, write = 60)
            |    @POST("heavy-task")
            |    suspend fun heavyTask(@Body body: RequestBody): GlobalResponse<Result>
            |}
        """.trimMargin())
        sb.appendLine()
        sb.appendLine("3. @Retry - Override retry config per API method:")
        sb.appendLine("""
            |interface RetryApi {
            |    @Retry(maxAttempts = 3, initialBackoffMs = 500)
            |    @GET("unstable-endpoint")
            |    suspend fun unstableEndpoint(): GlobalResponse<Data>
            |
            |    @Retry(maxAttempts = 0)
            |    @POST("create-order")
            |    suspend fun createOrder(@Body order: Order): GlobalResponse<OrderResult>
            |
            |    @Retry(maxAttempts = 5, retryOnPost = true)
            |    @POST("submit-form")
            |    suspend fun submitForm(@Body form: FormData): GlobalResponse<FormResult>
            |}
        """.trimMargin())

        tv.text = sb.toString()
    }
}
