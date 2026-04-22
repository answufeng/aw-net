package com.answufeng.net.demo

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

abstract class BaseDemoActivity : AppCompatActivity() {

    protected lateinit var contentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getTitleText()
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon = ContextCompat.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)

        contentLayout = findViewById(R.id.contentLayout)
        setupContent(contentLayout)
    }

    abstract fun getTitleText(): String
    abstract fun setupContent(layout: LinearLayout)

    protected fun addSectionTitle(text: String) {
        TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addPrimaryButton(text: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addOutlinedButton(text: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            setOnClickListener { onClick() }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addCodeBlock(text: String) {
        TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            typeface = android.graphics.Typeface.MONOSPACE
            background = ContextCompat.getDrawable(context, R.drawable.bg_code_block)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(12)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addLogBlock(initialText: String): TextView {
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            strokeColor = getColor(R.color.divider)
            strokeWidth = dp(1)
            setCardBackgroundColor(getColor(R.color.card_bg))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            lp.bottomMargin = dp(12)
            contentLayout.addView(this, lp)
        }

        return TextView(this).apply {
            text = initialText
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            background = getDrawable(R.drawable.bg_log)
            card.addView(this)
        }
    }

    protected fun addDivider() {
        View(this).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_divider)
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
