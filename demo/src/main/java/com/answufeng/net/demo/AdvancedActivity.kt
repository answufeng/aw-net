package com.answufeng.net.demo

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.util.RequestDedup
import com.answufeng.net.http.util.RequestThrottle
import com.answufeng.net.http.util.pollingFlow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class AdvancedActivity : AppCompatActivity() {

    private val tv by lazy { TextView(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Advanced Features"
        val scrollView = ScrollView(this)
        scrollView.setPadding(24, 24, 24, 24)
        scrollView.addView(tv)
        setContentView(scrollView)

        val sb = StringBuilder()

        sb.appendLine("=== Request Dedup ===")
        sb.appendLine("When multiple callers request the same data concurrently,")
        sb.appendLine("RequestDedup merges them into a single actual request.")
        sb.appendLine()
        sb.appendLine("Usage:")
        sb.appendLine("""
            |val dedup = RequestDedup()
            |val result = dedup.dedupRequest("user_info_" + userId) {
            |    api.getUserInfo(userId)
            |}
        """.trimMargin())
        sb.appendLine()

        sb.appendLine("=== Request Throttle ===")
        sb.appendLine("Limits the minimum interval between identical requests.")
        sb.appendLine()
        sb.appendLine("Usage:")
        sb.appendLine("""
            |val throttle = RequestThrottle(intervalMs = 3000)
            |val result = throttle.throttleRequest("refresh_list") {
            |    api.getList()
            |}
            |throttle.invalidate("refresh_list") // Force next request
        """.trimMargin())
        sb.appendLine()

        sb.appendLine("=== Polling ===")
        sb.appendLine("Periodically execute a request using Flow.")
        sb.appendLine()
        sb.appendLine("Usage:")
        sb.appendLine("""
            |lifecycleScope.launch {
            |    pollingFlow(
            |        periodMillis = 5000,
            |        maxAttempts = 10,
            |        stopWhen = { it.status == "completed" }
            |    ) {
            |        api.checkTaskStatus(taskId)
            |    }.collect { result ->
            |        updateUI(result)
            |    }
            |}
        """.trimMargin())

        tv.text = sb.toString()
    }
}
