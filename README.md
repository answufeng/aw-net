# aw-net

[![](https://jitpack.io/v/answufeng/aw-net.svg)](https://jitpack.io/#answufeng/aw-net)

Android 网络基础库，基于 **OkHttp**、**Retrofit**、**Hilt** 与 **Kotlin 协程**，提供 HTTP/HTTPS、可运行时切换的配置、鉴权与重试、上传下载与 **WebSocket** 等能力。本文档按「先上手、再深入」组织，示例代码默认 Kotlin + Hilt。

## 环境要求

| 项目 | 最低版本 |
|------|---------|
| Kotlin | 2.0.21+ |
| Android minSdk | 24 |
| Android compileSdk | 35 |
| Demo targetSdk（集成验证） | 35 |
| JDK | 17 |
| AGP | 8.2.2+ |
| Gradle | 8.11+ |

## 使用须知

- **依赖来源**：通过 [JitPack](https://jitpack.io/#answufeng/aw-net) 按 Git tag 版本引用；`implementation("com.github.answufeng:aw-net:TAG")` 中的 **TAG 需与发布 tag 一致**（见 [CHANGELOG](CHANGELOG.md)）。
- **JSON 与 Retrofit 转换器**：默认使用 **Gson**（`GsonConverterFactory`）；`GlobalResponse` 与 `GlobalResponseTypeAdapterFactory` 与 Gson 绑定。若需 **Moshi** 等，请自定义 `NetworkClientFactory` 并以 Hilt 覆盖单例，见下「自定义 Retrofit / Converter」。
- **重试只开一层（重要）**：`NetworkConfig.enableRetryInterceptor` 在 **OkHttp 拦截器层**重试；`RequestExecutor` / `NetworkExecutor` 的 `retryOnFailure` 是 **协程内** 重试。同时开启时总退避会**叠加放大**——请**只选其中一层**。Debug 构建下若两者同时启用且 `retryOnFailure > 0`，`RequestExecutor` 会打一条 **Log.w** 提示（Release 无此日志）。
- **慢请求**：`NetworkConfig.slowRequestThresholdMs` 非空时，单次受追踪的 execute / download / upload 若总耗时超过阈值会输出 **Log.w**（`AwNetTrackable`）；可与下方 `NetTracker` 模板在 END 事件的 `durationMs` 上自建 SLA 告警。
- **Hilt 可选依赖**：`TokenProvider`、`NetLogger` 等以 `java.util.Optional` 注入，表示「应用可选提供」；未提供时库内走默认/无操作实现。
- **NetTracker**：推荐用 Hilt 提供 `com.answufeng.net.http.annotations.NetTracker` 实现；`NetworkModule` 会写入 `NetTracker.delegate`。请避免在 `Application` 里**再**手动赋值 `delegate` 导致行为混乱。若只关闭**库内**请求/上传/下载的埋点事件，用 `NetworkConfig.enableRequestTracking = false`（见「运行时配置」表）。
- **baseUrl 格式**：须以 `http://` 或 `https://` 开头、**以 `/` 结尾**；**不得**含 query（`?…`）或 fragment（`#…`）。允许带路径前缀，例如 `https://api.example.com/v1/`。

## 工程品质与本地检查

- **CI**（[`.github/workflows/ci.yml`](.github/workflows/ci.yml)）：JDK 17 下对 `:aw-net:assembleRelease`、**ktlint**、`:demo:assembleRelease`（R8 冒烟）、**Android Lint** 做基础门禁；**不含**仓库内单元测试（本库以 **单 module + demo** 验证为主）。
- **本地一键（推荐）**：`./gradlew :aw-net:assembleRelease :aw-net:ktlintCheck :aw-net:lintRelease :demo:assembleRelease`
- **演示**：[demo/DEMO_MATRIX.md](demo/DEMO_MATRIX.md)；demo 工具栏 **「演示清单」**。
- **上线前**：确认 **重试只开一层**（OkHttp 拦截器 vs 协程 `retryOnFailure`）；Release 关闭调试 Mock；核对 `NetworkConfig` 与证书/埋点策略。
- **ktlint**（[Kotlin 官方风格](https://kotlinlang.org/docs/coding-conventions.html)）：`./gradlew :aw-net:ktlintCheck`；若未通过，先执行 `./gradlew :aw-net:ktlintFormat` 再提交流程。
- **协作者**：[CONTRIBUTING.md](CONTRIBUTING.md)（环境、提交流程、常见任务）。

## Quick Start (3 Steps)

### Step 1: 添加依赖

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
    // Hilt 前置（aw-net 依赖 Hilt）
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
}
```

### Step 2: 提供 NetworkConfig

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

> `baseUrl` 必须以 `http://` 或 `https://` 开头、以 `/` 结尾，且**不能**包含 `?` 或 `#`（与 `NetworkConfig` 构建时校验一致）。

### Step 3: 发起请求

```kotlin
// 定义接口
interface UserApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Int): GlobalResponse<User>
}

// 使用
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

### RequestOption 速查（与 `requestOption { }` DSL 一致）

| 字段 | 默认值 | 含义 |
|------|--------|------|
| `successCode` | `null`（用全局 `NetworkConfig.defaultSuccessCode`） | 本次请求的成功业务码 |
| `dispatcher` | `Dispatchers.IO` | 执行 Retrofit 调用的协程调度器 |
| `tag` | `null` | 埋点 / `NetTracker` 业务标签 |
| `retryOnFailure` | `0` | **协程内**额外重试次数（不含首次）；与 OkHttp `DynamicRetryInterceptor` **勿叠开** |
| `retryDelayMs` | `300` | 协程重试基础间隔（含指数与抖动） |
| `retryOnTechnical` | `true` | 技术失败是否继续重试 |
| `retryOnBusiness` | `false` | 业务失败是否重试（慎用） |

DSL 用法见上表；具体调用可参考仓库 `demo` 中动态/高级配置相关页面。

## 演示应用

模块 `demo/` 为主界面卡片导航；工具栏菜单 **「演示清单」** 提供摘要，完整矩阵见 [demo/DEMO_MATRIX.md](demo/DEMO_MATRIX.md)。建议在 **JDK 17** 下执行 `./gradlew :aw-net:demo:assembleRelease` 做冒烟。

---

## 响应模型：BaseResponse vs GlobalResponse

aw-net 使用 `BaseResponse<T>` 接口定义统一响应结构：

```kotlin
interface BaseResponse<T> {
    val code: Int        // 业务状态码
    val msg: String      // 业务消息
    val data: T?         // 业务数据
}
```

| 类型 | 说明 |
|------|------|
| `GlobalResponse<T>` | 默认实现，使用标准 `code` / `msg` / `data` 字段名。大多数接口直接用此类。 |
| 自定义实现 | 若后端字段名不同（如 `status` / `message` / `result`），通过 `NetworkConfig.responseFieldMapping` 配置映射，或自行实现 `BaseResponse<T>`。 |

## 特性

### HTTP
- 统一结果包装 `NetworkResult<T>`（Success / TechnicalFailure / BusinessFailure）；链式与 `fold` / `recoverWith` 等见 `NetworkResultExt.kt`
- 开箱即用：只需配置 `baseUrl` 即可发起请求
- 文件下载：进度回调、SHA-256 Hash 校验
- 文件上传：单文件/多文件/Multipart，进度回调
- Token 鉴权：`TokenRefreshCoordinator` 统一管理 HTTP 401 和业务 401 刷新，并发安全
- 重试机制：指数退避 + 随机抖动（Jitter）、可配置策略
- 动态配置：运行时切换 BaseUrl（`@BaseUrl`）、按接口超时（`@Timeout`）、按接口重试（`@Retry`）
- 响应映射：自定义 code/msg/data 字段名，兼容不同后端
- 日志格式化：敏感信息脱敏（Header + Body 字段级）、JSON 美化
- 请求监控：`NetEvent` 事件追踪（耗时、成功率）
- 轮询工具：`pollingFlow()` 周期请求
- 请求去重：`RequestDedup` 相同请求合并
- 请求节流：`RequestThrottle` 限制请求间隔
- 按标签批量取消请求：`RequestCanceller`
- Cookie 持久化：`PersistentCookieJar`
- 开发/测试阶段 Mock：`MockInterceptor`
- 连接池优化：可配置空闲连接数和存活时间
- SSL 证书固定：防止中间人攻击
- 网络状态监听：实时监控网络连接和类型变化

### WebSocket
- 多连接管理：同时维护多个 WebSocket 连接
- 心跳检测：可配置间隔和超时
- 断线重连：指数退避 + 随机抖动，基于协程调度
- 离线消息队列：断开期间消息自动缓存，二进制消息安全拷贝
- 状态管理：`AtomicReference<WsState>` 原子化状态，消除竞态条件
- 响应式状态流：`connectionStateFlow` 可在 ViewModel 中监听

## 协程与线程约束

| API | 线程约束 | 说明 |
|-----|---------|------|
| `NetworkExecutor.executeRequest()` | 任意线程 | 内部协程安全 |
| `NetworkExecutor.executeRequestFlow()` | 任意线程 | 返回 Cold Flow |
| `NetworkConfigProvider.updateConfig()` | 任意线程 | 使用 AtomicReference 保证线程安全 |
| `WebSocketManager.connect/sendMessage/disconnect()` | 任意线程 | 内部同步安全 |
| `WebSocketListener` 回调 | 主线程 | 默认在主线程回调，可通过 `Config.callbackOnMainThread` 修改 |
| `NetworkMonitor.isConnected` | 主线程 | Android ConnectivityManager 要求 |

---

## Advanced

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
            override fun onUnauthorized() { /* Navigate to login */ }
        }
    }
}
```

> Token 刷新机制说明：aw-net 通过 `TokenRefreshCoordinator` 统一管理 Token 刷新：
> - **HTTP 401**：由 OkHttp 的 `TokenAuthenticator` 委托给 `TokenRefreshCoordinator` 刷新
> - **业务 code=401**：由 `RequestExecutor` 委托给 `TokenRefreshCoordinator` 刷新
> - 两种场景共享同一把锁，确保并发安全，不会重复刷新
> - 刷新失败均触发 `UnauthorizedHandler.onUnauthorized()`

### 自定义日志 NetLogger

通过 Hilt 可选绑定注入自定义日志实现：

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

### 按接口成功码

不同后端接口的成功码可能不同。支持三级优先级：**显式参数 > @SuccessCode 注解 > 全局配置**。

```kotlin
// 方式一：@SuccessCode 注解
interface MultiCodeApi {
    @SuccessCode(200)
    @GET("legacy-api")
    suspend fun legacyApi(): GlobalResponse<Data>

    @SuccessCode(0)
    @GET("new-api")
    suspend fun newApi(): GlobalResponse<Data>
}

// 方式二：executeRequest 显式参数（最高优先级）
executor.executeRequest(successCode = 200) { api.getUser() }
```

### 使用 Builder 模式配置 NetworkConfig

```kotlin
NetworkConfig.builder("https://api.example.com/").apply {
    connectTimeout = 15L
    readTimeout = 15L
    networkLogLevel = NetworkLogLevel.BODY
    extraHeaders = mapOf("X-App-Version" to "1.0.0")
}.build()
```

### 完整 NetworkConfig

```kotlin
NetworkConfig(
    baseUrl = "https://api.example.com/",
    connectTimeout = 15L,       // 1~300 秒
    readTimeout = 15L,          // 1~300 秒
    writeTimeout = 15L,         // 1~300 秒
    defaultSuccessCode = 0,
    networkLogLevel = NetworkLogLevel.BODY,
    extraHeaders = mapOf("X-App-Version" to "1.0.0"),
    cacheDir = cacheDir,        // 需与 cacheSize 同时提供
    cacheSize = 10_000_000L,    // 需与 cacheDir 同时提供
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

### 使用 RequestOption 简化参数

```kotlin
val result = executor.executeRequest(
    option = RequestOption(
        retryOnFailure = 3,
        tag = "getUserInfo",
        retryDelayMs = 500L
    )
) { api.getUser(1) }
```

亦可用 `requestOption { }` 构建相同配置：

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

### 使用 Flow 版本 API

`executeRequestFlow` / `requestResultFlow`（行为相同）是**单结果冷流**，启动收集后**仅发射一次** `NetworkResult`；与连续多事件的 `Channel` / `SharedFlow` 不同。

```kotlin
val userState = executor.requestResultFlow { api.getUser(1) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

### 自定义 Retrofit / Converter（Moshi 等）

库默认在 `NetworkModule` 中提供 `NetworkClientFactory`（Gson + `GlobalResponseTypeAdapterFactory`）。若需其它反序列化栈，在应用中 **@Provides @Singleton** 同类型**覆盖** Hilt 绑定即可，例如使用 Moshi（需自行添加 `retrofit2-converter-moshi` 依赖）：

```kotlin
// 依赖中需有：retrofit2:converter-moshi、moshi-kotlin
// import com.squareup.moshi.Moshi
// import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
// import retrofit2.converter.moshi.MoshiConverterFactory
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

> 使用非 Gson 时，需保证接口返回类型与 `BaseResponse` / 业务 DTO 在所用 Converter 下可反序列化；`GlobalResponseTypeAdapterFactory` 仅随 Gson 路径注册。

### 网络状态监听

```kotlin
@Inject lateinit var networkMonitor: NetworkMonitor

lifecycleScope.launch {
    networkMonitor.isConnected.collect { connected ->
        if (connected) {
            val type = networkMonitor.networkType.value
            // WIFI / CELLULAR / ETHERNET / NONE
        }
    }
}
```

### 请求监控（NetTracker）

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object TrackerModule {
    @Provides @Singleton
    fun provideNetTracker(): NetTracker {
        return object : NetTracker {
            override fun onEvent(event: NetEvent) {
                // NetEvent: name, stage, timestampMs, durationMs(仅 END), resultType, errorCode, tag
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

在 `NetTracker` 实现里对 **END** 事件按耗时过滤的示例（替代或补充 `slowRequestThresholdMs` 的自有上报）：

```kotlin
import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage

override fun onEvent(event: NetEvent) {
    if (event.stage != NetEventStage.END) return
    val ms = event.durationMs ?: return
    if (ms > 3_000) {
        // 上报慢请求：结合 event.name、event.tag、event.resultType
    }
}
```

### HTTP 响应缓存（可选）

若已在 `NetworkConfig` 同时提供 **cacheDir** 与 **cacheSize**，`NetworkModule` 会为 `OkHttpClient` 装配 `Cache`（磁盘缓存，受响应 `Cache-Control` 影响）。与「仅 Gson 解析业务体」正交：适合 GET 类可缓存接口；POST 默认仍走网络。需服务端返回合理的缓存头；仅客户端开 cache 无法违背服务端 `no-store`。

### 运行时配置变更

`NetworkConfig` 在 **Hilt 单例 `NetworkConfigProvider`** 中通过 `update { }` / `updateConfig()` 替换。下表中的「**当前生效**」指：新请求在拦截器/执行器里读取 `configProvider.current` 时能否看到最新值，**不**表示「不重建就改变 `OkHttpClient` 在构造时已经写死的项」。

| 字段 / 能力 | 当前生效 | 说明 |
|------|:---:|------|
| baseUrl | ✅ | `DynamicBaseUrlInterceptor` 按请求与运行时 base 对齐（接口上另有 `@BaseUrl`） |
| networkLogLevel | ✅ | `DynamicLoggingInterceptor` 按当前级别记录 |
| extraHeaders、defaultSuccessCode、responseFieldMapping | ✅ | 各拦截器与 Gson 工厂按请求读取 `current` |
| sensitiveHeaders / sensitiveBodyFields | ✅ | `PrettyNetLogger` 等按当前值脱敏 |
| enableRequestTracking | ✅ | 为 `false` 时库内 `RequestExecutor` / 上传与下载不再发 `NetEvent` 起止（不影响 `NetworkResult` 本身） |
| slowRequestThresholdMs | ✅ | 非 null 且 >0 时，`TrackableExecutor` 对单次调用耗时超过阈值打 Logcat 警告；与 `enableRequestTracking` 独立（关闭埋点仍可记录慢请求日志） |
| 在接口上标注 `@Timeout` 的方法 | ✅ | `DynamicTimeoutInterceptor` **只对该次请求**在 `Chain` 上改连接/读/写超时，与 `NetworkConfig` 里是否改过**全局**秒数无关 |
| 全局的 connectTimeout / readTimeout / writeTimeout | ❌ | 在创建 **单例** `OkHttpClient` 时从配置读入；仅对 `update { copy(...) }` 改全局秒数，**不能**重配已存在的 Client；需改可依赖单接口的 `@Timeout` 或自管重建 Client |
| maxIdleConnections、keepAliveDurationSeconds、cache、证书钉、cookieJar | ❌ | 在构建 `OkHttpClient` 时生效，之后不随 `update` 而变 |
| `tokenRefreshLockAcquireTimeoutMs` | ⚠️ | 在 **Hilt 首次**创建 `TokenRefreshCoordinator` 时从配置读入；仅 `update` **不**会更新已创建实例（以类 KDoc 为准） |

```kotlin
@Inject lateinit var configProvider: NetworkConfigProvider

// 运行时切换环境
configProvider.updateConfig(
    configProvider.current.copy(baseUrl = "https://staging.api.example.com/")
)
```

### 请求取消 RequestCanceller

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

### Cookie 持久化 PersistentCookieJar

```kotlin
val cookieJar = PersistentCookieJar(File(cacheDir, "network_cookies"))

// 方式一：通过 NetworkConfig 配置
NetworkConfig.builder("https://api.example.com/")
    .cookieJar(cookieJar)
    .build()

// 方式二：清除指定域名的 Cookie
cookieJar.clearForDomain("api.example.com")

// 方式三：清除所有 Cookie
cookieJar.clear()
```

### Mock 拦截器 MockInterceptor

```kotlin
val mockInterceptor = MockInterceptor(enable = BuildConfig.DEBUG).apply {
    mock("/users", """{"code":0,"msg":"success","data":[{"id":1,"name":"Alice"}]}""")
    mock("/login", 401, """{"code":401,"msg":"unauthorized","data":null}""")
    mock("/slow-api", 200, """{"code":0,"msg":"ok"}""", delayMs = 1000)
    mock("/items/*", """{"code":0,"msg":"ok","data":[]}""") // 通配符匹配
}
```

---

## WebSocket

### 基本使用

```kotlin
@Inject lateinit var wsManager: WebSocketManager

wsManager.connectDefault(
    url = "wss://echo.example.com",
    listener = object : WebSocketManager.WebSocketListener {
        override fun onOpen(connectionId: String) { /* … */ }
        override fun onMessage(connectionId: String, text: String) { /* … */ }
        override fun onMessage(connectionId: String, bytes: ByteArray) { /* … */ }
        override fun onClosing(connectionId: String, code: Int, reason: String) { /* … */ }
        override fun onClosed(connectionId: String, code: Int, reason: String) { /* … */ }
        override fun onFailure(connectionId: String, t: Throwable) { /* … */ }
        override fun onHeartbeatTimeout(connectionId: String) { /* … */ }
        override fun onStateChanged(
            connectionId: String, oldState: WebSocketManager.State, newState: WebSocketManager.State
        ) { /* … */ }
        override fun onReconnecting(connectionId: String, attempt: Int) { /* … */ }
    }
)
wsManager.sendText("Hello!")
wsManager.disconnectDefault()
// 页面或 ViewModel 销毁时另建议 wsManager.disconnectAll() 或 wsManager.close()（java.lang.AutoCloseable）
```

> 上例为**可编译**的最小骨架；`WebSocket` 与 HTTP `Authenticator` 的鉴权**不**自动打通，建连前请自管 Token / URL 参数。完整回调见下「`WebSocketListener` 完整回调」表。

### Config 字段说明

| 字段 | 默认值 | 说明 |
|------|--------|------|
| heartbeatIntervalMs | 30000 | 心跳间隔（毫秒），0 表示不发送心跳 |
| heartbeatTimeoutMs | 60000 | 心跳超时（毫秒），超时后触发断线重连 |
| heartbeatMessage | "ping" | 心跳消息内容 |
| enableHeartbeat | true | 是否启用心跳检测 |
| wsLogLevel | BASIC | 日志级别（NONE / BASIC / FULL） |
| callbackOnMainThread | true | 回调是否在主线程执行 |
| messageQueueCapacity | 100 | 离线消息队列容量 |
| dropOldestWhenQueueFull | false | 队列满时是否丢弃最旧消息 |
| enableMessageReplay | true | 是否启用离线消息补发 |
| connectTimeout | 10 | 连接超时（秒） |
| readTimeout | 0 | 读超时（秒），0=无限 |
| writeTimeout | 10 | 写超时（秒） |
| maxReconnectAttempts | 0 | 最大重连次数，0=无限 |
| reconnectBaseDelayMs | 1000 | 重连基础延迟（毫秒） |
| reconnectMaxDelayMs | 30000 | 重连最大延迟（毫秒） |

### 多连接管理

```kotlin
// 建立多个连接
wsManager.connect("chat", "wss://chat.example.com", listener = chatListener)
wsManager.connect("notification", "wss://notify.example.com", listener = notifyListener)

// 分别发送消息
wsManager.sendMessage("chat", "Hello!")
wsManager.sendMessage("notification", "{\"type\":\"subscribe\"}")

// 断开指定连接
wsManager.disconnect("chat")
```

### 状态监听（StateFlow）

```kotlin
lifecycleScope.launch {
    wsManager.connectionStateFlow.collect { stateMap ->
        val chatState = stateMap["chat"]
        val notifyState = stateMap["notification"]
        updateUI(chatState, notifyState)
    }
}
```

### WebSocketListener 完整回调

| 回调 | 触发时机 |
|------|---------|
| `onOpen` | 连接已建立 |
| `onMessage(text)` | 收到文本消息 |
| `onMessage(bytes)` | 收到二进制消息 |
| `onClosing` | 连接正在关闭（收到 close frame，尚未回复） |
| `onClosed` | 连接已关闭 |
| `onFailure` | 连接发生错误 |
| `onHeartbeatTimeout` | 心跳超时 |
| `onStateChanged` | 连接状态变化 |
| `onReconnecting` | 正在重连 |

---

## NetworkResult 处理

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

> **注意**：`NetworkResult.Success.data` 可能为 null，表示业务成功但无返回数据（如删除/更新操作）。使用 `onSuccessNotNull` 扩展可安全处理非 null 数据。

## 错误码

| 常量 | 值 | 说明 |
|------|-----|------|
| NetCode.Business.SUCCESS | 0 | 业务成功 |
| NetCode.Business.UNAUTHORIZED | 401 | 业务层未授权（非 HTTP 401） |
| NetCode.Business.FORBIDDEN | 403 | 禁止访问 |
| NetCode.Business.NOT_FOUND | 404 | 资源不存在 |
| NetCode.Technical.TIMEOUT | -1 | 超时 |
| NetCode.Technical.NO_NETWORK | -2 | 无网络 |
| NetCode.Technical.SSL_ERROR | -3 | SSL 错误 |
| NetCode.Technical.REQUEST_CANCELED | -999 | 请求取消 |
| NetCode.Technical.UNKNOWN | -1000 | 未知错误 |
| NetCode.Technical.PARSE_ERROR | -1001 | JSON 解析失败 |

> **注意**：`NetCode.Business` 中的常量值与 HTTP 状态码相同，但语义不同。HTTP 层的 401 和业务层的 401 均由 `TokenRefreshCoordinator` 统一处理。

## 混淆配置

aw-net 已在 `consumer-rules.pro` 中内置了必要的混淆规则，**大多数项目无需额外配置**。

如果你的项目使用了自定义 ProGuard 规则，请确保不要移除以下 keep 规则：

```proguard
-keep class com.answufeng.net.http.config.** { *; }
-keep class com.answufeng.net.http.annotations.** { *; }
-keep class com.answufeng.net.http.model.** { *; }
-keep interface com.answufeng.net.websocket.WebSocketManager { *; }
```

如果你的 **业务模型类** 用于 Retrofit 接口返回值，需要自行保留：

```proguard
-keep class com.yourapp.model.** { *; }
```

在 release 构建后，检查 `app/build/outputs/mapping/release/mapping.txt` 确认关键类未被混淆。

## 依赖说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| OkHttp | 4.12.0 | HTTP/WebSocket 传输层 |
| Retrofit | 2.11.0 | 类型安全 HTTP 接口 |
| Gson | (传递依赖) | JSON 序列化 |
| Hilt | 2.52 | 依赖注入 |
| Coroutines | 1.9.0 | 协程异步支持 |

## 常见问题

**Q: 如何处理并发请求？**
A: `NetworkExecutor` 可在多个协程中并发调用。Token 刷新由 `TokenRefreshCoordinator` 用**可重入锁**串行化（OkHttp `Authenticator` 与协程侧 `RequestExecutor` 共用），并带**持锁超时**；业务未授权码与 HTTP 401 两条路径请见上文「Token 鉴权」与源码中 `TokenAuthenticator` / `RequestExecutor` 的说明。

**Q: 如何实现离线优先？**
A: 配置 `cacheDir` 和 `cacheSize` 启用 OkHttp HTTP 缓存，配合 `CacheControl` 头实现离线缓存策略。

**Q: 如何取消请求？**
A: 直接取消发起请求的协程即可（`job.cancel()`），`kotlinx.coroutines.CancellationException` 会被正确重新抛出，不会误入错误处理流程。

**Q: 如何自定义 OkHttp 配置？**
A: 通过 `@AppInterceptor` 注解提供自定义拦截器，或通过 `OptionalBindingsModule` 替换 `OkHttpClient` 提供。

**Q: 如何配置日志级别？**
A: 使用 `NetworkConfig.networkLogLevel`，支持 `NONE`、`BASIC`、`HEADERS`、`BODY` 四个级别。WebSocket 日志使用 `Config.wsLogLevel`，支持 `NONE`、`BASIC`、`FULL`。

**Q: 为什么库代码里出现 `java.util.Optional`？**
A: 这是 Dagger / Hilt 对「可选绑定」的惯用写法。在 Kotlin 应用层仍通过 Hilt 模块 `@BindsOptionalOf` + 按需 `@Provides` 即可，与 Java 8 `Optional` 的语义一致：未提供实现时 `Optional` 为空。

## 许可证

Apache License 2.0，详见 [LICENSE](LICENSE)。

<!-- 文档变更请同步更新 CHANGELOG「Documentation」等条目 -->
