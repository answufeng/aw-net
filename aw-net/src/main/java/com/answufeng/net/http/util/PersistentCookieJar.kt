package com.answufeng.net.http.util

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * 持久化 CookieJar 实现，将 Cookie 缓存到内存并持久化到本地文件。
 *
 * ### 用法
 * ```kotlin
 * val cookieJar = PersistentCookieJar(File(context.cacheDir, "network_cookies"))
 * val client = OkHttpClient.Builder()
 *     .cookieJar(cookieJar)
 *     .build()
 * ```
 *
 * 或通过 [NetworkConfig.cookieJar] 注入：
 * ```kotlin
 * NetworkConfig.builder("https://api.example.com/")
 *     .cookieJar(PersistentCookieJar(File(cacheDir, "cookies")))
 *     .build()
 * ```
 *
 * @param storageFile Cookie 持久化文件路径
 * @since 1.1.0
 */
class PersistentCookieJar(
    private val storageFile: File
) : CookieJar {

    private val cookies = ConcurrentHashMap<String, MutableList<SerializableCookie>>()

    init {
        loadFromDisk()
    }

    override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
        val host = url.host
        val existing = cookies.getOrPut(host) { mutableListOf() }

        synchronized(existing) {
            for (cookie in cookieList) {
                val key = cookie.name + "@" + cookie.domain
                existing.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                if (!cookie.expiresAt.let { exp -> exp < System.currentTimeMillis() && exp != Long.MAX_VALUE }) {
                    existing.add(SerializableCookie(cookie))
                }
            }
        }

        saveToDisk()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val result = mutableListOf<Cookie>()
        val expired = mutableListOf<SerializableCookie>()

        cookies.forEach { (domain, cookieList) ->
            synchronized(cookieList) {
                val iterator = cookieList.iterator()
                while (iterator.hasNext()) {
                    val sc = iterator.next()
                    val cookie = sc.toCookie()
                    if (cookie.expiresAt.let { it < System.currentTimeMillis() && it != Long.MAX_VALUE }) {
                        expired.add(sc)
                        iterator.remove()
                    } else if (cookie.matches(url)) {
                        result.add(cookie)
                    }
                }
            }
        }

        if (expired.isNotEmpty()) {
            saveToDisk()
        }

        return result
    }

    /**
     * 清除所有 Cookie。
     * @since 1.1.0
     */
    fun clear() {
        cookies.clear()
        saveToDisk()
    }

    /**
     * 清除指定域名的 Cookie。
     * @since 1.1.0
     */
    fun clearForDomain(domain: String) {
        cookies.remove(domain)
        saveToDisk()
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) return
        try {
            ObjectInputStream(FileInputStream(storageFile)).use { ois ->
                @Suppress("UNCHECKED_CAST")
                val loaded = ois.readObject() as? Map<String, MutableList<SerializableCookie>> ?: return
                cookies.putAll(loaded)
            }
        } catch (_: Exception) {
            cookies.clear()
        }
    }

    private fun saveToDisk() {
        try {
            storageFile.parentFile?.mkdirs()
            ObjectOutputStream(FileOutputStream(storageFile)).use { oos ->
                oos.writeObject(cookies.toMap())
            }
        } catch (_: Exception) {
        }
    }

    internal data class SerializableCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    ) : Serializable {

        constructor(cookie: Cookie) : this(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly
        )

        fun toCookie(): Cookie {
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(path)
            val cookie = if (hostOnly) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }
            return if (secure && httpOnly) {
                cookie.secure().httpOnly().build()
            } else if (secure) {
                cookie.secure().build()
            } else if (httpOnly) {
                cookie.httpOnly().build()
            } else {
                cookie.build()
            }
        }

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    private fun Cookie.matches(url: HttpUrl): Boolean {
        if (hostOnly && url.host != domain) return false
        if (!hostOnly && !url.host.endsWith(domain)) return false
        if (secure && !url.isHttps) return false
        if (path != "/" && !url.encodedPath.startsWith(path)) return false
        return true
    }
}
