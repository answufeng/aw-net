package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import com.answufeng.net.http.annotations.NetworkConfigProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import javax.inject.Inject

class AdvancedConfigActivity : BaseDemoActivity() {

    @Inject lateinit var configProvider: NetworkConfigProvider

    private lateinit var configText: TextView

    override fun getTitleText() = "高级配置"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("当前运行时配置")
        addBodyText("从 NetworkConfigProvider 读取当前生效的配置参数。")

        val configCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(this, lp)
        }

        configText = TextView(this).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            configCard.addView(this)
        }
        refreshConfig()

        addDivider()

        addSectionTitle("运行时修改配置")
        addBodyText("部分配置支持运行时修改（日志级别、额外请求头、默认成功码），修改后立即生效。")

        MaterialButton(this).apply {
            text = "切换日志级别为 BODY"
            setOnClickListener {
                val current = configProvider.current
                configProvider.updateConfig(
                    current.copy(networkLogLevel = com.answufeng.net.http.annotations.NetworkLogLevel.BODY)
                )
                refreshConfig()
            }
            layout.addView(this)
        }

        MaterialButton(this).apply {
            text = "切换日志级别为 NONE"
            setOnClickListener {
                val current = configProvider.current
                configProvider.updateConfig(
                    current.copy(networkLogLevel = com.answufeng.net.http.annotations.NetworkLogLevel.NONE)
                )
                refreshConfig()
            }
            layout.addView(this)
        }

        MaterialButton(this).apply {
            text = "添加额外请求头"
            setOnClickListener {
                val current = configProvider.current
                configProvider.updateConfig(
                    current.copy(extraHeaders = current.extraHeaders + ("X-Demo-Header" to "aw-net-demo"))
                )
                refreshConfig()
            }
            layout.addView(this)
        }

        MaterialButton(this).apply {
            text = "刷新配置"
            setOnClickListener { refreshConfig() }
            layout.addView(this)
        }
    }

    private fun refreshConfig() {
        val config = configProvider.current
        configText.text = buildString {
            appendLine("Base URL: ${config.baseUrl}")
            appendLine("Connect Timeout: ${config.connectTimeout}ms")
            appendLine("Read Timeout: ${config.readTimeout}ms")
            appendLine("Write Timeout: ${config.writeTimeout}ms")
            appendLine("Log Level: ${config.networkLogLevel}")
            appendLine("Success Code: ${config.defaultSuccessCode}")
            appendLine("Extra Headers: ${config.extraHeaders}")
            appendLine("Max Idle Connections: ${config.maxIdleConnections}")
            appendLine("Keep Alive: ${config.keepAliveDurationSeconds}s")
            appendLine("Retry Enabled: ${config.enableRetryInterceptor}")
            appendLine("Retry Max Attempts: ${config.retryMaxAttempts}")
        }
    }
}
