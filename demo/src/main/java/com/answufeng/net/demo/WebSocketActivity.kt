package com.answufeng.net.demo

import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.answufeng.net.websocket.IWebSocketManager
import com.answufeng.net.websocket.WebSocketManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WebSocketActivity : BaseDemoActivity() {

    @Inject lateinit var wsManager: IWebSocketManager

    private lateinit var tvLog: TextView
    private lateinit var etMessage: TextInputEditText

    override fun getTitleText() = "🔌 WebSocket"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("连接控制")

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layout.addView(this, lp)
        }

        MaterialButton(this).apply {
            text = "连接"
            setOnClickListener { connect() }
            btnRow.addView(this)
        }

        MaterialButton(this).apply {
            text = "断开"
            setOnClickListener { disconnect() }
            btnRow.addView(this)
        }

        addDivider()

        addSectionTitle("发送消息")

        val inputLayout = TextInputLayout(this).apply {
            hint = "输入消息内容"
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layout.addView(this, lp)
        }

        etMessage = TextInputEditText(inputLayout.context).apply {
            inputLayout.addView(this)
        }

        MaterialButton(this).apply {
            text = "发送"
            setOnClickListener { sendMessage() }
            layout.addView(this)
        }

        addDivider()

        addSectionTitle("快捷消息")

        val chipGroup = ChipGroup(this).apply {
            layout.addView(this)
        }

        listOf("ping", "hello", "test").forEach { msg ->
            Chip(this).apply {
                text = msg
                setOnClickListener {
                    wsManager.sendText(msg)
                    appendLog("发送: $msg")
                }
                chipGroup.addView(this)
            }
        }

        addDivider()

        addSectionTitle("消息日志")

        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(300)
            )
            layout.addView(this, lp)
        }

        tvLog = TextView(this).apply {
            text = "等待连接..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            card.addView(this)
        }
    }

    private fun connect() {
        appendLog("正在连接...")
        wsManager.connectDefault(
            url = "wss://echo.websocket.org",
            config = WebSocketManager.Config(
                enableHeartbeat = true,
                heartbeatIntervalMs = 30_000L,
                heartbeatTimeoutMs = 60_000L,
                maxReconnectAttempts = 5,
                enableMessageReplay = true
            ),
            listener = object : WebSocketManager.WebSocketListener {
                override fun onOpen(connectionId: String) {
                    runOnUiThread { appendLog("✅ 已连接: $connectionId") }
                }
                override fun onMessage(connectionId: String, text: String) {
                    runOnUiThread { appendLog("📩 收到: $text") }
                }
                override fun onClosed(connectionId: String, code: Int, reason: String) {
                    runOnUiThread { appendLog("🔌 已关闭: $code $reason") }
                }
                override fun onFailure(connectionId: String, throwable: Throwable) {
                    runOnUiThread { appendLog("❌ 错误: ${throwable.message}") }
                }
                override fun onHeartbeatTimeout(connectionId: String) {
                    runOnUiThread { appendLog("⏰ 心跳超时") }
                }
            }
        )
    }

    private fun disconnect() {
        wsManager.disconnectDefault()
        appendLog("🔌 已断开")
    }

    private fun sendMessage() {
        val msg = etMessage.text.toString()
        if (msg.isNotBlank()) {
            wsManager.sendText(msg)
            appendLog("📤 发送: $msg")
            etMessage.text?.clear()
        }
    }

    private fun appendLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        tvLog.append("[$time] $msg\n")
        (tvLog.parent as? android.widget.ScrollView)?.post {
            (tvLog.parent as? android.widget.ScrollView)?.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager.disconnectDefault()
    }
}
