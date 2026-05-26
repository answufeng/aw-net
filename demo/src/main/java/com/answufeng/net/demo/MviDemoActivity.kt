package com.answufeng.net.demo

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.answufeng.net.demo.mvi.PostIntent
import com.answufeng.net.demo.mvi.PostMviViewModel
import com.answufeng.net.demo.mvi.PostUiState
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MviDemoActivity : BaseDemoActivity() {
    private val viewModel: PostMviViewModel by viewModels()

    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var tvResult: TextView
    private lateinit var retryButton: MaterialButton

    override fun getTitleText() = "MVI 架构"

    override fun setupContent(layout: LinearLayout) {
        addLead("单向数据流：View 发送 Intent → ViewModel 更新 State → View 订阅 State 渲染。")

        addButtonRow(
            { text = "Load"; setOnClickListener { viewModel.dispatch(PostIntent.Load) } },
            { text = "Refresh"; setOnClickListener { viewModel.dispatch(PostIntent.Refresh) } },
        )

        progressBar =
            CircularProgressIndicator(this).apply {
                visibility = View.GONE
                val lp = LinearLayout.LayoutParams(dp(40), dp(40))
                lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
                lp.bottomMargin = dp(8)
                layout.addView(this, lp)
            }

        retryButton =
            addOutlinedButton("Retry") {
                viewModel.dispatch(PostIntent.Retry)
            }.apply {
                visibility = View.GONE
            }

        addSectionTitle("State")
        tvResult = addResultCard("发送 Load Intent 开始…")

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }

        addDivider()
        addInfoCard(
            title = "与 MVVM 的区别",
            description = "MVVM 常暴露多个方法（load/refresh）；MVI 收敛为 dispatch(Intent)，状态集中在不可变 data class。",
            hint = "Intent: PostMviContract.kt · ViewModel: PostMviViewModel.kt",
        )
        addInfoCard(
            title = "网络层不变",
            description = "仍通过 UserRepository → executor.execute { api.getPosts() }",
            hint = "与 MVVM 示例共用 UserRepository",
        )
    }

    private fun render(state: PostUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        retryButton.visibility = if (state.showRetry) View.VISIBLE else View.GONE

        tvResult.text =
            when {
                state.isLoading -> "加载中…"
                state.errorMessage != null -> "失败\n${state.errorMessage}"
                state.posts.isEmpty() -> "无数据"
                else -> {
                    buildString {
                        appendLine("成功 · ${state.posts.size} 条")
                        appendLine()
                        state.posts.take(5).forEach { post ->
                            appendLine("#${post.id}  ${post.title.take(36)}")
                        }
                        if (state.posts.size > 5) appendLine("… 另有 ${state.posts.size - 5} 条")
                    }
                }
            }
    }
}
