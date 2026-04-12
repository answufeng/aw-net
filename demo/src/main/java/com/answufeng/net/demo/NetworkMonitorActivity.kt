package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.answufeng.net.http.util.NetworkMonitor
import com.answufeng.net.http.util.NetworkType
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class NetworkMonitorActivity : BaseDemoActivity() {

    @Inject lateinit var networkMonitor: NetworkMonitor

    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun getTitleText() = "📶 网络监听"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("当前网络状态")

        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(12)
            layout.addView(this, lp)
        }

        tvStatus = TextView(this).apply {
            text = "检测中..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            card.addView(this)
        }

        addSectionTitle("状态变更日志")

        val logCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(250)
            )
            layout.addView(this, lp)
        }

        tvLog = TextView(this).apply {
            text = "监听中...\n"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            logCard.addView(this)
        }

        updateStatus()

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                networkMonitor.isConnected.collect { connected ->
                    val label = if (connected) "🟢 在线" else "🔴 离线"
                    tvStatus.text = label
                    appendLog("连接变更: $label")
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                networkMonitor.networkType.collect { type ->
                    val typeName = when (type) {
                        NetworkType.NONE -> "无网络"
                        NetworkType.WIFI -> "Wi-Fi"
                        NetworkType.CELLULAR -> "移动网络"
                        NetworkType.ETHERNET -> "以太网"
                        NetworkType.OTHER -> "其他"
                    }
                    appendLog("网络类型: $typeName")
                }
            }
        }
    }

    private fun updateStatus() {
        val online = networkMonitor.isOnline()
        tvStatus.text = if (online) "🟢 在线" else "🔴 离线"
    }

    private fun appendLog(msg: String) {
        val time = timeFormat.format(Date())
        tvLog.append("[$time] $msg\n")
    }
}
