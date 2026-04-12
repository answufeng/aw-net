# aw-net

[![](https://jitpack.io/v/answufeng/aw-net.svg)](https://jitpack.io/#answufeng/aw-net)

Android 网络基础库，基于 OkHttp + Retrofit + Hilt 封装，支持 HTTP 与 WebSocket。

## 📐 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                      应用层                              │
│  ViewModel / Activity → NetworkExecutor (统一入口)       │
└───────────┬─────────────────────────────┬───────────────┘
            │                             │
    ┌───────▼───────┐            ┌────────▼────────┐
    │  HTTP 模块     │            │  WebSocket 模块  │
    │               │            │                 │
    │ RequestExecutor│            │ WebSocketManager│
    │ DownloadExecutor│           │ WebSocketClientImpl│
    │ UploadExecutor │            │                 │
    └───────┬───────┘            └────────┬────────┘
            │                             │
    ┌───────▼─────────────────────────────▼───────────┐
    │                  OkHttp 层                       │
    │  Interceptors → TokenAuthenticator → ConnectionPool│
    └───────────────────────┬─────────────────────────┘
                            │
    ┌───────────────────────▼─────────────────────────┐
    │                  Retrofit 层                     │
    │  GsonConverterFactory → DynamicAnnotations      │
    └─────────────────────────────────────────────────┘
```

**数据流**：应用层通过 `NetworkExecutor` 发起请求 → 委托给具体 Executor → OkHttp 拦截器链处理（BaseUrl/超时/重试/日志/鉴权）→ Retrofit 执行 HTTP 调用 → 结果封装为 `NetworkResult<T>` 返回。

## ✨ 功能特性

### HTTP
- 统一结果包装 `NetworkResult<T>`（Success / TechnicalFailure / BusinessFailure）
- 开箱即用：只需配置 `baseUrl` 即可发起请求
- 文件下载：进度回调、SHA-256 Hash 校验
- 文件上传：单文件/多文件/Multipart，进度回调
- Token 鉴权：自动刷新、401 拦截、并发安全
- 重试机制：指数退避、可配置策略
- 动态配置：运行时切换 BaseUrl（`@BaseUrl`）、按接口超时（`@Timeout`）、按接口重试（`@Retry`）
- 响应映射：自定义 code/msg/data 字段名，兼容不同后端
- 日志格式化：敏感信息脱敏（Header + Body 字段级）、JSON 美化
- 请求监控：`NetEvent` 事件追踪（耗时、成功率）
- 轮询工具：`pollingFlow()` 周期请求
- 请求去重：`RequestDedup` 相同请求合并
- 请求节流：`RequestThrottle` 限制请求间隔
- 连接池优化：可配置空闲连接数和存活时间
- SSL 证书固定：防止中间人攻击
- 网络状态监听：实时监控网络连接和类型变化

### WebSocket
- 多连接管理：同时维护多个 WebSocket 连接
- 心跳检测：可配置间隔和超时
- 断线重连：指数退避 + 随机抖动
- 离线消息队列：断开期间消息自动缓存

## 🔧 环境要求

| 项目 | 最低版本 |
|------|---------|
| Kotlin | 2.0.21+ |
| Android minSdk | 24 |
| Android compileSdk | 35 |
| JDK | 17 |
| AGP | 8.7.3+ |
| Gradle | 8.11+ |

## 📦 引入方式

### 1. 添加 JitPack 仓库

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. 添加依赖

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-net:1.0.0")
}
```

### 3. Hilt 前置要求

aw-net 依赖 Hilt，项目需要集成 Hilt：

```kotlin
// 根 build.gradle.kts
plugins {
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// app/build.gradle.kts
plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
}
```

Application 类添加 `@HiltAndroidApp`：

```kotlin
@HiltAndroidApp
class MyApp : Application()
```

## 🚀 快速开始

### 最小配置：只需一个 baseUrl

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig {
        return NetworkConfig(baseUrl = "https://api.example.com/")
    }
}
```

> `baseUrl` 必须以 `http://` 或 `https://` 开头，以 `/` 结尾。

### 定义 API 接口

```kotlin
interface UserApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Int): GlobalResponse<User>
}
```

### 发起请求

```kotlin
@AndroidEntryPoint
class UserActivity : AppCompatActivity() {

    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var retrofit: Retrofit

    private val api by lazy { retrofit.create(UserApi::class.java) }

    private fun loadUser() {
        lifecycleScope.launch {
            val result = executor.executeRequest { api.getUser(1) }

            result
                .onSuccess { user -> showUser(user) }
                .onBusinessFailure { code, msg -> showToast("业务错误: $msg") }
                .onTechnicalFailure { ex -> showToast("网络错误: ${ex.message}") }
        }
    }
}
```

## 📖 进阶配置

### 完整 NetworkConfig

```kotlin
NetworkConfig(
    baseUrl = "https://api.example.com/",
    connectTimeout = 15L,       // 1~300 秒
    readTimeout = 15L,          // 1~300 秒
    writeTimeout = 15L,         // 1~300 秒
    defaultSuccessCode = 0,
    networkLogLevel = NetworkLogLevel.BODY,  // 替代已废弃的 isLogEnabled
    extraHeaders = mapOf("X-App-Version" to "1.0.0"),
    cacheDir = cacheDir,        // 需与 cacheSize 同时提供
    cacheSize = 10_000_000L,    // 需与 cacheDir 同时提供
    enableRetryInterceptor = true,
    retryMaxAttempts = 2,
    retryInitialBackoffMs = 300L,
    maxIdleConnections = 10,
    keepAliveDurationSeconds = 600,
    certificatePins = listOf(
        CertificatePin("api.example.com", listOf("sha256/AAAA..."))
    ),
    responseFieldMapping = ResponseFieldMapping(
        codeKey = "status", msgKey = "message", dataKey = "result"
    ),
    sensitiveHeaders = NetworkConfig.DEFAULT_SENSITIVE_HEADERS + setOf("x-custom-secret"),
    sensitiveBodyFields = NetworkConfig.DEFAULT_SENSITIVE_BODY_FIELDS + setOf("id_card")
)
```

### Token 鉴权

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides @Singleton
    fun provideTokenProvider(): TokenProvider {
        return InMemoryTokenProvider().apply { updateToken("your-access-token") }
    }

    @Provides @Singleton
    fun provideUnauthorizedHandler(): UnauthorizedHandler {
        return object : UnauthorizedHandler {
            override fun onUnauthorized() { /* Navigate to login */ }
        }
    }
}
```

> Token 刷新机制说明：aw-net 在两个层面处理 401：
> - **HTTP 401**：由 OkHttp 的 `TokenAuthenticator` 自动拦截刷新
> - **业务 code=401**：由 `RequestExecutor` 在协程层处理刷新
> - 两种场景下刷新失败都会触发 `UnauthorizedHandler.onUnauthorized()`

### 动态 BaseUrl

```kotlin
interface FileApi {
    @BaseUrl("https://cdn.example.com/")
    @GET("files/{name}")
    suspend fun downloadFile(@Path("name") name: String): ResponseBody
}
```

### 按接口超时 / 重试

```kotlin
interface SlowApi {
    @Timeout(read = 60, write = 60)
    @POST("heavy-task")
    suspend fun heavyTask(@Body body: RequestBody): GlobalResponse<Result>

