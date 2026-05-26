package com.answufeng.net.demo

import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.util.NetworkExecutor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 推荐分层：ViewModel 只依赖 Repository，由 Repository 组合 [NetworkExecutor] + API。
 */
@Singleton
class UserRepository
    @Inject
    constructor(
        private val executor: NetworkExecutor,
        private val api: JsonPlaceholderApi,
    ) {
        suspend fun loadPosts(): NetworkResult<List<Post>> = executor.execute { api.getPosts() }

        suspend fun createPost(body: PostBody): NetworkResult<Post> = executor.execute { api.createPost(body) }
}
