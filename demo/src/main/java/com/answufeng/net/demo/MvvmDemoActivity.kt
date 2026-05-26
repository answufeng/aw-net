package com.answufeng.net.demo

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.answufeng.net.http.model.NetworkResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MvvmDemoActivity : BaseDemoActivity() {
    private val viewModel: PostViewModel by viewModels()

    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var tvResult: TextView
    private lateinit var retryButton: MaterialButton

    override fun getTitleText() = "MVVM 架构"

    override fun setupContent(layout: LinearLayout) {
        addLead("ViewModel 只依赖 UserRepository；Repository 内部调用 executor.execute。")

        addButtonRow(
            { text = "加载列表"; setOnClickListener { viewModel.loadPosts() } },
            { text = "刷新"; setOnClickListener { viewModel.refresh() } },
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
            addOutlinedButton("重试") {
                viewModel.loadPosts()
            }.apply {
                visibility = View.GONE
            }

        addSectionTitle("状态与结果")
        tvResult = addResultCard("点击「加载列表」开始…")

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Idle -> {
                            progressBar.visibility = View.GONE
                            retryButton.visibility = View.GONE
                        }
                        is UiState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            retryButton.visibility = View.GONE
                            tvResult.text = "加载中…"
                        }
                        is UiState.Success -> {
                            progressBar.visibility = View.GONE
                            retryButton.visibility = View.GONE
                            if (state.posts.isEmpty()) {
                                tvResult.text = "无数据"
                            } else {
                                val sb = StringBuilder("成功 · ${state.posts.size} 条\n\n")
                                state.posts.take(5).forEach { post ->
                                    sb.appendLine("#${post.id}  ${post.title.take(36)}")
                                }
                                if (state.posts.size > 5) sb.appendLine("… 另有 ${state.posts.size - 5} 条")
                                tvResult.text = sb.toString()
                            }
                        }
                        is UiState.Error -> {
                            progressBar.visibility = View.GONE
                            retryButton.visibility = View.VISIBLE
                            tvResult.text = "失败\n${state.message}"
                        }
                    }
                }
            }
        }

        addDivider()
        addInfoCard(
            title = "分层",
            description = "Activity → ViewModel → UserRepository → NetworkExecutor + Api",
            hint = "详见 demo/UserRepository.kt",
        )
    }
}

@HiltViewModel
class PostViewModel
    @Inject
    constructor(
        private val repository: UserRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
        val uiState: StateFlow<UiState> = _uiState

        fun loadPosts() {
            _uiState.value = UiState.Loading
            viewModelScope.launch {
                val result = repository.loadPosts()
                _uiState.value =
                    when (result) {
                        is NetworkResult.Success ->
                            UiState.Success(result.data ?: emptyList())
                        is NetworkResult.TechnicalFailure ->
                            UiState.Error(result.exception.message ?: "网络错误")
                        is NetworkResult.BusinessFailure ->
                            UiState.Error("${result.code}: ${result.msg}")
                    }
            }
        }

        fun refresh() = loadPosts()
    }

sealed class UiState {
    object Idle : UiState()

    object Loading : UiState()

    data class Success(val posts: List<Post>) : UiState()

    data class Error(val message: String) : UiState()
}
