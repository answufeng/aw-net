package com.answufeng.net.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "aw-net Demo"

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val buttons = listOf(
            "Basic Request (GET/POST)" to BasicRequestActivity::class.java,
            "File Download" to DownloadActivity::class.java,
            "File Upload" to UploadActivity::class.java,
            "Token Auth" to AuthActivity::class.java,
            "Dynamic Config" to DynamicConfigActivity::class.java,
            "WebSocket" to WebSocketActivity::class.java,
            "Network Monitor" to NetworkMonitorActivity::class.java,
            "Advanced (Dedup/Throttle/Polling)" to AdvancedActivity::class.java
        )

        buttons.forEach { (label, clazz) ->
            Button(this).apply {
                text = label
                setAllCaps(false)
                textSize = 16f
                setPadding(0, 24, 0, 24)
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, clazz))
                }
                layout.addView(this)
            }
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
