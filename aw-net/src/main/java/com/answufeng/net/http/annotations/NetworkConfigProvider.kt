package com.answufeng.net.http.annotations

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NetworkConfig 的运行时配置容器。
 *
 * - 内部使用 AtomicReference 持有当前配置，保证线程安全；
 * - 支持在运行时通过 [update] 或 [updateConfig] 方法动态调整配置（如切换环境、更新公共 Header）。
 *
 * 新增：支持注册变更监听器（用于拦截器或其他缓存持有者在配置变更时刷新缓存）。
 * @since 1.0.0
 */@Singleton
class NetworkConfigProvider @Inject constructor(initialConfig: NetworkConfig) {

    private val ref = AtomicReference(initialConfig)

    // 监听器，当配置发生变化时会被调用
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * 当前生效的配置快照。
     * @since 1.0.0
 */    val current: NetworkConfig
        get() = ref.get()

    /**
     * 直接替换为新的 NetworkConfig。
     * @since 1.0.0
 */    fun updateConfig(newConfig: NetworkConfig) {
        ref.set(newConfig)
        // 通知监听器
        listeners.forEach { runCatching { it() } }
    }

    /**
     * 通过变换函数更新配置，例如：
     *
     * provider.update { it.copy(baseUrl = newBaseUrl) }
     * @since 1.0.0
 */    fun update(transform: (NetworkConfig) -> NetworkConfig) {
        ref.updateAndGet(transform)
        // 通知监听器
        listeners.forEach { runCatching { it() } }
    }

    /**
     * 注册一个配置变更监听器，返回一个用于注销的函数
     * @since 1.0.0
 */    @Suppress("unused") // 公开 API — 供项目层观察配置变更
    fun registerListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}
