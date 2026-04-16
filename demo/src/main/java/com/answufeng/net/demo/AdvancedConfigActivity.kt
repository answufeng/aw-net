package com.answufeng.net.demo

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.answufeng.net.http.annotations.NetworkConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class AdvancedConfigActivity : BaseDemoActivity() {

    override fun getTitleText() = "高级配置"

    override fun setupContent(layout: LinearLayout) {
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
                    "Base URL: https://jsonplaceholder.typicode.com/\n" +
                    "Connect Timeout: 15000ms\n" +
                    "Read Timeout: 15000ms\n" +
                    "Write Timeout: 15000ms"
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
                        "Base URL: https://jsonplaceholder.typicode.com/\n" +
                        "Connect Timeout: 15000ms\n" +
                        "Read Timeout: 15000ms\n" +
                        "Write Timeout: 15000ms"
            }
            layout.addView(this)
        }
    }
}
