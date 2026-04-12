package com.answufeng.net.demo

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class Post(val id: Int = 0, val title: String = "", val body: String = "", val userId: Int = 0)

data class PostBody(val userId: Int, val title: String, val body: String)

interface JsonPlaceholderApi {
    @GET("posts")
    suspend fun getPosts(): List<Post>

    @POST("posts")
    suspend fun createPost(@Body body: PostBody): Post
}

interface ErrorApi {
    @GET("posts/999999")
    suspend fun getNonExistent(): List<Post>

    @GET("posts")
    suspend fun getPosts(): List<Post>
}
