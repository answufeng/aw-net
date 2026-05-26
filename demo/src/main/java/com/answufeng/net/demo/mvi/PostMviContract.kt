package com.answufeng.net.demo.mvi

import com.answufeng.net.demo.Post

/**
 * MVI 契约：View 只发送 [PostIntent]，只渲染 [PostUiState]。
 */
sealed interface PostIntent {
    /** 首次加载 */
    data object Load : PostIntent

    /** 用户下拉 / 点刷新 */
    data object Refresh : PostIntent

    /** 失败后重试 */
    data object Retry : PostIntent
}

data class PostUiState(
    val isLoading: Boolean = false,
    val posts: List<Post> = emptyList(),
    val errorMessage: String? = null,
) {
    val showRetry: Boolean get() = !isLoading && errorMessage != null

    val isEmpty: Boolean get() = !isLoading && errorMessage == null && posts.isEmpty()
}
