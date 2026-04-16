package com.answufeng.net.demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialCardView>(R.id.cardHttp).setOnClickListener {
            startActivity(Intent(this, HttpDemoActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardWebSocket).setOnClickListener {
            startActivity(Intent(this, WebSocketActivity::class.java))
        }
    }
}
