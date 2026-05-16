package com.answufeng.net.http.util

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Cookie 存储加密器接口，用于对持久化的 Cookie 数据进行加解密。
 *
 * 项目层可实现此接口以提供自定义的加密方案（如 AES、Android Keystore 等），
 * 并通过 [PersistentCookieJar] 构造参数注入。
 */
interface CookieEncryptor {
    fun encrypt(plaintext: String): String

    fun decrypt(ciphertext: String): String
}

/**
 * 持久化 Cookie 存储，将 Cookie 以 JSON 格式写入本地文件。
 *
 * 支持可选的 [CookieEncryptor] 加密，启用后磁盘上的 Cookie 数据将以密文存储。
 * 内存中始终保存明文，加密仅在磁盘读写时生效。
 *
 * @param storageFile 持久化文件路径
 * @param encryptor 可选的加解密器，为 null 时不加密（默认）
 */
class PersistentCookieJar(
    private val storageFile: File,
    private val encryptor: CookieEncryptor? = null,
) : CookieJar {
    private val cookies = ConcurrentHashMap<String, MutableList<SerializableCookie>>()
    private val diskWriteExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "aw-cookie-disk").apply { isDaemon = true }
        }

    @Volatile
    private var diskWritePending = false

    @Volatile
    private var diskLoadComplete = false

    companion object {
        private const val DISK_WRITE_DELAY_MS = 500L
    }

    init {
        diskWriteExecutor.execute {
            loadFromDisk()
            diskLoadComplete = true
        }
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookieList: List<Cookie>,
    ) {
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

    fun clear() {
        cookies.clear()
        saveToDiskImmediate()
    }

    fun clearForDomain(domain: String) {
        cookies.remove(domain)
        saveToDiskImmediate()
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) return
        try {
            val raw = storageFile.readText()
            val json = encryptor?.decrypt(raw) ?: raw
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
        if (diskWritePending) return
        diskWritePending = true
        diskWriteExecutor.schedule({
            diskWritePending = false
            doWriteToDisk()
        }, DISK_WRITE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun saveToDiskImmediate() {
        diskWritePending = false
        diskWriteExecutor.execute { doWriteToDisk() }
    }

    private fun doWriteToDisk() {
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
            val plaintext = root.toString()
            val output = encryptor?.encrypt(plaintext) ?: plaintext
            storageFile.writeText(output)
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
        val hostOnly: Boolean,
    ) {
        constructor(cookie: Cookie) : this(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
        )

        fun toCookie(): Cookie {
            val builder =
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .expiresAt(expiresAt)
                    .path(path)
            val cookie =
                if (hostOnly) {
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
                    hostOnly = json.getBoolean("hostOnly"),
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
