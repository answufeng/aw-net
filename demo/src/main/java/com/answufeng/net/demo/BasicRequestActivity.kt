package com.answufeng.net.demo

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.fold
import com.answufeng.net.http.util.NetworkExecutor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import javax.inject.Inject

@AndroidEntryPoint
class BasicRequestActivity : AppCompatActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private val tv by lazy { TextView(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Basic Request"
        val scrollView = ScrollView(this)
        scrollView.setPadding(24, 24, 24, 24)
        scrollView.addView(tv)
        setContentView(scrollView)

        lifecycleScope.launch {
            tv.text = "Loading..."
            val api = retrofit.create(JsonPlaceholderApi::class.java)

            val sb = StringBuilder()

            sb.appendLine("=== GET Request ===")
            val getResult: NetworkResult<List<Post>> = executor.executeRawRequest {
                api.getPosts()
            }
            sb.appendLine(formatResult(getResult))

            sb.appendLine()
            sb.appendLine("=== POST Request ===")
            val postResult: NetworkResult<Post> = executor.executeRawRequest {
                api.createPost(PostBody(1, "Test Title", "Test Body"))
            }
            sb.appendLine(formatResult(postResult))

            tv.text = sb.toString()
        }
    }

    private fun <T> formatResult(result: NetworkResult<T>): String {
        return result.fold(
            onSuccess = { data -> "Success: ${data.toString().take(200)}" },
            onTechnicalFailure = { ex -> "Technical Error: ${ex.message}" },
            onBusinessFailure = { code, msg -> "Business Error: $code - $msg" }
        )
    }
}

interface JsonPlaceholderApi {
    @GET("posts")
    suspend fun getPosts(): List<Post>

    @POST("posts")
    suspend fun createPost(@Body body: PostBody): Post
}

data class Post(val id: Int = 0, val title: String = "", val body: String = "", val userId: Int = 0)
data class PostBody(val userId: Int, val title: String, val body: String)
