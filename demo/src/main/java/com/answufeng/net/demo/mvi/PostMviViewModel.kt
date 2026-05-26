package com.answufeng.net.demo.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.answufeng.net.demo.UserRepository
import com.answufeng.net.http.model.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostMviViewModel
    @Inject
    constructor(
        private val repository: UserRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PostUiState())
        val state: StateFlow<PostUiState> = _state.asStateFlow()

        /** 统一入口：Activity / Composable 只调用此方法发送用户意图 */
        fun dispatch(intent: PostIntent) {
            when (intent) {
                PostIntent.Load,
                PostIntent.Refresh,
                PostIntent.Retry,
                -> loadPosts()
            }
        }

        private fun loadPosts() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                when (val result = repository.loadPosts()) {
                    is NetworkResult.Success ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                posts = result.data ?: emptyList(),
                                errorMessage = null,
                            )
                        }
                    is NetworkResult.TechnicalFailure ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.exception.message ?: "网络错误",
                            )
                        }
                    is NetworkResult.BusinessFailure ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "${result.code}: ${result.msg}",
                            )
                        }
                }
            }
        }
    }
