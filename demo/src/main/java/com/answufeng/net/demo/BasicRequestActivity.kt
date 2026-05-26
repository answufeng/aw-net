package com.answufeng.net.demo

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.fold
import com.answufeng.net.http.util.NetworkExecutor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BasicRequestActivity : BaseDemoActivity() {
    @Inject lateinit var executor: NetworkExecutor

    @Inject lateinit var api: JsonPlaceholderApi

    @Inject lateinit var httpBinApi: HttpBinApi

    private lateinit var postAdapter: PostAdapter
    private lateinit var tvResult: TextView

    override fun getTitleText() = "核心请求"

    override fun setupContent(layout: LinearLayout) {
        addLead("推荐用法：Retrofit 声明 T?，用 executor.execute 发起请求。本页数据源为 JSONPlaceholder（裸 JSON）。")

        addButtonRow(
            { text = "GET 帖子"; setOnClickListener { fetchPosts() } },
            { text = "POST 创建"; setOnClickListener { createPost() } },
        )

        addOutlinedButton("第三方 GET (httpbin)") { fetchThirdParty() }

        addSectionTitle("帖子列表")

        addListCard(260) { container ->
            RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@BasicRequestActivity)
                postAdapter = PostAdapter()
                adapter = postAdapter
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                container.addView(
                    this,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        }

        addSectionTitle("请求日志")
        tvResult = addResultCard("点击上方按钮发起请求…")
    }

    private fun fetchPosts() {
        tvResult.text = "GET /posts …"
        postAdapter.clear()
        lifecycleScope.launch {
            val result = executor.execute { api.getPosts() }
            tvResult.text = formatResult("GET /posts", result)
            if (result is NetworkResult.Success) {
                result.data?.let { postAdapter.setPosts(it.take(8)) }
            }
        }
    }

    private fun createPost() {
        tvResult.text = "POST /posts …"
        lifecycleScope.launch {
            val result =
                executor.execute {
                    api.createPost(PostBody(1, "aw-net Demo", "由 execute 创建的测试帖子"))
                }
            tvResult.text = formatResult("POST /posts", result)
            if (result is NetworkResult.Success) {
                result.data?.let { postAdapter.addPost(it) }
            }
        }
    }

    private fun fetchThirdParty() {
        tvResult.text = "GET https://httpbin.org/get …"
        lifecycleScope.launch {
            val result = executor.execute { httpBinApi.get() }
            tvResult.text = formatResult("httpbin /get", result)
        }
    }

    private fun <T> formatResult(
        endpoint: String,
        result: NetworkResult<T>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("▸ $endpoint")
        sb.appendLine()
        result.fold(
            onSuccess = { data ->
                sb.appendLine("✓ Success")
                sb.appendLine(data.toString().take(600))
            },
            onTechnicalFailure = { ex ->
                sb.appendLine("✗ Technical")
                sb.appendLine("  ${ex.code}: ${ex.message}")
            },
            onBusinessFailure = { code, msg ->
                sb.appendLine("✗ Business")
                sb.appendLine("  $code: $msg")
            },
        )
        return sb.toString()
    }

    private class PostAdapter : RecyclerView.Adapter<PostAdapter.Holder>() {
        private val posts = mutableListOf<Post>()

        fun setPosts(newPosts: List<Post>) {
            posts.clear()
            posts.addAll(newPosts)
            notifyDataSetChanged()
        }

        fun addPost(post: Post) {
            posts.add(0, post)
            notifyItemInserted(0)
        }

        fun clear() {
            posts.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val post = posts[position]
            holder.title.text = post.title
            holder.body.text = post.body
        }

        override fun getItemCount() = posts.size

        class Holder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.tvPostTitle)
            val body: TextView = itemView.findViewById(R.id.tvPostBody)
        }
    }
}
