package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.util.RequestDedup
import com.answufeng.net.http.util.RequestThrottle
import com.answufeng.net.http.util.pollingFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class AdvancedActivity : BaseDemoActivity() {
    private lateinit var tvResult: TextView
    private val dedup = RequestDedup()
    private val throttle = RequestThrottle(intervalMs = 3000)
    private val requestCount = AtomicInteger(0)

    override fun getTitleText() = "请求工具"

    override fun setupContent(layout: LinearLayout) {
        addLead("去重、节流、轮询为可选工具类，在 Repository 或 ViewModel 中按需组合 execute。")

        addPrimaryButton("去重 · 5 路并发") { testDedup() }
        addOutlinedButton("节流 · 3 秒窗口") { testThrottle() }
        addOutlinedButton("轮询 · 5 次 / 2 秒") { testPolling() }

        addSectionTitle("输出")
        tvResult = addResultCard("点击按钮开始…")
    }

    private fun testDedup() {
        tvResult.text = "并发去重中…\n"
        requestCount.set(0)
        val key = "dedup_${System.currentTimeMillis() % 10000}"

        repeat(5) { index ->
            lifecycleScope.launch {
                val result =
                    dedup.dedupRequest(key) {
                        requestCount.incrementAndGet()
                        delay(800)
                        "ok"
                    }
                appendResult("#$index → $result（实际执行 ${requestCount.get()} 次）")
            }
        }
    }

    private fun testThrottle() {
        tvResult.text = "节流测试…\n"
        val key = "throttle_demo"
        repeat(3) { index ->
            lifecycleScope.launch {
                val result =
                    throttle.throttleRequest(key) {
                        "t=${System.currentTimeMillis() % 10000}"
                    }
                appendResult("点击 $index → $result")
            }
        }
    }

    private fun testPolling() {
        tvResult.text = "轮询中…\n"
        var count = 0
        lifecycleScope.launch {
            pollingFlow(periodMillis = 2000, maxAttempts = 5) {
                count++
                "第 $count 次"
            }.collect { value ->
                appendResult(value)
            }
            appendResult("轮询结束")
        }
    }

    private fun appendResult(line: String) {
        tvResult.append("\n$line")
    }
}
