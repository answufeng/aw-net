package com.answufeng.net.demo

import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.util.NetworkExecutor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import javax.inject.Inject

@AndroidEntryPoint
class MvvmDemoActivity : BaseDemoActivity() {

    private val viewModel: PostViewModel by viewModels()

    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var tvResult: TextView

    override fun getTitleText() = "🏗️ MVVM 示例"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("ViewModel + StateFlow")
        addBodyText("在 ViewModel 中使用 NetworkExecutor 发起请求，通过 StateFlow 驱动 UI 更新。这是推荐的架构模式。")

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layout.addView(this)
        }

        MaterialButton(this).apply {
            text = "加载帖子列表"
            setOnClickListener { viewModel.loadPosts() }
            btnRow.addView(this)
        }

        MaterialButton(this).apply {
            text = "刷新"
            setOnClickListener { viewModel.refresh() }
            btnRow.addView(this)
        }

        addDivider()

        addSectionTitle("加载状态")

        progressBar = CircularProgressIndicator(this).apply {
            visibility = android.view.View.GONE
            val lp = LinearLayout.LayoutParams(dp(48), dp(48))
            lp.gravity = android.view.Gravity.CENTER
            layout.addView(this, lp)
        }

        addDivider()

        addSectionTitle("结果")

        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(this, lp)
        }

        tvResult = TextView(this).apply {
            text = "点击按钮加载..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_log)
            card.addView(this)
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Idle -> {
                        progressBar.visibility = android.view.View.GONE
                    }
                    is UiState.Loading -> {
                        progressBar.visibility = android.view.View.VISIBLE
                        tvResult.text = "⏳ 加载中..."
                    }
                    is UiState.Success -> {
                        progressBar.visibility = android.view.View.GONE
                        val sb = StringBuilder()
                        sb.appendLine("✅ 加载成功，共 ${state.posts.size} 条")
                        sb.appendLine()
                        state.posts.take(5).forEach { post ->
                            sb.appendLine("  #${post.id} ${post.title.take(30)}")
                        }
                        if (state.posts.size > 5) sb.appendLine("  ... 还有 ${state.posts.size - 5} 条")
                        tvResult.text = sb.toString()
                    }
                    is UiState.Error -> {
                        progressBar.visibility = android.view.View.GONE
                        tvResult.text = "❌ ${state.message}"
                    }
                }
            }
        }

        addDivider()

        addSectionTitle("代码示例")
        addCodeBlock(
            """
@HiltViewModel
class PostViewModel @Inject constructor(
    private val executor: NetworkExecutor,
    private val retrofit: Retrofit
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun loadPosts() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = executor.executeRawRequest {
                retrofit.create(Api::class.java).getPosts()
            }
            when (result) {
                is NetworkResult.Success ->
                    _uiState.value = UiState.Success(result.data ?: emptyList())
                is NetworkResult.TechnicalFailure ->
                    _uiState.value = UiState.Error(result.exception.message ?: "Error")
                is NetworkResult.BusinessFailure ->
                    _uiState.value = UiState.Error("Business Error")
            }
        }
    }
}
""".trimIndent().replace("Business Error", "\${result.code}: \${result.msg}")
        )
    }
}

@HiltViewModel
class PostViewModel @Inject constructor(
    private val executor: NetworkExecutor,
    private val retrofit: Retrofit
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun loadPosts() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val api = retrofit.create(JsonPlaceholderApi::class.java)
            val result: NetworkResult<List<Post>> = executor.executeRawRequest {
                api.getPosts()
            }
            when (result) {
                is NetworkResult.Success ->
                    _uiState.value = UiState.Success(result.data ?: emptyList())
                is NetworkResult.TechnicalFailure ->
                    _uiState.value = UiState.Error(result.exception.message ?: "未知错误")
                is NetworkResult.BusinessFailure ->
                    _uiState.value = UiState.Error("${result.code}: ${result.msg}")
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
