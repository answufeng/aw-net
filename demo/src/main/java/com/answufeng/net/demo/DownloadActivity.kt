package com.answufeng.net.demo

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.model.GlobalResponse
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.ProgressInfo
import com.answufeng.net.http.util.NetworkExecutor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming
import okhttp3.ResponseBody
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DownloadActivity : AppCompatActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private val tv by lazy { TextView(this) }
    private val progressBar by lazy { ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "File Download"
        val scrollView = ScrollView(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        layout.addView(progressBar)
        layout.addView(tv)
        scrollView.addView(layout)
        setContentView(scrollView)

        val progressFlow = MutableSharedFlow<ProgressInfo>(replay = 1)

        lifecycleScope.launch {
            progressFlow.collect { info ->
                progressBar.progress = info.progress
                tv.text = "Progress: ${info.progress}% (${info.currentSize}/${info.totalSize})"
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
                is NetworkResult.Success -> tv.append("\n\nDownload complete: ${targetFile.absolutePath}")
                is NetworkResult.TechnicalFailure -> tv.append("\n\nError: ${result.exception.message}")
                is NetworkResult.BusinessFailure -> tv.append("\n\nBusiness Error: ${result.code} - ${result.msg}")
            }
        }
    }
}

interface DownloadApi {
    @Streaming
    @GET("{path}")
    suspend fun downloadFile(@Path("path") path: String): ResponseBody
}
