package com.answufeng.net.http.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络连接类型。
 * @since 1.0.0
 */
enum class NetworkType {
    /** 无网络连接 
    * @since 1.0.0
 */
    NONE,
    /** Wi-Fi 连接 
    * @since 1.0.0
 */
    WIFI,
    /** 蜂窝移动网络 
    * @since 1.0.0
 */
    CELLULAR,
    /** 以太网 
    * @since 1.0.0
 */
    ETHERNET,
    /** 其他网络类型 
    * @since 1.0.0
 */
    OTHER
}

/**
 * 网络状态监听器，基于 Android [ConnectivityManager] 实现。
 *
 * 提供两种使用方式：
 * 1. **StateFlow**（推荐）：通过 [isConnected] 和 [networkType] 实时获取网络状态
 * 2. **回调 Flow**：通过 [observeNetworkEvents] 获取网络连接/断开事件流
 *
 * 使用示例：
 * ```kotlin
 * @Inject lateinit var networkMonitor: NetworkMonitor
 *
 * // 方式一：观察连接状态
 * lifecycleScope.launch {
 *     networkMonitor.isConnected.collect { connected ->
 *         updateUI(connected)
 *     }
 * }
 *
 * // 方式二：快速判断当前是否在线
 * if (networkMonitor.isOnline()) {
 *     // proceed with network request
 * }
 *
 * // 方式三：获取网络类型
 * lifecycleScope.launch {
 *     networkMonitor.networkType.collect { type ->
 *         when (type) {
 *             NetworkType.WIFI -> showWifiIcon()
 *             NetworkType.CELLULAR -> showCellularIcon()
 *             NetworkType.NONE -> showOfflineIcon()
 *             else -> showDefaultIcon()
 *         }
 *     }
 * }
 * ```
 * @since 1.0.0
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    private val _networkType = MutableStateFlow(checkCurrentNetworkType())

    /**
     * 当前是否有网络连接（实时 StateFlow）。
     * @since 1.0.0
 */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * 当前网络连接类型（实时 StateFlow）。
     * @since 1.0.0
 */
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = true
            _networkType.value = resolveNetworkType(network)
        }

        override fun onLost(network: Network) {
            recheckConnectivity()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _isConnected.value = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
            _networkType.value = resolveNetworkType(networkCapabilities)
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            // 部分设备权限不足时会抛出 SecurityException
        }
    }

    /**
     * 同步判断当前是否有网络连接。
     * @since 1.0.0
 */
    fun isOnline(): Boolean = _isConnected.value

    /**
     * 获取当前网络类型的快照。
     * @since 1.0.0
 */
    fun currentNetworkType(): NetworkType = _networkType.value

    /**
     * 销毁监听器，注销所有系统级回调，释放资源。
     *
     * 调用后 [isConnected] 和 [networkType] 将不再更新。
     * 适用于非 Singleton 场景或需要显式释放的生命周期。
     * @since 1.0.0
 */
    fun destroy() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // 回调未注册时会抛出 IllegalArgumentException
        }
    }

    /**
     * 观察网络状态变化事件流（每次变化都会发射）。
     *
     * 与 [isConnected] 不同，此 Flow 基于 callbackFlow，适合需要在单独协程中
     * 处理每次网络变化事件的场景。会自动在协程取消时注销回调。
     * @since 1.0.0
 */
    fun observeNetworkEvents(): Flow<NetworkType> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(resolveNetworkType(network))
            }

            override fun onLost(network: Network) {
                trySend(checkCurrentNetworkType())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(resolveNetworkType(networkCapabilities))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: SecurityException) {
            close()
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()

    private fun recheckConnectivity() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _isConnected.value = connected
        _networkType.value = if (connected) resolveNetworkType(capabilities!!) else NetworkType.NONE
    }

    private fun checkCurrentConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkCurrentNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
        return resolveNetworkType(capabilities)
    }

    private fun resolveNetworkType(network: Network): NetworkType {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkType.OTHER
        return resolveNetworkType(capabilities)
    }

    private fun resolveNetworkType(capabilities: NetworkCapabilities): NetworkType {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }
}
