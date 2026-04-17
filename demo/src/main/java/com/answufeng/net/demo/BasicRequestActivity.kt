package com.answufeng.net.demo

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
import javax.inject.Inject

@AndroidEntryPoint
class BasicRequestActivity : BaseDemoActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private lateinit var tvResult: TextView
    private val api by lazy { retrofit.create(JsonPlaceholderApi::class.java) }

    override fun getTitleText() = "📡 基础请求"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("GET 请求")
        addBodyText("从 JSONPlaceholder 获取帖子列表，使用 executeRawRequest 直接返回原始数据。")

        val btnGet = MaterialButton(this).apply {
            text = "发送 GET 请求"
            setOnClickListener { performGetRequest() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
        layout.addView(btnGet)

        addSectionTitle("POST 请求")
        addBodyText("向 JSONPlaceholder 创建新帖子，观察请求体和响应。")

        val btnPost = MaterialButton(this).apply {
            text = "发送 POST 请求"
            setOnClickListener { performPostRequest() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
        layout.addView(btnPost)

        addSectionTitle("自定义成功码")
        addBodyText("使用 executeRequest 的 successCode 参数或 @SuccessCode 注解。需要 IBaseResponse 返回类型的 API。")
        addCodeBlock("""
executor.executeRequest(
    successCode = 200
) { api.getUser() }

// 或使用 @SuccessCode 注解
@SuccessCode(200)
@GET("legacy-api")
suspend fun legacyApi(): GlobalResponse<Data>""".trimIndent())

        addDivider()

        addSectionTitle("请求结果")

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(16)
            }
            layout.addView(this)
        }

        tvResult = TextView(this).apply {
            text = "点击上方按钮发送请求..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = getDrawable(R.drawable.bg_log)
            card.addView(this)
        }
    }

    private fun performGetRequest() {
        tvResult.text = "⏳ 请求中..."
        lifecycleScope.launch {
            val result: NetworkResult<List<Post>> = executor.executeRawRequest { api.getPosts() }
            tvResult.text = formatResult("GET /posts", result)
        }
    }

    private fun performPostRequest() {
        tvResult.text = "⏳ 请求中..."
        lifecycleScope.launch {
            val result: NetworkResult<Post> = executor.executeRawRequest {
                api.createPost(PostBody(1, "aw-net 测试标题", "aw-net 测试内容"))
            }
            tvResult.text = formatResult("POST /posts", result)
        }
    }

    private fun <T> formatResult(endpoint: String, result: NetworkResult<T>): String {
        val sb = StringBuilder()
        sb.appendLine("── $endpoint ──")
        sb.appendLine()
        result.fold(
            onSuccess = { data ->
                sb.appendLine("✅ 成功")
                sb.appendLine(data.toString().take(500))
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
}
