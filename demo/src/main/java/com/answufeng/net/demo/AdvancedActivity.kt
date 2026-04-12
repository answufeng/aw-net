package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.util.RequestDedup
import com.answufeng.net.http.util.RequestThrottle
import com.answufeng.net.http.util.pollingFlow
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class AdvancedActivity : BaseDemoActivity() {

    private lateinit var tvResult: TextView
    private val dedup = RequestDedup()
    private val throttle = RequestThrottle(intervalMs = 3000)
    private val requestCount = AtomicInteger(0)

    override fun getTitleText() = "🚀 高级功能"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("请求去重 (RequestDedup)")
        addBodyText("当多个调用者同时请求相同数据时，RequestDedup 将它们合并为一次实际请求。点击按钮快速发送 5 个并发请求，观察实际执行次数。")

        MaterialButton(this).apply {
            text = "测试去重 (5个并发请求)"
            setOnClickListener { testDedup() }
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("请求节流 (RequestThrottle)")
        addBodyText("限制相同请求的最小间隔。3 秒内重复点击只会执行第一次请求。")

        MaterialButton(this).apply {
            text = "测试节流 (3秒间隔)"
            setOnClickListener { testThrottle() }
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("轮询 (pollingFlow)")
        addBodyText("使用 Flow 周期性执行请求，支持最大次数和停止条件。")

        MaterialButton(this).apply {
            text = "开始轮询 (5次, 2秒间隔)"
            setOnClickListener { testPolling() }
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("运行结果")

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

    private fun testDedup() {
        tvResult.text = "⏳ 发送 5 个并发去重请求...\n"
        requestCount.set(0)
        val key = "dedup_test_${System.currentTimeMillis() % 10000}"

        repeat(5) { index ->
            lifecycleScope.launch {
                val result = dedup.dedupRequest(key) {
                    requestCount.incrementAndGet()
                    delay(1000)
                    "数据结果"
                }
                appendResult("请求#$index → $result (实际执行次数: ${requestCount.get()})")
            }
        }
    }

    private fun testThrottle() {
        tvResult.text = "⏳ 测试节流...\n"
        val key = "throttle_test"

        repeat(3) { index ->
            lifecycleScope.launch {
                val result = throttle.throttleRequest(key) {
                    "节流结果-${System.currentTimeMillis() % 10000}"
                }
                appendResult("点击#$index → $result")
            }
        }
    }

    private fun testPolling() {
        tvResult.text = "⏳ 开始轮询...\n"
        var pollCount = 0

        lifecycleScope.launch {
            pollingFlow(
                periodMillis = 2000,
                maxAttempts = 5,
                stopWhen = { false }
            ) {
                pollCount++
                "轮询结果#$pollCount @ ${System.currentTimeMillis() % 10000}"
            }.collect { value ->
                appendResult(value)
            }
            appendResult("✅ 轮询结束")
        }
    }

    private fun appendResult(msg: String) {
        tvResult.append("• $msg\n")
    }
}
