package com.answufeng.net.demo

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.fold
import com.answufeng.net.http.util.NetworkExecutor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DownloadDemoActivity : BaseDemoActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private lateinit var tvResult: TextView

    override fun getTitleText() = "文件下载"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("下载演示")

        MaterialButton(this).apply {
            text = "下载测试文件"
            setOnClickListener { downloadFile() }
            backgroundTintList = getColorStateList(R.color.primary)
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("下载结果")

        val resultCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(200)
            )
            layout.addView(this, lp)
        }

        tvResult = TextView(this).apply {
            text = "点击上方按钮开始下载..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            resultCard.addView(this)
        }
    }

    private fun downloadFile() {
        tvResult.text = "准备下载..."

        lifecycleScope.launch {
            tvResult.text = "正在下载..."

            val api = retrofit.create(DownloadApi::class.java)
            val result: NetworkResult<ByteArray> = executor.executeRawRequest {
                api.downloadFile()
            }

            result.fold(
                onSuccess = { data ->
                    if (data != null) {
                        val file = saveDownloadedFile(data)
                        tvResult.text = "下载成功\n保存路径: ${file.absolutePath}\n文件大小: ${data.size} bytes"
                    } else {
                        tvResult.text = "下载成功但数据为空"
                    }
                },
                onTechnicalFailure = { ex ->
                    tvResult.text = "下载失败\n错误: ${ex.message}"
                },
                onBusinessFailure = { code, msg ->
                    tvResult.text = "业务错误\nCode: $code\nMessage: $msg"
                }
            )
        }
    }

    private fun saveDownloadedFile(data: ByteArray): File {
        val file = File(filesDir, "downloaded_test.txt")
        file.writeBytes(data)
        return file
    }

    interface DownloadApi {
        @retrofit2.http.GET("/download/test.txt")
        suspend fun downloadFile(): ByteArray
    }
}
