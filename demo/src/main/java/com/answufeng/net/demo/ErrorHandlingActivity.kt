package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.exception.BaseNetException
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.onSuccess
import com.answufeng.net.http.model.onBusinessFailure
import com.answufeng.net.http.model.onTechnicalFailure
import com.answufeng.net.http.model.onFailure
import com.answufeng.net.http.util.NetworkExecutor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import javax.inject.Inject

@AndroidEntryPoint
class ErrorHandlingActivity : BaseDemoActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private lateinit var tvResult: TextView
    private val api by lazy { retrofit.create(ErrorApi::class.java) }

    override fun getTitleText() = "❌ 错误处理"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("404 错误")
        addBodyText("请求不存在的资源，触发 TechnicalFailure（HTTP 404）。")

        MaterialButton(this).apply {
            text = "请求不存在的路径"
            setOnClickListener { test404() }
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("超时错误")
        addBodyText("使用极短超时（1ms）触发超时错误，演示 @Timeout 注解和错误分类。")

        MaterialButton(this).apply {
            text = "模拟超时"
            setOnClickListener { testTimeout() }
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("自动重试")
        addBodyText("使用 executeRequest 的 retryOnFailure 参数，对失败请求自动重试 3 次。")

        MaterialButton(this).apply {
            text = "请求并自动重试"
            setOnClickListener { testRetry() }
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("链式错误处理")
        addBodyText("使用 onSuccess / onBusinessFailure / onTechnicalFailure 链式处理不同结果。")

        MaterialButton(this).apply {
            text = "链式处理演示"
            setOnClickListener { testChainHandling() }
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("结果")

        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(this, lp)
        }

        tvResult = TextView(this).apply {
            text = "点击上方按钮测试..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            card.addView(this)
        }
    }

    private fun test404() {
        tvResult.text = "⏳ 请求中..."
        lifecycleScope.launch {
            val result: NetworkResult<List<Post>> = executor.executeRawRequest {
                api.getNonExistent()
            }
            tvResult.text = formatErrorResult("404 测试", result)
        }
    }

    private fun testTimeout() {
        tvResult.text = "⏳ 请求中（极短超时）..."
        lifecycleScope.launch {
            val result: NetworkResult<List<Post>> = executor.executeRawRequest(
                retryOnFailure = 0
            ) {
                kotlinx.coroutines.delay(50)
                api.getPosts()
            }
            tvResult.text = formatErrorResult("超时测试", result)
        }
    }

    private fun testRetry() {
        tvResult.text = "⏳ 请求中（自动重试 3 次）..."
        lifecycleScope.launch {
            val result: NetworkResult<List<Post>> = executor.executeRawRequest(
                retryOnFailure = 3,
                retryDelayMs = 500
            ) {
                api.getNonExistent()
            }
            tvResult.text = formatErrorResult("重试测试", result)
        }
    }

    private fun testChainHandling() {
        tvResult.text = "⏳ 请求中..."
        lifecycleScope.launch {
            val result: NetworkResult<List<Post>> = executor.executeRawRequest {
                api.getPosts()
            }

            val sb = StringBuilder()
            sb.appendLine("── 链式处理演示 ──")
            sb.appendLine()

            result.onSuccess { data ->
                sb.appendLine("✅ onSuccess: 获取到 ${data?.size ?: 0} 条数据")
            }.onBusinessFailure { code, msg ->
                sb.appendLine("⚠️ onBusinessFailure: code=$code, msg=$msg")
            }.onTechnicalFailure { ex ->
                sb.appendLine("❌ onTechnicalFailure: code=${ex.code}, msg=${ex.message}")
            }

            sb.appendLine()
            sb.appendLine("── onFailure 统一处理 ──")
            result.onFailure { failure ->
                when (failure) {
                    is NetworkResult.TechnicalFailure ->
                        sb.appendLine("技术错误: ${failure.exception.code} - ${failure.exception.message}")
                    is NetworkResult.BusinessFailure ->
                        sb.appendLine("业务错误: ${failure.code} - ${failure.msg}")
                    else -> sb.appendLine("未知错误")
                }
            }

            tvResult.text = sb.toString()
        }
    }

    private fun <T> formatErrorResult(label: String, result: NetworkResult<T>): String {
        val sb = StringBuilder()
        sb.appendLine("── $label ──")
        sb.appendLine()
        when (result) {
            is NetworkResult.Success -> {
                sb.appendLine("✅ 成功")
                sb.appendLine(result.data.toString().take(200))
            }
            is NetworkResult.TechnicalFailure -> {
                val ex = result.exception
                sb.appendLine("❌ TechnicalFailure")
                sb.appendLine("  错误码: ${ex.code}")
                sb.appendLine("  消息: ${ex.message}")
                sb.appendLine("  类型: ${ex.javaClass.simpleName}")
            }
            is NetworkResult.BusinessFailure -> {
                sb.appendLine("⚠️ BusinessFailure")
                sb.appendLine("  code: ${result.code}")
                sb.appendLine("  msg: ${result.msg}")
            }
        }
        return sb.toString()
    }
}
