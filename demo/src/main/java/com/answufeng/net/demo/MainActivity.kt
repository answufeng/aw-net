package com.answufeng.net.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = getString(R.string.app_name)
        }

        findViewById<RecyclerView>(R.id.rvDemo).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter =
                DemoHomeAdapter { entry ->
                    startActivity(DemoCatalog.intentFor(this@MainActivity, entry))
                }
        }
    }
}
