@file:Suppress("DEPRECATION")

package com.answufeng.net.http.annotations

/**
 * @deprecated 已迁移至 [com.answufeng.net.http.config.NetworkConfig]
 */
@Deprecated(
    message = "Use com.answufeng.net.http.config.NetworkConfig instead",
    replaceWith = ReplaceWith("NetworkConfig", "com.answufeng.net.http.config.NetworkConfig"),
    level = DeprecationLevel.WARNING
)
typealias NetworkConfig = com.answufeng.net.http.config.NetworkConfig

/**
 * @deprecated 已迁移至 [com.answufeng.net.http.config.NetworkConfigProvider]
 */
@Deprecated(
    message = "Use com.answufeng.net.http.config.NetworkConfigProvider instead",
    replaceWith = ReplaceWith("NetworkConfigProvider", "com.answufeng.net.http.config.NetworkConfigProvider"),
    level = DeprecationLevel.WARNING
)
typealias NetworkConfigProvider = com.answufeng.net.http.config.NetworkConfigProvider

/**
 * @deprecated 已迁移至 [com.answufeng.net.http.config.NetworkLogLevel]
 */
@Deprecated(
    message = "Use com.answufeng.net.http.config.NetworkLogLevel instead",
    replaceWith = ReplaceWith("NetworkLogLevel", "com.answufeng.net.http.config.NetworkLogLevel"),
    level = DeprecationLevel.WARNING
)
typealias NetworkLogLevel = com.answufeng.net.http.config.NetworkLogLevel

/**
 * @deprecated 已迁移至 [com.answufeng.net.http.config.CertificatePin]
 */
@Deprecated(
    message = "Use com.answufeng.net.http.config.CertificatePin instead",
    replaceWith = ReplaceWith("CertificatePin", "com.answufeng.net.http.config.CertificatePin"),
    level = DeprecationLevel.WARNING
)
typealias CertificatePin = com.answufeng.net.http.config.CertificatePin
