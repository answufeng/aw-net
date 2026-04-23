package com.answufeng.net.http.config

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 持有**当前** [NetworkConfig] 的单例（多线程读安全），供拦截器、执行器与 [com.answufeng.net.http.util.NetworkClientFactory] 在每次请求时取快照。
 *
 * - [current] 使用 [java.util.concurrent.atomic.AtomicReference]；[update] / [updateConfig] 替换整份配置并 [notifyListeners]。
 * - [registerListener] 用于在配置变化时让缓存型组件（如 [com.answufeng.net.http.interceptor.ExtraHeadersInterceptor]）失效或重建；监听器内异常会吞掉以免拖垮调用方。
 */
@Singleton
class NetworkConfigProvider @Inject constructor(initialConfig: NetworkConfig) {

    private val ref = AtomicReference(initialConfig)

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * 当前生效的配置快照。
     */
    val current: NetworkConfig
        get() = ref.get()

    /**
     * 直接替换为新的 NetworkConfig。
     */
    fun updateConfig(newConfig: NetworkConfig) {
        ref.set(newConfig)
        notifyListeners()
    }

    /**
     * 通过变换函数更新配置，例如：
     *
     * provider.update { it.copy(baseUrl = newBaseUrl) }
     */
    fun update(transform: (NetworkConfig) -> NetworkConfig) {
        ref.updateAndGet(transform)
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { listener ->
            try {
                listener()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 注册一个配置变更监听器，返回一个用于注销的函数。
     * 供宿主在运行时代码中订阅 [NetworkConfig] 变更；库内无直接引用属正常，**属稳定公共 API**。
     */
    @Suppress("unused")
    fun registerListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}