    @Retry(maxAttempts = 3, initialBackoffMs = 500)
    @GET("unstable")
    suspend fun unstableEndpoint(): GlobalResponse<Data>
}
```

## 📡 WebSocket

```kotlin
@Inject lateinit var wsManager: IWebSocketManager

wsManager.connectDefault(
    url = "wss://echo.websocket.org",
    config = WebSocketManager.Config(
        heartbeatIntervalMs = 30_000L,
        heartbeatTimeoutMs = 60_000L  // 心跳超时检测，0 表示不检测
    ),
    listener = object : WebSocketManager.WebSocketListener {
        override fun onOpen(connectionId: String) {}
        override fun onMessage(connectionId: String, text: String) {}
        override fun onClosed(connectionId: String, code: Int, reason: String) {}
        override fun onFailure(connectionId: String, t: Throwable) {}
        override fun onHeartbeatTimeout(connectionId: String) {}
    }
)

wsManager.sendText("Hello!")
wsManager.disconnectDefault()
```

## 🔧 NetworkResult 处理

```kotlin
val result: NetworkResult<User> = executor.executeRequest { api.getUser(1) }

// 链式回调
result.onSuccess { }.onBusinessFailure { _, _ -> }.onTechnicalFailure { }

// 统一失败处理
result.onFailure { failure ->
    when (failure) {
        is NetworkResult.TechnicalFailure -> handleError(failure.exception)
        is NetworkResult.BusinessFailure -> handleBizError(failure.code, failure.msg)
        else -> {}
    }
}

