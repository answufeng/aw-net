package com.answufeng.net.demo

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.answufeng.net.http.config.NetworkConfig
import com.answufeng.net.http.interceptor.DynamicBaseUrlInterceptor
import com.answufeng.net.http.interceptor.DynamicTimeoutInterceptor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdvancedConfigActivity : BaseDemoActivity() {

    override fun getTitleText() = "⚙️ 高级配置"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("动态 Base URL")

        val baseUrlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layout.addView(this, lp)
        }

        MaterialButton(this).apply {
            text = "切换到测试环境"
            setOnClickListener { setTestBaseUrl() }
            backgroundTintList = getColorStateList(R.color.primary)
            baseUrlRow.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        MaterialButton(this).apply {
            text = "切换到生产环境"
            setOnClickListener { setProductionBaseUrl() }
            backgroundTintList = getColorStateList(R.color.secondary)
            baseUrlRow.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        addDivider()

        addSectionTitle("动态超时配置")

        val timeoutRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layout.addView(this, lp)
        }

        MaterialButton(this).apply {
            text = "设置 10s 超时"
            setOnClickListener { setShortTimeout() }
            backgroundTintList = getColorStateList(R.color.primary)
            timeoutRow.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        MaterialButton(this).apply {
            text = "设置 30s 超时"
            setOnClickListener { setLongTimeout() }
            backgroundTintList = getColorStateList(R.color.secondary)
            timeoutRow.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        addDivider()

        addSectionTitle("配置信息")

        val configCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(200)
            )
            layout.addView(this, lp)
        }

        val configText = TextView(this).apply {
            text = "当前配置:\n" +
                    "Base URL: ${NetworkConfig.baseUrl}\n" +
                    "Connect Timeout: ${NetworkConfig.connectTimeout}ms\n" +
                    "Read Timeout: ${NetworkConfig.readTimeout}ms\n" +
                    "Write Timeout: ${NetworkConfig.writeTimeout}ms"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            configCard.addView(this)
        }

        addDivider()

        MaterialButton(this).apply {
            text = "刷新配置"
            setOnClickListener { 
                configText.text = "当前配置:\n" +
                        "Base URL: ${NetworkConfig.baseUrl}\n" +
                        "Connect Timeout: ${NetworkConfig.connectTimeout}ms\n" +
                        "Read Timeout: ${NetworkConfig.readTimeout}ms\n" +
                        "Write Timeout: ${NetworkConfig.writeTimeout}ms"
            }
            layout.addView(this)
        }
    }

    private fun setTestBaseUrl() {
        DynamicBaseUrlInterceptor.setBaseUrl("https://api.test.example.com")
        showToast("已切换到测试环境")
    }

    private fun setProductionBaseUrl() {
        DynamicBaseUrlInterceptor.setBaseUrl("https://api.example.com")
        showToast("已切换到生产环境")
    }

    private fun setShortTimeout() {
        DynamicTimeoutInterceptor.setTimeout(
            connectTimeoutMs = 10_000,
            readTimeoutMs = 10_000,
            writeTimeoutMs = 10_000
        )
        showToast("已设置 10s 超时")
    }

    private fun setLongTimeout() {
        DynamicTimeoutInterceptor.setTimeout(
            connectTimeoutMs = 30_000,
            readTimeoutMs = 30_000,
            writeTimeoutMs = 30_000
        )
        showToast("已设置 30s 超时")
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
