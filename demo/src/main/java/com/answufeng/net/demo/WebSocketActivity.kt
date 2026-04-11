package com.answufeng.net.demo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.net.websocket.IWebSocketManager
import com.answufeng.net.websocket.WebSocketManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WebSocketActivity : AppCompatActivity() {

    @Inject lateinit var wsManager: IWebSocketManager

    private val tvLog by lazy { TextView(this) }
    private val etMessage by lazy { EditText(this).apply { hint = "Enter message" } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "WebSocket"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val btnConnect = Button(this).apply {
            text = "Connect"
            setOnClickListener { connect() }
        }
        val btnDisconnect = Button(this).apply {
            text = "Disconnect"
            setOnClickListener { disconnect() }
        }
        val btnSend = Button(this).apply {
            text = "Send"
            setOnClickListener { sendMessage() }
        }

        layout.addView(btnConnect)
        layout.addView(btnDisconnect)
        layout.addView(etMessage)
        layout.addView(btnSend)
        layout.addView(tvLog)

        val scrollView = ScrollView(this)
        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun connect() {
        wsManager.connectDefault(
            url = "wss://echo.websocket.org",
            config = WebSocketManager.Config(
                enableHeartbeat = true,
                heartbeatIntervalMs = 30_000L,
                maxReconnectAttempts = 5,
                enableMessageReplay = true
            ),
            listener = object : WebSocketManager.WebSocketListener {
                override fun onOpen(connectionId: String) {
                    runOnUiThread { appendLog("Connected: $connectionId") }
                }
                override fun onMessage(connectionId: String, text: String) {
                    runOnUiThread { appendLog("Received: $text") }
                }
                override fun onClosed(connectionId: String, code: Int, reason: String) {
                    runOnUiThread { appendLog("Closed: $code $reason") }
                }
                override fun onFailure(connectionId: String, throwable: Throwable) {
                    runOnUiThread { appendLog("Error: ${throwable.message}") }
                }
            }
        )
    }

    private fun disconnect() {
        wsManager.disconnectDefault()
        appendLog("Disconnected")
    }

    private fun sendMessage() {
        val msg = etMessage.text.toString()
        if (msg.isNotBlank()) {
            wsManager.sendText(msg)
            appendLog("Sent: $msg")
            etMessage.text.clear()
        }
    }

    private fun appendLog(msg: String) {
        tvLog.append("[$msg]\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager.disconnectDefault()
    }
}
