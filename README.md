# aw-net

[![](https://jitpack.io/v/answufeng/aw-net.svg)](https://jitpack.io/#answufeng/aw-net)

Android 网络基础库，基于 OkHttp + Retrofit + Hilt 封装，支持 HTTP 与 WebSocket。

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
    connectTimeout = 15L,
    readTimeout = 15L,
    writeTimeout = 15L,
    defaultSuccessCode = 0,
    isLogEnabled = true,
    networkLogLevel = NetworkLogLevel.BODY,
    extraHeaders = mapOf("X-App-Version" to "1.0.0"),
    cacheDir = cacheDir,
    cacheSize = 10_000_000L,
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
    config = WebSocketManager.Config(heartbeatIntervalMs = 30_000L),
    listener = object : WebSocketManager.WebSocketListener {
        override fun onOpen(connectionId: String) {}
        override fun onMessage(connectionId: String, text: String) {}
        override fun onClosed(connectionId: String, code: Int, reason: String) {}
        override fun onFailure(connectionId: String, t: Throwable) {}
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

## ❌ 错误码

| 常量 | 值 | 说明 |
|------|-----|------|
| NetCode.Tech.TIMEOUT | -1 | 超时 |
| NetCode.Tech.NO_NETWORK | -2 | 无网络 |
| NetCode.Tech.SSL_ERROR | -3 | SSL 错误 |
| NetCode.Tech.REQUEST_CANCELED | -999 | 请求取消 |
| NetCode.Tech.UNKNOWN | -1000 | 未知错误 |
| NetCode.Tech.PARSE_ERROR | -1001 | JSON 解析失败 |

## 📋 依赖说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| OkHttp | 4.12.0 | HTTP/WebSocket 传输层 |
| Retrofit | 2.11.0 | 类型安全 HTTP 接口 |
| Gson | 2.11.0 | JSON 序列化 |
| Hilt | 2.52 | 依赖注入 |
| Coroutines | 1.9.0 | 协程异步支持 |

## 📄 License

```
Copyright 2024 answufeng

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
