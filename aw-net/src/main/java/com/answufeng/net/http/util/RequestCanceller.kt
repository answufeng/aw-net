package com.answufeng.net.http.util

import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 请求取消管理器，支持按 tag 批量取消请求。
 *
 * ### 用法
 * ```kotlin
 * val canceller = RequestCanceller()
 *
 * // 在请求开始时注册
 * val job = canceller.register("loadUser") {
 *     executor.executeRequest { api.getUser() }
 * }
 *
 * // 按标签取消
 * canceller.cancelByTag("loadUser")
 *
 * // 取消所有请求
 * canceller.cancelAll()
 * ```
 *
 * @since 1.1.0
 */
class RequestCanceller {

    private val jobs = ConcurrentHashMap<String, CopyOnWriteArrayList<Job>>()

    /**
     * 注册一个请求 Job 到指定 tag。
     * @param tag 请求标签
     * @param job 请求对应的 Job
     * @since 1.1.0
     */
    fun register(tag: String, job: Job) {
        jobs.computeIfAbsent(tag) { CopyOnWriteArrayList() }.add(job)
        job.invokeOnCompletion { removeJob(tag, job) }
    }

    /**
     * 取消指定 tag 下的所有请求。
     * @param tag 请求标签
     * @since 1.1.0
     */
    fun cancelByTag(tag: String) {
        jobs.remove(tag)?.forEach { it.cancel() }
    }

    /**
     * 取消所有请求。
     * @since 1.1.0
     */
    fun cancelAll() {
        jobs.keys.toList().forEach { cancelByTag(it) }
    }

    /**
     * 获取指定 tag 下活跃的请求数量。
     * @since 1.1.0
     */
    fun activeCount(tag: String): Int = jobs[tag]?.size ?: 0

    /**
     * 获取所有活跃请求的总数量。
     * @since 1.1.0
     */
    fun totalActiveCount(): Int = jobs.values.sumOf { it.size }

    private fun removeJob(tag: String, job: Job) {
        jobs[tag]?.let { list ->
            list.remove(job)
            if (list.isEmpty()) {
                jobs.remove(tag)
            }
        }
    }
}
