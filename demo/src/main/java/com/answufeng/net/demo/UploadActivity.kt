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
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class UploadActivity : AppCompatActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private val tv by lazy { TextView(this) }
    private val progressBar by lazy { ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "File Upload"
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
                tv.text = "Upload Progress: ${info.progress}%"
            }
        }

        lifecycleScope.launch {
            val testFile = File(cacheDir, "upload_test.txt")
            testFile.writeText("Hello, this is a test upload file from aw-net demo.")

            val result = executor.uploadFile(
                file = testFile,
                partName = "file",
                progressFlow = progressFlow
            ) { part ->
                retrofit.create(UploadApi::class.java).uploadFile(part)
            }

            when (result) {
                is NetworkResult.Success -> tv.append("\n\nUpload complete!")
                is NetworkResult.TechnicalFailure -> tv.append("\n\nError: ${result.exception.message}")
                is NetworkResult.BusinessFailure -> tv.append("\n\nBusiness Error: ${result.code} - ${result.msg}")
            }
        }
    }
}

interface UploadApi {
    @Multipart
    @POST("upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): GlobalResponse<String>
}
