package com.answufeng.net.demo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        findViewById<MaterialCardView>(R.id.cardBasicRequest).setOnClickListener {
            startActivity(Intent(this, BasicRequestActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardHttp).setOnClickListener {
            startActivity(Intent(this, HttpDemoActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardAuth).setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardDynamicConfig).setOnClickListener {
            startActivity(Intent(this, DynamicConfigActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardAdvancedConfig).setOnClickListener {
            startActivity(Intent(this, AdvancedConfigActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardDownload).setOnClickListener {
            startActivity(Intent(this, DownloadActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardUpload).setOnClickListener {
            startActivity(Intent(this, UploadActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardAdvanced).setOnClickListener {
            startActivity(Intent(this, AdvancedActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardErrorHandling).setOnClickListener {
            startActivity(Intent(this, ErrorHandlingActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardNetworkMonitor).setOnClickListener {
            startActivity(Intent(this, NetworkMonitorActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardMvvm).setOnClickListener {
            startActivity(Intent(this, MvvmDemoActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardWebSocket).setOnClickListener {
            startActivity(Intent(this, WebSocketActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.demo_main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_demo_playbook -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.demo_playbook_title)
                .setMessage(R.string.demo_playbook_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
