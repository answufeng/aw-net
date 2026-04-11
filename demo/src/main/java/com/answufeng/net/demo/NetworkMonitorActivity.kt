package com.answufeng.net.demo

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.util.NetworkMonitor
import com.answufeng.net.http.util.NetworkType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NetworkMonitorActivity : AppCompatActivity() {

    @Inject lateinit var networkMonitor: NetworkMonitor

    private val tv by lazy { TextView(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Network Monitor"
        val scrollView = ScrollView(this)
        scrollView.setPadding(24, 24, 24, 24)
        scrollView.addView(tv)
        setContentView(scrollView)

        tv.text = "Monitoring network state...\n\n"

        tv.append("Current online: ${networkMonitor.isOnline()}\n\n")

        lifecycleScope.launch {
            networkMonitor.isConnected.collect { connected ->
                tv.append("Connection changed: ${if (connected) "ONLINE" else "OFFLINE"}\n")
            }
        }

        lifecycleScope.launch {
            networkMonitor.networkType.collect { type ->
                val typeName = when (type) {
                    NetworkType.NONE -> "None"
                    NetworkType.WIFI -> "Wi-Fi"
                    NetworkType.CELLULAR -> "Cellular"
                    NetworkType.ETHERNET -> "Ethernet"
                    NetworkType.OTHER -> "Other"
                }
                tv.append("Network type: $typeName\n")
            }
        }
    }
}
