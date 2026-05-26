package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.RequestOption
import com.answufeng.net.http.model.onBusinessFailure
import com.answufeng.net.http.model.onSuccess
import com.answufeng.net.http.model.onTechnicalFailure
import com.answufeng.net.http.util.NetworkExecutor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ErrorHandlingActivity : BaseDemoActivity() {
    @Inject lateinit var executor: NetworkExecutor

    @Inject lateinit var api: ErrorApi

    private lateinit var tvResult: TextView

    override fun getTitleText() = "错误与重试"

    override fun setupContent(layout: LinearLayout) {
        addLead("演示 NetworkResult 三类结果与协程层重试。勿与 OkHttp @Retry 同时叠开。")

        addPrimaryButton("404 · 不存在资源") { test404() }
        addOutlinedButton("整体超时 (1ms)") { testTimeout() }
        addOutlinedButton("协程重试 ×3") { testRetry() }
        addOutlinedButton("链式 onSuccess / onFailure") { testChainHandling() }

        addSectionTitle("日志")
        tvResult = addResultCard("选择上方场景…")
    }

    private fun test404() {
        tvResult.text = "请求中…"
        lifecycleScope.launch {
            val result = executor.execute { api.getNonExistent() }
            tvResult.text = formatResult("404", result)
        }
    }

    private fun testTimeout() {
        tvResult.text = "请求中…"
        lifecycleScope.launch {
            val result =
                executor.execute(
                    option = RequestOption(totalTimeoutMs = 1),
                ) { api.getPosts() }
            tvResult.text = formatResult("timeout", result)
        }
    }

    private fun testRetry() {
        tvResult.text = "重试中…"
        lifecycleScope.launch {
            val result =
                executor.execute(
                    option =
                        RequestOption(
                            retryOnFailure = 3,
                            retryDelayMs = 400,
                            retryOnTechnical = true,
                        ),
                ) { api.getNonExistent() }
            tvResult.text = formatResult("retry×3", result)
        }
    }

    private fun testChainHandling() {
        tvResult.text = "请求中…"
        lifecycleScope.launch {
            val sb = StringBuilder()
            executor.execute { api.getPosts() }
                .onSuccess { list -> sb.appendLine("onSuccess: ${list?.size ?: 0} 条") }
                .onTechnicalFailure { ex -> sb.appendLine("onTechnical: ${ex.message}") }
                .onBusinessFailure { c, m -> sb.appendLine("onBusiness: $c $m") }
            tvResult.text = sb.toString()
        }
    }

    private fun <T> formatResult(
        tag: String,
        result: NetworkResult<T>,
    ): String =
        buildString {
            appendLine("▸ $tag")
            appendLine()
            when (result) {
                is NetworkResult.Success -> appendLine("Success\n${result.data}")
                is NetworkResult.TechnicalFailure ->
                    appendLine("TechnicalFailure\n${result.exception.code}: ${result.exception.message}")
                is NetworkResult.BusinessFailure ->
                    appendLine("BusinessFailure\n${result.code}: ${result.msg}")
            }
        }
}
