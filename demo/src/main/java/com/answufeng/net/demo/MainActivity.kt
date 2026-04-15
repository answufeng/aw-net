package com.answufeng.net.demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cards: Map<MaterialCardView, Class<*>> = mapOf(
            findViewById<MaterialCardView>(R.id.cardHttp) to HttpDemoActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardWebSocket) to WebSocketActivity::class.java
        )

        cards.forEach { (card, clazz) ->
            card.setOnClickListener {
                startActivity(Intent(this, clazz))
            }
        }
    }
}
