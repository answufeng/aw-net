package com.answufeng.net.demo

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.util.NetworkExecutor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class UploadDemoActivity : BaseDemoActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private lateinit var tvResult: TextView

    override fun getTitleText() = "📤 文件上传"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("上传演示")

        MaterialButton(this).apply {
            text = "上传测试文件"
            setOnClickListener { uploadFile() }
            backgroundTintList = getColorStateList(R.color.primary)
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("上传结果")

        val resultCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(200)
            )
            layout.addView(this, lp)
        }

        tvResult = TextView(this).apply {
            text = "点击上方按钮开始上传..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            resultCard.addView(this)
        }
    }

    private fun uploadFile() {
        tvResult.text = "⏳ 准备上传..."

        // 创建测试文件
        val testFile = createTestFile()
        if (!testFile.exists()) {
            tvResult.text = "❌ 测试文件创建失败"
            return
        }

        lifecycleScope.launch {
            tvResult.text = "⏳ 正在上传..."
            
            val requestFile = RequestBody.create(
                MediaType.parse("text/plain"),
                testFile
            )
            
            val body = MultipartBody.Part.createFormData(
                "file",
                testFile.name,
                requestFile
            )

            val api = retrofit.create(UploadApi::class.java)
            val result: NetworkResult<UploadResponse> = executor.executeRawRequest {
                api.uploadFile(body)
            }

            tvResult.text = formatResult("POST /upload", result)
        }
    }

    private fun createTestFile(): File {
        val file = File(filesDir, "test_upload.txt")
        file.writeText("Hello from aw-net upload test!\nTimestamp: ${System.currentTimeMillis()}")
        return file
    }

    private fun <T> formatResult(endpoint: String, result: NetworkResult<T>): String {
        val sb = java.lang.StringBuilder()
        sb.appendLine("── $endpoint ──")
        sb.appendLine()
        result.fold(
            onSuccess = { data ->
                sb.appendLine("✅ 上传成功")
                sb.appendLine(data.toString())
            },
            onTechnicalFailure = { ex ->
                sb.appendLine("❌ 技术错误")
                sb.appendLine("  code: ${ex.code}")
                sb.appendLine("  msg: ${ex.message}")
            },
            onBusinessFailure = { code, msg ->
                sb.appendLine("⚠️ 业务错误")
                sb.appendLine("  code: $code")
                sb.appendLine("  msg: $msg")
            }
        )
        return sb.toString()
    }

    interface UploadApi {
        @retrofit2.http.Multipart
        @retrofit2.http.POST("/upload")
        suspend fun uploadFile(
            @retrofit2.http.Part file: MultipartBody.Part
        ): UploadResponse
    }

    data class UploadResponse(
        val success: Boolean,
        val message: String,
        val fileUrl: String? = null
    )
}
