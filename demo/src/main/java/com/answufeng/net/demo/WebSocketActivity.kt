package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.answufeng.net.websocket.IWebSocketManager
import com.answufeng.net.websocket.WebSocketLogLevel
import com.answufeng.net.websocket.WebSocketManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WebSocketActivity : BaseDemoActivity() {

    @Inject lateinit var wsManager: IWebSocketManager

    private lateinit var tvLog: TextView
    private lateinit var etMessage: TextInputEditText
    private lateinit var etUrl: TextInputEditText
    private lateinit var scrollView: ScrollView
    private lateinit var messageAdapter: MessageAdapter
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun getTitleText() = "🔌 WebSocket"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("连接入口")

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
            text = "默认连接"
            setOnClickListener { connectDefault() }
            backgroundTintList = getColorStateList(R.color.primary)
            btnRow.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        MaterialButton(this).apply {
            text = "自定义连接"
            setOnClickListener { connectCustom() }
            backgroundTintList = getColorStateList(R.color.secondary)
            btnRow.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        addDivider()

        addSectionTitle("自定义连接 URL")

        val urlInputLayout = TextInputLayout(this).apply {
            hint = "WebSocket URL"
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layout.addView(this, lp)
        }

        etUrl = TextInputEditText(urlInputLayout.context).apply {
            setText("wss://ws.postman-echo.com/raw")
            urlInputLayout.addView(this)
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

        listOf("ping", "hello", "test", "{\"type\":\"message\",\"content\":\"Hello WebSocket\"}").forEach { msg ->
            Chip(this).apply {
                text = msg
                setOnClickListener {
                    wsManager.sendText(msg)
                    addMessage(MessageItem(MessageType.SENT, msg))
                    appendLog("发送: $msg")
                }
                chipGroup.addView(this)
            }
        }

        addDivider()

        addSectionTitle("消息列表")

        val messageCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(300)
            )
            layout.addView(this, lp)
        }

        val messageRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WebSocketActivity)
            messageAdapter = MessageAdapter()
            adapter = messageAdapter
            messageCard.addView(this)
        }

        addDivider()

        addSectionTitle("详细日志")

        val logCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(200)
            )
            layout.addView(this, lp)
        }

        scrollView = ScrollView(this).apply {
            logCard.addView(this)
        }

        tvLog = TextView(this).apply {
            text = "等待连接..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            scrollView.addView(this)
        }

        addDivider()

        MaterialButton(this).apply {
            text = "断开连接"
            setOnClickListener { disconnect() }
            layout.addView(this)
        }
    }

    private fun connectDefault() {
        appendLog("正在连接默认服务器...")
        wsManager.connectDefault(
            url = "wss://ws.postman-echo.com/raw",
            config = WebSocketManager.Config(
                enableHeartbeat = true,
                heartbeatIntervalMs = 30_000L,
                heartbeatTimeoutMs = 60_000L,
                maxReconnectAttempts = 5,
                enableMessageReplay = true,
                wsLogLevel = WebSocketLogLevel.FULL
            ),
            listener = createWebSocketListener("默认")
        )
    }

    private fun connectCustom() {
        val url = etUrl.text.toString().trim()
        if (url.isBlank()) {
            Toast.makeText(this, "请输入 WebSocket URL", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            Toast.makeText(this, "URL 必须以 ws:// 或 wss:// 开头", Toast.LENGTH_SHORT).show()
            return
        }
        appendLog("正在连接自定义服务器: $url")
        wsManager.connect(
            connectionId = "custom",
            url = url,
            config = WebSocketManager.Config(
                enableHeartbeat = true,
                heartbeatIntervalMs = 30_000L,
                heartbeatTimeoutMs = 60_000L,
                maxReconnectAttempts = 5,
                enableMessageReplay = true
            ),
            listener = createWebSocketListener("自定义")
        )
    }

    private fun createWebSocketListener(source: String): WebSocketManager.WebSocketListener {
        return object : WebSocketManager.WebSocketListener {
            override fun onOpen(connectionId: String) {
                runOnUiThread {
                    appendLog("✅ $source 已连接: $connectionId")
                    addMessage(MessageItem(MessageType.SYSTEM, "$source 连接已建立"))
                }
            }
            override fun onMessage(connectionId: String, text: String) {
                runOnUiThread {
                    appendLog("📩 收到: $text")
                    addMessage(MessageItem(MessageType.RECEIVED, text))
                }
            }
            override fun onClosed(connectionId: String, code: Int, reason: String) {
                runOnUiThread {
                    appendLog("🔌 $source 已关闭: $code $reason")
                    addMessage(MessageItem(MessageType.SYSTEM, "$source 连接已关闭: $code $reason"))
                }
            }
            override fun onFailure(connectionId: String, throwable: Throwable) {
                runOnUiThread {
                    appendLog("❌ $source 错误: ${throwable.message}")
                    addMessage(MessageItem(MessageType.SYSTEM, "$source 连接失败: ${throwable.message}"))
                }
            }
            override fun onHeartbeatTimeout(connectionId: String) {
                runOnUiThread {
                    appendLog("⏰ 心跳超时")
                    addMessage(MessageItem(MessageType.SYSTEM, "心跳超时"))
                }
            }
        }
    }

    private fun disconnect() {
        wsManager.disconnectDefault()
        wsManager.disconnect("custom")
        appendLog("🔌 已断开所有连接")
        addMessage(MessageItem(MessageType.SYSTEM, "所有连接已断开"))
    }

    private fun sendMessage() {
        val msg = etMessage.text.toString()
        if (msg.isNotBlank()) {
            wsManager.sendText(msg)
            addMessage(MessageItem(MessageType.SENT, msg))
            appendLog("📤 发送: $msg")
            etMessage.text?.clear()
        }
    }

    private fun addMessage(item: MessageItem) {
        messageAdapter.addMessage(item)
    }

    private fun appendLog(msg: String) {
        val time = timeFormat.format(Date())
        tvLog.append("[$time] $msg\n")
        scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager.disconnectDefault()
        wsManager.disconnect("custom")
    }

    enum class MessageType {
        SENT,
        RECEIVED,
        SYSTEM
    }

    data class MessageItem(val type: MessageType, val content: String)

    class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

        private val messages = mutableListOf<MessageItem>()

        fun addMessage(item: MessageItem) {
            messages.add(item)
            notifyItemInserted(messages.size - 1)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MessageViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val item = messages[position]
            when (item.type) {
                MessageType.SENT -> {
                    holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.sent_message))
                    holder.textView.text = "📤 ${item.content}"
                }
                MessageType.RECEIVED -> {
                    holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.received_message))
                    holder.textView.text = "📩 ${item.content}"
                }
                MessageType.SYSTEM -> {
                    holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.system_message))
                    holder.textView.text = "📢 ${item.content}"
                }
            }
        }

        override fun getItemCount() = messages.size

        inner class MessageViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(android.R.id.text1)
        }
    }
}