// fold 统一处理
val text = result.fold(
    onSuccess = { "用户: ${it?.name}" },
    onTechnicalFailure = { "网络错误" },
    onBusinessFailure = { code, msg -> "业务错误: $msg" }
)

// 快捷取值
val user = result.getOrNull()
val userOrDefault = result.getOrDefault(defaultUser)

// 失败恢复
val recovered = result.recover { defaultUser }
```

> **注意**：`NetworkResult.Success.data` 可能为 null，表示业务成功但无返回数据（如删除/更新操作）。使用 `onSuccessNotNull` 扩展可安全处理非 null 数据。

## ❌ 错误码

| 常量 | 值 | 说明 |
|------|-----|------|
| NetCode.Biz.SUCCESS | 0 | 业务成功 |
| NetCode.Biz.UNAUTHORIZED | 401 | 业务层未授权（非 HTTP 401） |
| NetCode.Biz.FORBIDDEN | 403 | 禁止访问 |
| NetCode.Biz.NOT_FOUND | 404 | 资源不存在 |
| NetCode.Tech.TIMEOUT | -1 | 超时 |
| NetCode.Tech.NO_NETWORK | -2 | 无网络 |
| NetCode.Tech.SSL_ERROR | -3 | SSL 错误 |
| NetCode.Tech.REQUEST_CANCELED | -999 | 请求取消 |
| NetCode.Tech.UNKNOWN | -1000 | 未知错误 |
| NetCode.Tech.PARSE_ERROR | -1001 | JSON 解析失败 |

> **注意**：`NetCode.Biz` 中的常量值与 HTTP 状态码相同，但语义不同。HTTP 层的 401 由 `TokenAuthenticator` 处理，业务层的 401 由 `RequestExecutor` 处理。

## 🛡️ 混淆配置

aw-net 已内置 `consumer-rules.pro`，会自动应用到使用方项目。正常情况下**无需额外配置**。

如果你的项目使用了自定义 ProGuard 规则，请确保不要移除以下 keep 规则：

```proguard
-keep class com.answufeng.net.http.model.** { *; }
-keep class com.answufeng.net.http.annotations.** { *; }
-keep interface com.answufeng.net.websocket.IWebSocketManager { *; }
```

如果你的 API 响应模型类被混淆，Gson 反序列化会失败。请对你的模型类添加 keep 规则或使用 `@SerializedName` 注解。

## 📋 依赖说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| OkHttp | 4.12.0 | HTTP/WebSocket 传输层 |
| Retrofit | 2.11.0 | 类型安全 HTTP 接口 |
| Gson | 2.11.0 | JSON 序列化 |
| Hilt | 2.52 | 依赖注入 |
| Coroutines | 1.9.0 | 协程异步支持 |

## ❓ FAQ

**Q: 如何处理并发请求？**
A: `NetworkExecutor` 是线程安全的，可以在多个协程中同时调用。Token 刷新使用 Mutex 串行化，不会出现重复刷新。

**Q: 如何实现离线优先？**
A: 配置 `cacheDir` 和 `cacheSize` 启用 OkHttp HTTP 缓存，配合 `CacheControl` 头实现离线缓存策略。

**Q: 如何取消请求？**
A: 直接取消发起请求的协程即可（`job.cancel()`），OkHttp 的 `CancellationException` 会被正确处理。

**Q: 如何自定义 OkHttp 配置？**
A: 通过 `@AppInterceptor` 注解提供自定义拦截器，或通过 `OptionalBindingsModule` 替换 `OkHttpClient` 提供。

**Q: `isLogEnabled` 和 `networkLogLevel` 有什么区别？**
A: `isLogEnabled` 已废弃，建议使用 `networkLogLevel`。当 `networkLogLevel = AUTO` 时，回退到 `isLogEnabled` 的行为以兼容旧配置。

## 📄 License

```
Copyright 2024 answufeng

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
