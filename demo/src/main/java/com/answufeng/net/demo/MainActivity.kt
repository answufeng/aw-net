package com.answufeng.net.demo

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 主布局
        val mainLayout = findViewById<LinearLayout>(R.id.mainLayout)

        // 标题
        mainLayout.addView(TextView(this).apply {
            text = "🌐 aw-net 功能演示"
            textSize = 20f
            setPadding(0, 0, 0, 20)
        })

        // 功能卡片布局
        val cards = listOf(
            "HTTP 请求" to HttpDemoActivity::class.java,
            "WebSocket" to WebSocketActivity::class.java,
            "文件上传" to UploadDemoActivity::class.java,
            "文件下载" to DownloadDemoActivity::class.java,
            "高级配置" to AdvancedConfigActivity::class.java
        )

        cards.forEach { (title, clazz) ->
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                setPadding(20, 20, 20, 20)
            }

            val cardContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            cardContent.addView(TextView(this).apply {
                text = title
                textSize = 16f
                setPadding(0, 0, 0, 8)
            })

            cardContent.addView(Button(this).apply {
                text = "进入演示"
                setOnClickListener {
                    startActivity(android.content.Intent(this@MainActivity, clazz))
                }
            })

            card.addView(cardContent)
            mainLayout.addView(card)
        }
    }
}
