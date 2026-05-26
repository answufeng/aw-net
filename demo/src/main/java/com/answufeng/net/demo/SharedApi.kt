package com.answufeng.net.demo

import com.answufeng.net.http.annotations.BaseUrl
import com.answufeng.net.http.annotations.RawResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class Post(val id: Int = 0, val title: String = "", val body: String = "", val userId: Int = 0)

data class PostBody(val userId: Int, val title: String, val body: String)

data class HttpBinGetResponse(val url: String = "")

/** JSONPlaceholder：裸 JSON，演示 @RawResponse + 同域 baseUrl */
interface JsonPlaceholderApi {
    @RawResponse
    @GET("posts")
    suspend fun getPosts(): List<Post>

    @RawResponse
    @POST("posts")
    suspend fun createPost(
        @Body body: PostBody,
    ): Post
}

/** 第三方域名：演示 @RawResponse + @BaseUrl */
interface HttpBinApi {
    @RawResponse
    @BaseUrl("https://httpbin.org/")
    @GET("get")
    suspend fun get(): HttpBinGetResponse
}

interface ErrorApi {
    @RawResponse
    @GET("posts/999999")
    suspend fun getNonExistent(): List<Post>

    @RawResponse
    @GET("posts")
    suspend fun getPosts(): List<Post>
}
