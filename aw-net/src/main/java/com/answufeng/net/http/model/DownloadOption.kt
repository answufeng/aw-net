package com.answufeng.net.http.model

import com.answufeng.net.http.util.DownloadFailureStrategy
import com.answufeng.net.http.util.HashVerificationStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 下载配置选项，用于简化 [com.answufeng.net.http.util.NetworkExecutor.downloadFile] 等方法的参数传递。
 *
 * 使用示例：
 * ```kotlin
 * val result = executor.downloadFile(
 *     targetFile = file,
 *     option = DownloadOption(
 *         expectedHash = sha256,
 *         failureStrategy = DownloadFailureStrategy.DELETE_PARTIAL
 *     )
 * ) { api.download() }
 * ```
 *
 * 亦可用 [downloadOption] 构建等效配置。
 *
 * @param progressFlow 进度回调流
 * @param expectedHash 预期文件摘要（完整文件的摘要，非部分）
 * @param hashAlgorithm 摘要算法，默认 "SHA-256"
 * @param hashStrategy 校验失败策略
 * @param failureStrategy 下载失败后文件处理策略
 * @param dispatcher 协程调度器，默认 IO
 * @param tag 监控标签
 */
data class DownloadOption(
    val progressFlow: MutableSharedFlow<ProgressInfo>? = null,
    val expectedHash: String? = null,
    val hashAlgorithm: String = "SHA-256",
    val hashStrategy: HashVerificationStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH,
    val failureStrategy: DownloadFailureStrategy = DownloadFailureStrategy.DELETE_PARTIAL,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val tag: String? = null,
) {
    companion object {
        val DEFAULT = DownloadOption()
    }
}

inline fun downloadOption(block: DownloadOptionBuilder.() -> Unit): DownloadOption {
    val b = DownloadOptionBuilder()
    b.block()
    return b.build()
}

class DownloadOptionBuilder {
    var progressFlow: MutableSharedFlow<ProgressInfo>? = null
    var expectedHash: String? = null
    var hashAlgorithm: String = "SHA-256"
    var hashStrategy: HashVerificationStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH
    var failureStrategy: DownloadFailureStrategy = DownloadFailureStrategy.DELETE_PARTIAL
    var dispatcher: CoroutineDispatcher = Dispatchers.IO
    var tag: String? = null

    fun build(): DownloadOption =
        DownloadOption(
            progressFlow = progressFlow,
            expectedHash = expectedHash,
            hashAlgorithm = hashAlgorithm,
            hashStrategy = hashStrategy,
            failureStrategy = failureStrategy,
            dispatcher = dispatcher,
            tag = tag,
        )
}
