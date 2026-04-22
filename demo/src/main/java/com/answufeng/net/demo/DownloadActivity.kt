package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ProgressInfo
import com.answufeng.net.http.util.NetworkExecutor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DownloadActivity : BaseDemoActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvProgress: TextView
    private lateinit var tvResult: TextView

    override fun getTitleText() = "文件下载"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("下载文件")
        addBodyText("从 JSONPlaceholder 下载帖子数据，实时显示下载进度。支持 SHA-256 校验。")

        val btnDownload = MaterialButton(this).apply {
            text = "开始下载"
            setOnClickListener { performDownload() }
        }
        layout.addView(btnDownload)

        addDivider()

        addSectionTitle("下载进度")

        progressBar = LinearProgressIndicator(this).apply {
            max = 100
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layout.addView(this, lp)
        }

        tvProgress = addBodyText("等待下载...")

        addSectionTitle("下载结果")

        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(this, lp)
        }

        tvResult = TextView(this).apply {
            text = "尚未开始下载"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            card.addView(this)
        }
    }

    private fun performDownload() {
        progressBar.progress = 0
        tvProgress.text = "下载中..."
        tvResult.text = "下载中..."

        val progressFlow = MutableSharedFlow<ProgressInfo>(replay = 1)

        lifecycleScope.launch {
            progressFlow.collect { info ->
                progressBar.progress = info.progress
                tvProgress.text = "进度: ${info.progress}%  (${formatSize(info.currentSize)} / ${formatSize(info.totalSize)})"
            }
        }

        lifecycleScope.launch {
            val api = retrofit.create(DownloadApi::class.java)
            val targetFile = File(cacheDir, "download_test.json")

            val result = executor.downloadFile(
                targetFile = targetFile,
                progressFlow = progressFlow
            ) { api.downloadFile("posts") }

            when (result) {
                is NetworkResult.Success -> {
                    val file = result.data
                    tvResult.text = buildString {
                        appendLine("SUCCESS")
                        appendLine("  路径: ${file?.absolutePath}")
                        appendLine("  大小: ${formatSize(file?.length() ?: 0)}")
                    }
                }
                is NetworkResult.TechnicalFailure -> {
                    tvResult.text = "TECHNICAL_FAILURE: ${result.exception.message}"
                }
                is NetworkResult.BusinessFailure -> {
                    tvResult.text = "BUSINESS_FAILURE: ${result.code} - ${result.msg}"
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}

interface DownloadApi {
    @Streaming
    @GET("{path}")
    suspend fun downloadFile(@Path("path") path: String): ResponseBody
}
