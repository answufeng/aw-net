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
            findViewById<MaterialCardView>(R.id.cardBasic) to BasicRequestActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardDownload) to DownloadActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardUpload) to UploadActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardAuth) to AuthActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardDynamic) to DynamicConfigActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardWebSocket) to WebSocketActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardMonitor) to NetworkMonitorActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardAdvanced) to AdvancedActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardError) to ErrorHandlingActivity::class.java,
            findViewById<MaterialCardView>(R.id.cardMvvm) to MvvmDemoActivity::class.java
        )

        cards.forEach { (card, clazz) ->
            card.setOnClickListener {
                startActivity(Intent(this, clazz))
            }
        }
    }
}
