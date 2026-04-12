package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.model.GlobalResponse
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ProgressInfo
import com.answufeng.net.http.util.NetworkExecutor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import retrofit2.Retrofit
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class UploadActivity : BaseDemoActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvProgress: TextView
    private lateinit var tvResult: TextView

    override fun getTitleText() = "⬆️ 文件上传"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("上传文件")
        addBodyText("创建测试文件并上传，实时显示上传进度。使用 Multipart 上传。")

        val btnUpload = MaterialButton(this).apply {
            text = "开始上传"
            setOnClickListener { performUpload() }
        }
        layout.addView(btnUpload)

        addDivider()

        addSectionTitle("上传进度")

        progressBar = LinearProgressIndicator(this).apply {
            max = 100
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layout.addView(this, lp)
        }

        tvProgress = addBodyText("等待上传...")

        addSectionTitle("上传结果")

        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(this, lp)
        }

        tvResult = TextView(this).apply {
            text = "尚未开始上传"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            card.addView(this)
        }
    }

    private fun performUpload() {
        progressBar.progress = 0
        tvProgress.text = "上传中..."
        tvResult.text = "⏳ 上传中..."

        val progressFlow = MutableSharedFlow<ProgressInfo>(replay = 1)

        lifecycleScope.launch {
            progressFlow.collect { info ->
                progressBar.progress = info.progress
                tvProgress.text = "进度: ${info.progress}%"
            }
        }

        lifecycleScope.launch {
            val testFile = File(cacheDir, "upload_test.txt")
            testFile.writeText("Hello from aw-net! 这是一个测试上传文件。\n时间: ${System.currentTimeMillis()}")

            val result = executor.uploadFile(
                file = testFile,
                partName = "file",
                progressFlow = progressFlow
            ) { part ->
                retrofit.create(UploadApi::class.java).uploadFile(part)
            }

            when (result) {
                is NetworkResult.Success -> {
                    tvResult.text = buildString {
                        appendLine("✅ 上传完成")
                        appendLine("  文件: ${testFile.name}")
                        appendLine("  大小: ${testFile.length()} bytes")
                    }
                }
                is NetworkResult.TechnicalFailure -> {
                    tvResult.text = "❌ 错误: ${result.exception.message}"
                }
                is NetworkResult.BusinessFailure -> {
                    tvResult.text = "⚠️ 业务错误: ${result.code} - ${result.msg}"
                }
            }
        }
    }
}

interface UploadApi {
    @Multipart
    @POST("upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): GlobalResponse<String>
}
