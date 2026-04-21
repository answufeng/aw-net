package com.answufeng.net.http.config

/**
 * SSL 证书固定（Certificate Pinning）配置项。
 *
 * 将指定域名模式绑定到一组证书公钥的 SHA-256 哈希值，
 * OkHttp 在 TLS 握手时会校验服务端证书链中是否包含匹配的 pin，
 * 不匹配则拒绝连接，从而有效防止中间人攻击。
 *
 * 使用示例：
 * ```kotlin
 * CertificatePin(
 *     pattern = "*.example.com",
 *     pins = listOf(
 *         "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 *         "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
 *     )
 * )
 * ```
 *
 * @param pattern 域名匹配模式，支持通配符（如 `*.example.com`）
 * @param pins SHA-256 pin 值列表，格式为 `sha256/base64EncodedHash`。
 *            建议至少配置两个（当前 + 备用），以便证书轮换时不中断服务。
 */
data class CertificatePin(
    val pattern: String,
    val pins: List<String>
) {
    init {
        require(pattern.isNotBlank()) {
            "CertificatePin.pattern must not be blank."
        }
        require(pins.isNotEmpty()) {
            "CertificatePin.pins must not be empty for pattern: $pattern"
        }
        pins.forEach { pin ->
            require(pin.startsWith("sha256/") || pin.startsWith("sha1/")) {
                "CertificatePin.pin must start with 'sha256/' or 'sha1/', actual: $pin"
            }
        }
    }
}
