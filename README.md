# aw-net

[![](https://jitpack.io/v/answufeng/aw-net.svg)](https://jitpack.io/#answufeng/aw-net)

基于 **OkHttp、Retrofit、Hilt、Kotlin 协程** 的 Android 网络基础库，覆盖 HTTP/HTTPS、运行时配置、鉴权与重试、上传下载、**WebSocket**。

如果你只想最快接入并发起第一个请求，直接看下面的「5 分钟上手」即可；其它内容都可以后置按需查阅。

---

## 5 分钟上手（最小接入）

### 1) 添加依赖（JitPack）

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.answufeng:aw-net:1.0.0")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
}
```

`implementation` 中的 **版本号与 Git / JitPack 的 tag 一致**（上例为 `1.0.0`）。

### 2) 提供 `NetworkConfig`（Hilt）

```kotlin
@HiltAndroidApp
class MyApp : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {
    @Provides @Singleton
    fun provideNetworkConfig(): NetworkConfig {
        return NetworkConfig(baseUrl = "https://api.example.com/")
    }
}
```

`baseUrl` 须以 `http://` 或 `https://` 开头、**以 `/` 结尾**，且**不能**含 `?` 或 `#`。

### 3) 发起请求（Retrofit + `NetworkExecutor`）

```kotlin
interface UserApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Int): GlobalResponse<User>
}

@AndroidEntryPoint
class UserActivity : AppCompatActivity() {

    @Inject lateinit var executor: NetworkExecutor
    private val api by lazy { executor.createApi<UserApi>() }

    private fun loadUser() {
        lifecycleScope.launch {
            executor.executeRequest { api.getUser(1) }
                .onSuccess { user -> showUser(user) }
                .onBusinessFailure { _, msg -> showToast("业务错误: $msg") }
                .onTechnicalFailure { ex -> showToast("网络错误: ${ex.message}") }
        }
    }
}
```

**推荐**使用 `executeRequest(RequestOption) { }` 承载重试、标签等；多参数重载已标 `@Deprecated`。

---

## 目录（按常见需求跳转）

| 想做什么 | 跳转到 |
|----------|--------|
| 最短时间跑通依赖与请求 | [5 分钟上手（最小接入）](#5-分钟上手最小接入) · [环境要求](#环境要求) |
| 别踩雷（重试、鉴权、埋点等） | [集成约定与踩坑](#集成约定与踩坑) |
| 能力列表 / 与线程的关系 | [功能概览](#功能概览) · [协程与线程](#协程与线程) |
| Token、Config、Flow、Moshi 等 | [进阶话题](#进阶话题)（HTTP） |
| WebSocket | [WebSocket](#websocket) |
| 结果处理、错误码、混淆、FAQ | [NetworkResult](#networkresult) · [错误码](#错误码) · [混淆配置](#混淆配置) · [常见问题](#常见问题) |
| 本地构建与 Demo | [本仓库与工程检查](#本仓库与工程检查) |

---

## 环境要求

| 项目 | 最低版本 |
|------|----------|
| Kotlin | 2.0.21+ |
| Android minSdk | 24 |
| Android compileSdk | 35 |
| Demo targetSdk（验证用） | 35 |
| JDK | 17 |
| AGP | 8.2.2+ |
| Gradle | 8.11+ |

---
## `RequestOption` 与 `requestOption { }` DSL 对照

| 字段 | 默认 | 含义 |
|------|------|------|
| `successCode` | `null`（用 `NetworkConfig.defaultSuccessCode`） | 本请求业务成功码 |
| `dispatcher` | `Dispatchers.IO` | 执行 Retrofit 的调度器 |
| `tag` | `null` | 埋点 / `NetTracker` 业务标签 |
| `retryOnFailure` | `0` | **协程内**额外重试次数；与 OkHttp 拦截器重试**勿同时大开** |
| `retryDelayMs` | `300` | 协程重试基础间隔（指数 + 抖动） |
| `retryOnTechnical` | `true` | 技术失败是否重试 |
| `retryOnBusiness` | `false` | 业务失败是否重试（慎用） |

更多示例见 `demo` 中动态 / 高级配置页面。

---

## 集成约定与踩坑

### 约定速查

| 主题 | 说明 |
|------|------|
| 依赖与版本 | 见 [JitPack](https://jitpack.io/#answufeng/aw-net)；`TAG` 与发布的 Git tag 一致。 |
| JSON | 默认 **Gson**（`GsonConverterFactory`）；`GlobalResponse` 与 `GlobalResponseTypeAdapterFactory` 与 Gson 绑定。换 **Moshi** 等需自定义 `NetworkClientFactory` 并在 Hilt 中覆盖，见下 [自定义 Retrofit / Converter](#自定义-retrofit--converter-moshi-等)。 |
| **重试只开一层** | `NetworkConfig.enableRetryInterceptor` = **OkHttp 拦截器**重试；`RequestExecutor` / `NetworkExecutor` 的 `retryOnFailure` = **协程内**重试。同时开启会**叠加退避**。只选一层；若两者都开且 `retryOnFailure > 0`，**Debug** 下 `RequestExecutor` 会 `Log.w`（Release 无）。 |
| 拦截器重试与日志 | 拦截器在内侧多次 `proceed` 时，**不会**反复经过最外层带格式化的日志拦截器。需要「每轮重试都记日志」请调整装配或改用协程重试并关一层 OkHttp 重试。 |
| 慢请求 | `NetworkConfig.slowRequestThresholdMs` 有值时，受追踪的 execute / download / upload 超时会 `Log.w`；可与 `NetTracker` 的 END 事件拼 SLA。 |
| Hilt 可选依赖 | `TokenProvider`、`@NetLogger` 等以 `java.util.Optional` 表示可缺省；未提供时可用 [NoOpNetLogger](aw-net/src/main/java/com/answufeng/net/http/util/NoOpNetLogger.kt)。纯 Kotlin 也可在自有 `@Module` 里显式 `NoOpNetLogger`。 |
| `NetTracker` | 建议用 Hilt 提供 `com.answufeng.net.http.annotations.NetTracker` 实现，勿在 `Application` 里**再**手动设 `delegate` 与模块冲突。仅关库内埋点用 `NetworkConfig.enableRequestTracking = false`（与「运行时配置」表一致）。 |

### 误用与后果

| 误用 | 后果 | 正确做法 |
|------|------|----------|
| 拦截器重试 + 协程 `retryOnFailure` 同时大开 | 退避叠加、耗时与负载放大 | **只开一层**；见上表 |
| WebSocket 握手 401/403 指望走 HTTP `TokenRefreshCoordinator` | 与 HTTP 鉴权**不一致** | 建连前保证 URL/Token 有效，业务层自行重连（见 `WebSocketManager` KDoc） |
| 重复设 `NetTracker.delegate` 与 Hilt 混用 | 双写或覆盖 | 单一来源，或关 `enableRequestTracking` |

### 响应模型：`BaseResponse` 与 `GlobalResponse`

```kotlin
interface BaseResponse<T> {
    val code: Int
    val msg: String
    val data: T?
}
```

| 类型 | 说明 |
|------|------|
| `GlobalResponse<T>` | 默认实现（`code` / `msg` / `data`），大多数接口直接用。 |
| 自定义 | 字段名不同可用 `NetworkConfig.responseFieldMapping` 或自实现 `BaseResponse<T>`。 |

---

## 功能概览

**HTTP**  
`NetworkResult<T>` 统一成功 / 技术失败 / 业务失败；下载、上传、进度、Hash；`TokenRefreshCoordinator`（HTTP 401 与业务 401）；协程 + 拦截器两套重试（勿叠用）；`@BaseUrl` `@Timeout` `@Retry`；脱敏与格式化日志；`NetEvent`；`pollingFlow`、`RequestDedup`、`RequestThrottle`、`RequestCanceller`；`PersistentCookieJar`、`MockInterceptor`；连接池/证书钉/可选 HTTP 缓存；`NetworkMonitor`。

**WebSocket**  
多连接、心跳、断线重连、离线队列、`connectionStateFlow` 等，详见 [WebSocket](#websocket)。

---

## 协程与线程

| API | 线程 | 说明 |
|-----|------|------|
| `NetworkExecutor.executeRequest(…)` | 任意 | 内部协程安全 |
| `NetworkExecutor.requestResultFlow(…)` | 任意 | 单发 **Cold Flow**（`executeRequestFlow` 等已弃用、语义相同） |
| `NetworkConfigProvider.updateConfig()` | 任意 | `AtomicReference` |
| `WebSocketManager.connect` / `sendMessage` / `disconnect` | 任意 | 实现侧同步安全 |
| `WebSocketListener` 回调 | 默认主线程 | `Config.callbackOnMainThread` 可关 |
| `NetworkMonitor` 部分 API | 主线程 | `ConnectivityManager` 要求 |

### OkHttp 连接池与超时（宿主调参参考）

| 项 | 建议区间 | 说明 |
|----|----------|------|
| `connectTimeout` | 约 5～15s | 弱网可略增，过长易排队 |
| `readTimeout` / `writeTimeout` | 约 10～30s | 大文件/慢接口用 `@Timeout` 或独立 client |
| `callTimeout` | 0 或整体上限 | 与重试叠加时非 0 易难查 |
| `ConnectionPool` | 默认多够用 | 极多 Host 或长连再调大并监控 |

---

## 进阶话题

> 以下为常用扩展场景；**Token / 日志 / 注解 / NetworkConfig 完整样例**等均保留在下方，可按标题跳转或搜索。

### Token 鉴权

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides @Singleton
    fun provideTokenProvider(): TokenProvider {
        return InMemoryTokenProvider().apply { setAccessToken("your-access-token") }
    }

    @Provides @Singleton
    fun provideUnauthorizedHandler(): UnauthorizedHandler {
        return object : UnauthorizedHandler {
            override fun onUnauthorized() { /* 跳转登录等 */ }
        }
    }
}
```

机制概要：**HTTP 401** 走 OkHttp `TokenAuthenticator`；**业务 code=401** 走 `RequestExecutor`；两条路径经 `TokenRefreshCoordinator` 同一把可重入锁，避免重复刷新。失败时走 `UnauthorizedHandler.onUnauthorized()`。

### 自定义日志 `NetLogger`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LoggerModule {
    @Provides @Singleton @NetLogger
    fun provideNetLogger(): NetLogger {
        return object : NetLogger {
            override fun d(tag: String, msg: String) {
                Timber.tag(tag).d(msg)
            }
            override fun e(tag: String, msg: String, throwable: Throwable?) {
                Timber.tag(tag).e(throwable, msg)
            }
        }
    }
}
```

### 动态 `BaseUrl`、按接口超时/重试、按接口成功码

```kotlin
interface FileApi {
    @BaseUrl("https://cdn.example.com/")
    @GET("files/{name}")
    suspend fun downloadFile(@Path("name") name: String): ResponseBody
}

interface SlowApi {
    @Timeout(read = 60, write = 60)
    @POST("heavy-task")
    suspend fun heavyTask(@Body body: RequestBody): GlobalResponse<Result>

    @Retry(maxAttempts = 3, initialBackoffMs = 500)
    @GET("unstable")
    suspend fun unstableEndpoint(): GlobalResponse<Data>
}
```

成功码优先级：**`executeRequest(successCode=…)` 参数** > **`@SuccessCode`** > **全局 `defaultSuccessCode`**。

```kotlin
interface MultiCodeApi {
    @SuccessCode(200)
    @GET("legacy-api")
    suspend fun legacyApi(): GlobalResponse<Data>

    @SuccessCode(0)
    @GET("new-api")
    suspend fun newApi(): GlobalResponse<Data>
}

executor.executeRequest(successCode = 200) { api.getUser(1) }
```

### `NetworkConfig`：Builder 与较完整示例

```kotlin
NetworkConfig.builder("https://api.example.com/").apply {
    connectTimeout = 15L
    readTimeout = 15L
    networkLogLevel = NetworkLogLevel.BODY
    extraHeaders = mapOf("X-App-Version" to "1.0.0")
}.build()
```

```kotlin
import com.answufeng.net.http.config.CertificatePin

NetworkConfig(
    baseUrl = "https://api.example.com/",
    connectTimeout = 15L,
    readTimeout = 15L,
    writeTimeout = 15L,
    defaultSuccessCode = 0,
    networkLogLevel = NetworkLogLevel.BODY,
    extraHeaders = mapOf("X-App-Version" to "1.0.0"),
    cacheDir = cacheDir,
    cacheSize = 10_000_000L,
    enableRetryInterceptor = true,
    retryMaxAttempts = 2,
    retryInitialBackoffMs = 300L,
    maxIdleConnections = 5,
    keepAliveDurationSeconds = 300,
    certificatePins = listOf(
        CertificatePin("api.example.com", listOf("sha256/AAAA..."))
    ),
    cookieJar = PersistentCookieJar(File(cacheDir, "cookies")),
    responseFieldMapping = ResponseFieldMapping(
        codeKey = "status", msgKey = "message", dataKey = "result"
    ),
    sensitiveHeaders = NetworkConfig.DEFAULT_SENSITIVE_HEADERS + setOf("x-custom-secret"),
    sensitiveBodyFields = NetworkConfig.DEFAULT_SENSITIVE_BODY_FIELDS + setOf("id_card")
)
```

### `RequestOption` 显式传参

```kotlin
val result = executor.executeRequest(
    option = RequestOption(
        retryOnFailure = 3,
        tag = "getUserInfo",
        retryDelayMs = 500L
    )
) { api.getUser(1) }
```

```kotlin
import com.answufeng.net.http.model.requestOption

val result = executor.executeRequest(
    option = requestOption {
        tag = "getUserInfo"
        retryOnFailure = 3
        retryDelayMs = 500L
    }
) { api.getUser(1) }
```

### 单发 Flow：`requestResultFlow` / `rawRequestResultFlow`

与单次 `executeRequest` 等价，便于 `stateIn` 等。已弃用名称：`executeRequestFlow`、`executeRawRequestFlow`（行为相同）。

```kotlin
val userState = executor.requestResultFlow { api.getUser(1) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

### 自定义 Retrofit / Converter（Moshi 等）

库默认在 `NetworkModule` 中提供 `NetworkClientFactory`（Gson + `GlobalResponseTypeAdapterFactory`）。应用侧 `@Provides @Singleton` **同类型覆盖** 即可。使用非 Gson 时须保证 DTO 与 `BaseResponse` 在所选 Converter 下可解析；`GlobalResponseTypeAdapterFactory` 仅随 Gson 注册。

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppNetFactoryModule {
    @Provides @Singleton
    fun provideNetworkClientFactory(
        client: OkHttpClient
    ): NetworkClientFactory {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return object : NetworkClientFactory {
            override fun createRetrofit(baseUrl: String): Retrofit {
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
            }
        }
    }
}
```

### 网络状态

```kotlin
@Inject lateinit var networkMonitor: NetworkMonitor

lifecycleScope.launch {
    networkMonitor.isConnected.collect { connected ->
        if (connected) {
            val type = networkMonitor.networkType.value
        }
    }
}
```

### 请求监控 `NetTracker`

`NetTracker` 的 `onEvent` 抛错时库内会 `Log.w` 且**不会**让 `executeRequest` 失败；`trackAsync` 另有约 5s 超时。建议在 `onEvent` 内自行 try/catch，避免无意义刷日志。

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object TrackerModule {
    @Provides @Singleton
    fun provideNetTracker(): NetTracker {
        return object : NetTracker {
            override fun onEvent(event: NetEvent) {
                analytics.logEvent("net_request", bundleOf(
                    "name" to (event.name ?: ""),
                    "tag" to (event.tag ?: ""),
                    "stage" to event.stage.name,
                    "duration_ms" to (event.durationMs ?: -1L),
                    "result" to (event.resultType ?: "")
                ))
            }
        }
    }
}
```

对 **END** 事件按耗时自行上报的示例（可与 `slowRequestThresholdMs` 互补）：

```kotlin
import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage

override fun onEvent(event: NetEvent) {
    if (event.stage != NetEventStage.END) return
    val ms = event.durationMs ?: return
    if (ms > 3_000) {
        // 慢请求：结合 event.name、event.tag、event.resultType
    }
}
```

若已在 `NetworkConfig` 同时提供 `cacheDir` 与 `cacheSize`，`NetworkModule` 会为 `OkHttpClient` 装配**磁盘** `Cache`（受 `Cache-Control` 等约束，GET 等场景；POST 一般仍走网）。

### 运行时配置 `NetworkConfigProvider`

| 能力 / 字段 | 仅 `update` 后是否对**新请求**生效 | 说明 |
|-------------|----------------------------------|------|
| `baseUrl` | 是 | 含 `DynamicBaseUrlInterceptor`；另有 `@BaseUrl` |
| `networkLogLevel` / `extraHeaders` / `defaultSuccessCode` / `responseFieldMapping` / 脱敏集合 | 是 | 按请求读 `current` |
| `enableRequestTracking` / `slowRequestThresholdMs` | 是 | 见 KDoc |
| 方法上 `@Timeout` | 是 | 仅该次 `Chain` |
| 全局 `connectTimeout` / `read` / `write`（`NetworkConfig` 里改秒数） | 否* | 单例 `OkHttpClient` 已构建；可改单接口 `@Timeout` 或自建 Client |
| 连接池、缓存、证书钉、cookieJar 等 | 否* | 构建 Client 时钉死 |
| `tokenRefreshLockAcquireTimeoutMs` | ⚠️ | 以 `TokenRefreshCoordinator` **首次**创建时为准 |

\*「否」指仅 `update { copy(…) }` 不会魔法般改已存在的 Client 行为。

```kotlin
@Inject lateinit var configProvider: NetworkConfigProvider

configProvider.updateConfig(
    configProvider.current.copy(baseUrl = "https://staging.api.example.com/")
)
```

### 取消、`Cookie`、`Mock`

**RequestCanceller**

```kotlin
val canceller = RequestCanceller()

lifecycleScope.launch {
    canceller.register("loadUser", this.coroutineContext[Job]!!)
    executor.executeRequest { api.getUser() }
}

override fun onDestroy() {
    super.onDestroy()
    canceller.cancelByTag("loadUser")
}
```

**PersistentCookieJar**

```kotlin
val cookieJar = PersistentCookieJar(File(cacheDir, "network_cookies"))
NetworkConfig.builder("https://api.example.com/").cookieJar(cookieJar).build()
cookieJar.clearForDomain("api.example.com")
cookieJar.clear()
```

**MockInterceptor（仅建议 Debug）**

```kotlin
val mockInterceptor = MockInterceptor(enable = BuildConfig.DEBUG).apply {
    mock("/users", """{"code":0,"msg":"success","data":[{"id":1,"name":"Alice"}]}""")
    mock("/login", 401, """{"code":401,"msg":"unauthorized","data":null}""")
    mock("/slow-api", 200, """{"code":0,"msg":"ok"}""", delayMs = 1000)
    mock("/items/*", """{"code":0,"msg":"ok","data":[]}""")
}
```

---

## WebSocket

### 最小示例

```kotlin
@Inject lateinit var wsManager: WebSocketManager

wsManager.connectDefault(
    url = "wss://echo.example.com",
    listener = object : WebSocketManager.WebSocketListener {
        override fun onOpen(connectionId: String) { }
        override fun onMessage(connectionId: String, text: String) { }
        override fun onMessage(connectionId: String, bytes: ByteArray) { }
        override fun onClosing(connectionId: String, code: Int, reason: String) { }
        override fun onClosed(connectionId: String, code: Int, reason: String) { }
        override fun onFailure(connectionId: String, t: Throwable) { }
        override fun onHeartbeatTimeout(connectionId: String) { }
        override fun onStateChanged(
            connectionId: String, oldState: WebSocketManager.State, newState: WebSocketManager.State
        ) { }
        override fun onReconnecting(connectionId: String, attempt: Int) { }
    }
)
wsManager.sendText("Hello!")
wsManager.disconnectDefault()
// 建议页面或 ViewModel 销毁时调用 disconnectAll() 或 close()（AutoCloseable）
```

WebSocket 与 HTTP `Authenticator` **不自动打通**；建连前自行处理 Token/Query。多连接、销毁时调用 `disconnectAll()` 或 `close()` 等见 `WebSocketManager` KDoc。

| `Config` 字段 | 默认 | 说明 |
|---------------|------|------|
| `heartbeatIntervalMs` | 30000 | 0 = 不发送心跳 |
| `heartbeatTimeoutMs` | 60000 | 超时可触发重连 |
| `heartbeatMessage` | `"ping"` | 心跳内容 |
| `enableHeartbeat` | `true` | 是否启用心跳 |
| `wsLogLevel` | `BASIC` | `NONE` / `BASIC` / `FULL` |
| `callbackOnMainThread` | `true` |  listener 运行线程 |
| `messageQueueCapacity` | 100 | 离线队列 |
| `dropOldestWhenQueueFull` | `false` | 满时是否丢最旧 |
| `enableMessageReplay` | `true` | 重连后是否补发 |
| `connectTimeout` / `readTimeout` / `writeTimeout` | 10 / 0 / 10（秒） | 读为 0 表示无限（依 OkHttp 语义） |
| `maxReconnectAttempts` | 0 | 0 = 无限 |
| `reconnectBaseDelayMs` / `reconnectMaxDelayMs` | 1000 / 30000 | 重连退避 |

```kotlin
wsManager.connect("chat", "wss://chat.example.com", listener = chatListener)
wsManager.sendMessage("chat", "Hello!")
wsManager.disconnect("chat")

lifecycleScope.launch {
    wsManager.connectionStateFlow.collect { stateMap ->
        val chatState = stateMap["chat"]
    }
}
```

| 回调 | 时机 |
|------|------|
| `onOpen` | 已连接 |
| `onMessage`（文本/二进制） | 收到消息 |
| `onClosing` | 收到 close、尚未完成 |
| `onClosed` | 已关闭 |
| `onFailure` | 错误 |
| `onHeartbeatTimeout` | 心跳超时 |
| `onStateChanged` | 状态变化 |
| `onReconnecting` | 正在重连 |

---

## NetworkResult

```kotlin
val result: NetworkResult<User> = executor.executeRequest { api.getUser(1) }

result.onSuccess { }.onBusinessFailure { _, _ -> }.onTechnicalFailure { }

val text = result.fold(
    onSuccess = { "用户: ${it?.name}" },
    onTechnicalFailure = { "网络错误" },
    onBusinessFailure = { code, msg -> "业务错误: $msg" }
)

val user = result.getOrNull()
val recovered = result.recover { defaultUser }
```

`NetworkResult.Success.data` 可能为 `null`（无体业务成功），可用 `onSuccessNotNull` 等扩展（见 `NetworkResultExt`）。

---

## 错误码

| 常量 | 值 | 说明 |
|------|-----|------|
| `NetCode.Business.SUCCESS` | 0 | 业务成功 |
| `NetCode.Business.UNAUTHORIZED` | 401 | 业务未授权（非 HTTP 401 的语义，勿与协议层混读） |
| `NetCode.Business.FORBIDDEN` | 403 | 禁止访问 |
| `NetCode.Business.NOT_FOUND` | 404 | 资源不存在 |
| `NetCode.Technical.TIMEOUT` | -1 | 超时 |
| `NetCode.Technical.NO_NETWORK` | -2 | 无网络 |
| `NetCode.Technical.SSL_ERROR` | -3 | SSL 错误 |
| `NetCode.Technical.REQUEST_CANCELED` | -999 | 取消 |
| `NetCode.Technical.UNKNOWN` | -1000 | 未知 |
| `NetCode.Technical.PARSE_ERROR` | -1001 | JSON 解析失败 |

> `NetCode.Business` 的数值可与 HTTP 状态码同号，**语义是业务体**，与 HTTP 层 401/403 等不要混用；HTTP 401 与业务未授权均可能经 `TokenRefreshCoordinator` 处理，以你的后端契约为准。

---

## 混淆配置

`aw-net` 通过 `consumer-rules.pro` 随 AAR 下发，**多数应用无需再写**。

请**勿删除**对下列包/接口的保留类规则（并自行保留业务 DTO/接口实现类）：

```proguard
-keep class com.answufeng.net.http.config.** { *; }
-keep class com.answufeng.net.http.annotations.** { *; }
-keep class com.answufeng.net.http.model.** { *; }
-keep interface com.answufeng.net.websocket.WebSocketManager { *; }
```

```proguard
-keep class com.yourapp.model.** { *; }
```

发版后可在 `mapping.txt` 中抽查本库包名。

---

## 依赖说明（主要传递版本，以仓库 `libs` / Gradle 为准）

| 依赖 | 版本（参考） | 用途 |
|------|----------------|------|
| OkHttp | 4.12.0 | HTTP / WebSocket |
| Retrofit | 2.11.0 | API |
| Hilt | 2.52 | DI |
| Coroutines | 1.9.0 | 协程 |
| Gson | 随 Retrofit 转换器 | 默认 JSON |

---

## 本仓库与工程检查

| 项 | 说明 |
|----|------|
| Demo | 模块 `demo/`，主界面卡片；工具栏 **「演示清单」**；能力矩阵 [demo/DEMO_MATRIX.md](demo/DEMO_MATRIX.md) |
| 本地建议命令 | `./gradlew :aw-net:assembleRelease :aw-net:ktlintCheck :aw-net:lintRelease :demo:assembleRelease`（需 **JDK 17**） |
| CI | [`.github/workflows/ci.yml`](.github/workflows/ci.yml)：assemble、ktlint、R8 冒烟、Lint |
| 贡献 | [CONTRIBUTING.md](CONTRIBUTING.md) |
| 本机仍 Java 11？ | 为 Gradle 配置 **JDK 17**（`JAVA_HOME` 或 [gradle.properties](gradle.properties) 注释 / CONTRIBUTING） |

---

## 常见问题

| 问题 | 简要答复 |
|------|----------|
| 并发与 Token 刷新？ | 多协程可并发调 `NetworkExecutor`；`TokenRefreshCoordinator` 可重入锁 + 持锁超时，协程与 `Authenticator` 共用。 |
| 离线优先？ | `cacheDir` + `cacheSize` 开 OkHttp 缓存，配合响应 `Cache-Control` 等。 |
| 取消请求？ | 取消协程 `Job` 即可，`CancellationException` 会正确外抛。 |
| 深度自定义 `OkHttpClient`？ | 优先 `NetworkConfig` + 文档「运行时配置」表；应用拦截器用 `@AppInterceptor` 的 `Map<…, Interceptor>`；需完全替换时合并/排除 Hilt 的 `NetworkModule` 中 `OkHttp` 单例，避免重复 `OkHttpClient` 绑定。WebSocket 可用 `@WebSocketClient` 独立 Client。 |
| 日志级别？ | HTTP：`NetworkConfig.networkLogLevel`；WebSocket：`Config.wsLogLevel`。 |
| 为何出现 `java.util.Optional`？ | Hilt 表示可选绑定；应用侧可按需 `@Provides`，语义与 `Optional` 空一致。 |

---

## 许可证

Apache License 2.0，见 [LICENSE](LICENSE)。
