package com.answufeng.net.demo

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

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = getTitleText()
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        contentLayout = findViewById(R.id.contentLayout)
        setupContent(contentLayout)
    }

    abstract fun getTitleText(): String

    abstract fun setupContent(layout: LinearLayout)

    protected fun addLead(text: String) {
        TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            val lp = matchParentWrap()
            lp.bottomMargin = dp(20)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addSectionTitle(text: String) {
        TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            val lp = matchParentWrap()
            lp.topMargin = dp(8)
            lp.bottomMargin = dp(10)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addBodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            val lp = matchParentWrap()
            lp.bottomMargin = dp(12)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addInfoCard(
        title: String,
        description: String,
        hint: String? = null,
    ) {
        MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            strokeColor = getColor(R.color.divider)
            strokeWidth = dp(1)
            setCardBackgroundColor(getColor(R.color.card_bg))
            cardElevation = 0f
            val lp = matchParentWrap()
            lp.bottomMargin = dp(10)
            contentLayout.addView(this, lp)

            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                addView(
                    TextView(context).apply {
                        text = title
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                        setTextColor(getColor(R.color.text_primary))
                    },
                )
                addView(
                    TextView(context).apply {
                        text = description
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setTextColor(getColor(R.color.text_secondary))
                        val tlp = LinearLayout.LayoutParams(MP, WC)
                        tlp.topMargin = dp(6)
                        layoutParams = tlp
                    },
                )
                if (hint != null) {
                    addView(
                        TextView(context).apply {
                            text = hint
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                            setTextColor(getColor(R.color.text_tertiary))
                            typeface = android.graphics.Typeface.MONOSPACE
                            val hlp = LinearLayout.LayoutParams(MP, WC)
                            hlp.topMargin = dp(8)
                            layoutParams = hlp
                        },
                    )
                }
            }.also { addView(it) }
        }
    }

    protected fun addPrimaryButton(
        text: String,
        onClick: () -> Unit,
    ): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            this.text = text
            setOnClickListener { onClick() }
            val lp = matchParentWrap()
            lp.topMargin = dp(4)
            lp.bottomMargin = dp(4)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addOutlinedButton(
        text: String,
        onClick: () -> Unit,
    ): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            setOnClickListener { onClick() }
            val lp = matchParentWrap()
            lp.topMargin = dp(4)
            lp.bottomMargin = dp(4)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addButtonRow(vararg builders: MaterialButton.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = matchParentWrap()
            lp.bottomMargin = dp(12)
            contentLayout.addView(this, lp)
            builders.forEachIndexed { index, builder ->
                MaterialButton(this@BaseDemoActivity).apply {
                    builder()
                    val blp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    if (index > 0) blp.marginStart = dp(8)
                    addView(this, blp)
                }
            }
        }
    }

    protected fun addResultCard(initialText: String): TextView {
        val card =
            MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                strokeColor = getColor(R.color.divider)
                strokeWidth = dp(1)
                setCardBackgroundColor(getColor(R.color.card_bg))
                cardElevation = 0f
                val lp = matchParentWrap()
                lp.topMargin = dp(8)
                lp.bottomMargin = dp(12)
                contentLayout.addView(this, lp)
            }

        return TextView(this).apply {
            text = initialText
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(dp(4).toFloat(), 1f)
            background = getDrawable(R.drawable.bg_log)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            card.addView(
                this,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    protected fun addListCard(heightDp: Int, setup: (LinearLayout) -> Unit): LinearLayout {
        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
        setup(container)
        MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            strokeColor = getColor(R.color.divider)
            strokeWidth = dp(1)
            setCardBackgroundColor(getColor(R.color.card_bg))
            cardElevation = 0f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
            lp.bottomMargin = dp(12)
            contentLayout.addView(this, lp)
            addView(container)
        }
        return container
    }

    protected fun addCodeBlock(text: String) {
        TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.text_secondary))
            typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(dp(3).toFloat(), 1f)
            background = getDrawable(R.drawable.bg_code_block)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val lp = matchParentWrap()
            lp.bottomMargin = dp(12)
            contentLayout.addView(this, lp)
        }
    }

    protected fun addDivider() {
        View(this).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_divider)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            lp.topMargin = dp(16)
            lp.bottomMargin = dp(16)
            contentLayout.addView(this, lp)
        }
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private val MP: Int get() = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC: Int get() = LinearLayout.LayoutParams.WRAP_CONTENT

    private fun matchParentWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MP, WC)
}
