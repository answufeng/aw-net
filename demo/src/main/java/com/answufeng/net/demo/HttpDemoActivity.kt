package com.answufeng.net.demo

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.fold
import com.answufeng.net.http.util.NetworkExecutor
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import javax.inject.Inject

@AndroidEntryPoint
class HttpDemoActivity : BaseDemoActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private lateinit var postAdapter: PostAdapter
    private lateinit var tvResult: TextView
    private val api by lazy { retrofit.create(JsonPlaceholderApi::class.java) }

    override fun getTitleText() = "HTTP 请求"

    override fun setupContent(layout: LinearLayout) {
        addSectionTitle("功能列表")

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layout.addView(this, lp)
        }

        MaterialButton(this).apply {
            text = "获取帖子"
            setOnClickListener { fetchPosts() }
            backgroundTintList = getColorStateList(R.color.primary)
            btnRow.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        MaterialButton(this).apply {
            text = "创建帖子"
            setOnClickListener { createPost() }
            backgroundTintList = getColorStateList(R.color.secondary)
            btnRow.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        addDivider()

        addSectionTitle("帖子列表")

        val listCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(300)
            )
            layout.addView(this, lp)
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HttpDemoActivity)
            postAdapter = PostAdapter()
            adapter = postAdapter
            listCard.addView(this)
        }

        addDivider()

        addSectionTitle("请求结果")

        val resultCard = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(200)
            )
            layout.addView(this, lp)
        }

        tvResult = TextView(this).apply {
            text = "点击上方按钮发送请求..."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.log_text))
            typeface = android.graphics.Typeface.MONOSPACE
            background = getDrawable(R.drawable.bg_log)
            resultCard.addView(this)
        }
    }

    private fun fetchPosts() {
        tvResult.text = "请求中..."
        postAdapter.clear()
        lifecycleScope.launch {
            val result: NetworkResult<List<Post>> = executor.executeRawRequest { api.getPosts() }
            tvResult.text = formatResult("GET /posts", result)
            result.fold(
                onSuccess = { posts -> posts?.let { postAdapter.setPosts(it.take(10)) } },
                onTechnicalFailure = { _ -> },
                onBusinessFailure = { _, _ -> }
            )
        }
    }

    private fun createPost() {
        tvResult.text = "请求中..."
        lifecycleScope.launch {
            val result: NetworkResult<Post> = executor.executeRawRequest {
                api.createPost(PostBody(1, "aw-net 测试标题", "aw-net 测试内容"))
            }
            tvResult.text = formatResult("POST /posts", result)
            result.fold(
                onSuccess = { post -> post?.let { postAdapter.addPost(it) } },
                onTechnicalFailure = { _ -> },
                onBusinessFailure = { _, _ -> }
            )
        }
    }

    private fun <T> formatResult(endpoint: String, result: NetworkResult<T>): String {
        val sb = StringBuilder()
        sb.appendLine("── $endpoint ──")
        sb.appendLine()
        result.fold(
            onSuccess = { data ->
                sb.appendLine("SUCCESS")
                sb.appendLine(data.toString().take(500))
            },
            onTechnicalFailure = { ex ->
                sb.appendLine("TECHNICAL_FAILURE")
                sb.appendLine("  code: ${ex.code}")
                sb.appendLine("  msg: ${ex.message}")
            },
            onBusinessFailure = { code, msg ->
                sb.appendLine("BUSINESS_FAILURE")
                sb.appendLine("  code: $code")
                sb.appendLine("  msg: $msg")
            }
        )
        return sb.toString()
    }

    class PostAdapter : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

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

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PostViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return PostViewHolder(view)
        }

        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            val post = posts[position]
            holder.title.text = post.title
            holder.body.text = post.body
        }

        override fun getItemCount() = posts.size

        inner class PostViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(android.R.id.text1)
            val body: TextView = itemView.findViewById(android.R.id.text2)
        }
    }
}
