package com.answufeng.net.http.util

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper

/**
 * 持久化 CookieJar 实现，将 Cookie 缓存到内存并持久化到本地文件。
 *
 * 使用 JSON 格式序列化，比 Java 标准序列化更安全、更可读、更易调试。
 *
 * ### 用法
 * ```kotlin
 * val cookieJar = PersistentCookieJar(File(context.cacheDir, "network_cookies.json"))
 * val client = OkHttpClient.Builder()
 *     .cookieJar(cookieJar)
 *     .build()
 * ```
 *
 * 或通过 [NetworkConfig.cookieJar] 注入：
 * ```kotlin
 * NetworkConfig.builder("https://api.example.com/")
 *     .cookieJar(PersistentCookieJar(File(cacheDir, "cookies.json")))
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
    private val diskWriteHandler = Handler(Looper.getMainLooper())
    private val diskWriteRunnable = Runnable { saveToDiskImmediate() }
    private var diskWritePending = false

    companion object {
        private const val DISK_WRITE_DELAY_MS = 500L
    }

    init {
        loadFromDisk()
    }

    override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
        val host = url.host
        val existing = cookies.getOrPut(host) { mutableListOf() }

        synchronized(existing) {
            for (cookie in cookieList) {
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
        saveToDiskImmediate()
    }

    /**
     * 清除指定域名的 Cookie。
     * @since 1.1.0
     */
    fun clearForDomain(domain: String) {
        cookies.remove(domain)
        saveToDiskImmediate()
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) return
        try {
            val json = storageFile.readText()
            val root = JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val domain = keys.next()
                val arr = root.getJSONArray(domain)
                val list = mutableListOf<SerializableCookie>()
                for (i in 0 until arr.length()) {
                    list.add(SerializableCookie.fromJson(arr.getJSONObject(i)))
                }
                cookies[domain] = list
            }
        } catch (_: Exception) {
            cookies.clear()
        }
    }

    private fun saveToDisk() {
        synchronized(this) {
            if (diskWritePending) {
                diskWriteHandler.removeCallbacks(diskWriteRunnable)
            }
            diskWritePending = true
            diskWriteHandler.postDelayed(diskWriteRunnable, DISK_WRITE_DELAY_MS)
        }
    }

    private fun saveToDiskImmediate() {
        synchronized(this) {
            diskWritePending = false
        }
        try {
            storageFile.parentFile?.mkdirs()
            val root = JSONObject()
            cookies.forEach { (domain, list) ->
                val arr = JSONArray()
                synchronized(list) {
                    list.forEach { arr.put(it.toJson()) }
                }
                root.put(domain, arr)
            }
            storageFile.writeText(root.toString())
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
    ) {

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

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("name", name)
                put("value", value)
                put("expiresAt", expiresAt)
                put("domain", domain)
                put("path", path)
                put("secure", secure)
                put("httpOnly", httpOnly)
                put("hostOnly", hostOnly)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): SerializableCookie {
                return SerializableCookie(
                    name = json.getString("name"),
                    value = json.getString("value"),
                    expiresAt = json.getLong("expiresAt"),
                    domain = json.getString("domain"),
                    path = json.getString("path"),
                    secure = json.getBoolean("secure"),
                    httpOnly = json.getBoolean("httpOnly"),
                    hostOnly = json.getBoolean("hostOnly")
                )
            }
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
