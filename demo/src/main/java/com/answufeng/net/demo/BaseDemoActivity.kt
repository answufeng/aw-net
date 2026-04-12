package com.answufeng.net.demo

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

abstract class BaseDemoActivity : AppCompatActivity() {

    protected lateinit var contentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getTitleText()
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon = getDrawable(androidx.appcompat.R.drawable.abc_ic_ab_back_material)

        contentLayout = findViewById(R.id.contentLayout)
        setupContent(contentLayout)
    }

    abstract fun getTitleText(): String
    abstract fun setupContent(layout: LinearLayout)

    protected fun addSectionTitle(text: String) {
        TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(getColor(R.color.text_primary))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(16)
            lp.bottomMargin = dp(8)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addBodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColor(R.color.text_secondary))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addCodeBlock(text: String) {
        TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.primary_variant))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_code_block)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(12)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addDivider() {
        View(this).apply {
            background = getDrawable(R.drawable.bg_divider)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
            lp.topMargin = dp(8)
            lp.bottomMargin = dp(8)
            contentLayout.addView(this, lp)
        }
    }

    protected fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
