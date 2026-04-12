package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import com.answufeng.net.http.auth.TokenProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AuthActivity : BaseDemoActivity() {

    @Inject lateinit var tokenProvider: TokenProvider

    private lateinit var tvResult: TextView

    override fun getTitleText() = "🔐 Token 鉴权"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("Token 鉴权机制")
        addBodyText("aw-net 在两个层面处理 401：\n• HTTP 401 → TokenAuthenticator 自动刷新\n• 业务 code=401 → RequestExecutor 协程层处理\n• 刷新失败均触发 UnauthorizedHandler")

        addDivider()

        addSectionTitle("当前 Token 状态")

        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(this, lp)
        }

        tvResult = TextView(this).apply {
            text = "Token: ${tokenProvider.getAccessToken() ?: "未设置"}"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            card.addView(this)
        }

        addDivider()

        addSectionTitle("操作")

        val btnSet = MaterialButton(this).apply {
            text = "设置 Token"
            setOnClickListener {
                (tokenProvider as? com.answufeng.net.http.auth.InMemoryTokenProvider)?.setAccessToken("demo-token-${System.currentTimeMillis() % 10000}")
                tvResult.text = "Token: ${tokenProvider.getAccessToken()}"
            }
        }
        layout.addView(btnSet)

        val btnClear = MaterialButton(this).apply {
            text = "清除 Token"
            setOnClickListener {
                (tokenProvider as? com.answufeng.net.http.auth.InMemoryTokenProvider)?.setAccessToken(null)
                tvResult.text = "Token: 未设置"
            }
        }
        layout.addView(btnClear)

        addDivider()

        addSectionTitle("代码示例")
        addCodeBlock("""
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides @Singleton
    fun provideTokenProvider(): TokenProvider {
        return InMemoryTokenProvider().apply {
            setAccessToken("your-access-token")
        }
    }

    @Provides @Singleton
    fun provideUnauthorizedHandler(): UnauthorizedHandler {
        return object : UnauthorizedHandler {
            override fun onUnauthorized() {
                // 跳转登录页
            }
        }
    }
}""".trimIndent())
    }
}
