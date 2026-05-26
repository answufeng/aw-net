# aw-net

[![JitPack](https://jitpack.io/v/answufeng/aw-net.svg)](https://jitpack.io/#answufeng/aw-net)

基于 **OkHttp + Retrofit + 协程 + Hilt** 的 Android 网络库：HTTP 请求（自动解包业务响应、重试、Token）、上传下载（进度、断点续传）、WebSocket（自动重连、心跳、离线补发）。

| | |
|:--|:--|
| **版本** | `1.1.0`（[JitPack](https://jitpack.io/#answufeng/aw-net)） |
| **minSdk** | 24 |
| **示例** | demo 模块 |

---

## 目录

| 需求 | 章节 |
|------|------|
| 最快接入 | [快速上手](#快速上手) |
| ViewModel | [MVVM](#mvvm) · [MVI](#mvi) |
| 无 Hilt | [非 Hilt](#非-hilt) |
| 响应格式 / 字段名 | [业务响应格式](#业务响应格式) |
| 第三方接口 | [第三方接口](#第三方接口) |
| 请求参数 | [请求配置](#请求配置) |
| 进阶工具 | [进阶工具](#进阶工具) |
| 结果处理 | [NetworkResult](#networkresult) |
| Token | [Token 与鉴权](#token-与鉴权) |
| 上传下载 | [上传与下载](#上传与下载) |
| WebSocket | [WebSocket](#websocket) |
| 网络状态 | [网络状态](#网络状态) |
| 注解 | [注解](#注解) |
| 混淆 | [ProGuard](#proguard) |

---

## 快速上手

### 1. 依赖

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.answufeng:aw-net:1.1.0")
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
}
```

### 2. 配置

```kotlin
@HiltAndroidApp
class App : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {
    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig =
        NetworkConfig.builder("https://api.example.com/")
            .networkLogLevel = NetworkLogLevel.BODY
            .build()
}
```

### 3. API 与请求

```kotlin
interface UserApi {
    @GET("user/getUser")
    suspend fun getUser(): User?

    @GET("user/list")
    suspend fun getUsers(): List<User>
}

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)
}

@AndroidEntryPoint
class UserActivity : AppCompatActivity() {
    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var api: UserApi

    lifecycleScope.launch {
        val result = executor.execute { api.getUser() }
        result.onSuccess { user -> render(user) }
            .onTechnicalFailure { ex -> showError(ex.message) }
            .onBusinessFailure { code, msg -> showError("$code $msg") }
    }
}
```

后端 JSON 为 `{code, msg, data}` 时，Retrofit **直接声明业务类型**（`User?`、`List<User>`、`String?` 等），由库在解析阶段自动解包 `data`；`executor.execute` 的 `onSuccess` 收到的就是该类型。

---

## MVVM

推荐 ViewModel 依赖 **Repository**，由 Repository 调用 `executor.execute`：

```kotlin
@Singleton
class UserRepository @Inject constructor(
    private val executor: NetworkExecutor,
    private val api: UserApi,
) {
    suspend fun loadUsers(): NetworkResult<List<User>> =
        executor.execute { api.getUsers() }
}

@HiltViewModel
class UserViewModel @Inject constructor(
    private val repository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun loadUsers() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.loadUsers()
            _uiState.value =
                when (result) {
                    is NetworkResult.Success ->
                        UiState.Success(result.data ?: emptyList())
                    is NetworkResult.TechnicalFailure ->
                        UiState.Error(result.exception.message ?: "网络错误")
                    is NetworkResult.BusinessFailure ->
                        UiState.Error("${result.code}: ${result.msg}")
                }
        }
    }
}
```

---

## MVI

与 MVVM 相同，网络请求仍放在 **Repository**；区别是 View 只发 **Intent**，ViewModel 用单一 **State** 描述界面。

```kotlin
// Intent：用户意图
sealed interface PostIntent {
    data object Load : PostIntent
    data object Refresh : PostIntent
    data object Retry : PostIntent
}

// State：界面快照（单一数据源）
data class PostUiState(
    val isLoading: Boolean = false,
    val posts: List<Post> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class PostMviViewModel @Inject constructor(
    private val repository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PostUiState())
    val state: StateFlow<PostUiState> = _state.asStateFlow()

    fun dispatch(intent: PostIntent) {
        when (intent) {
            PostIntent.Load, PostIntent.Refresh, PostIntent.Retry -> loadPosts()
        }
    }

    private fun loadPosts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.loadPosts()) {
                is NetworkResult.Success ->
                    _state.update {
                        it.copy(isLoading = false, posts = result.data ?: emptyList())
                    }
                is NetworkResult.TechnicalFailure ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.exception.message)
                    }
                is NetworkResult.BusinessFailure ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "${result.code}: ${result.msg}")
                    }
            }
        }
    }
}
```

```kotlin
// Activity / Composable
lifecycleScope.launch {
    viewModel.state.collect { state -> render(state) }
}
viewModel.dispatch(PostIntent.Load)
```

完整可运行示例见 demo 模块 **「MVI 架构」**（`demo/mvi/`、`MviDemoActivity`）。

---

## 非 Hilt

```kotlin
val executor = AwNet.createExecutor(
    config = NetworkConfig.builder("https://api.example.com/").build(),
    networkMonitor = myNetworkMonitor,
    tokenProvider = myTokenProvider,           // 可选
    unauthorizedHandler = myUnauthorizedHandler, // 可选
)

val api = executor.createApi<UserApi>()
val wsManager = AwNet.createWebSocketManager()
```

---

## 业务响应格式

### 默认 `{code, msg, data}`

零配置即可，接口返回类型写 `T` 或 `T?`：

```kotlin
@GET("user/getUser")
suspend fun getUser(): User?
```

### 全局自定义字段名

例如 `{status, message, data}`：

```kotlin
NetworkConfig.builder("https://api.example.com/")
    .responseFields(code = "status", msg = "message", data = "data", successCode = 200)
    .build()
```

`status` 为 **boolean** 时（`true` 表示成功）：

```kotlin
NetworkConfig.builder("https://api.example.com/")
    .responseFields(code = "status", msg = "message", data = "data", successCode = 0)
    .build()
```

或使用预设：

```kotlin
.responseFieldMapping = ResponseFieldMapping.statusMessageData(successCode = 200)
// boolean status：ResponseFieldMapping.booleanStatusMessageData()
```

### 单接口覆盖字段名

```kotlin
@ResponseFields(codeKey = "errCode", msgKey = "errMsg", dataKey = "payload", successCode = 0)
@GET("legacy/user")
suspend fun legacyUser(): User?
```

### `data` 类型说明

| 声明 | 说明 |
|------|------|
| `User?` | `data` 为对象或 `null` |
| `String?` / `Int?` | 标量 |
| `List<User>` | 数组 |
| 内嵌 JSON 字符串 | 对象类型会自动二次解析（`parseEmbeddedJsonStringData`，默认开启） |

---

## 请求配置

统一使用 **`executor.execute`**，通过 `RequestOption` 控制重试、超时、Header 等：

```kotlin
val result = executor.execute(
    option = RequestOption(
        tag = "getUser",
        successCode = 200,          // 覆盖全局 defaultSuccessCode
        retryOnFailure = 2,
        retryDelayMs = 500,
        retryOnTechnical = true,
        retryOnBusiness = false,
        totalTimeoutMs = 10_000,
        extraHeaders = mapOf("X-Custom" to "value"),
    ),
) { api.getUser() }
```

| 参数 | 默认 | 说明 |
|------|------|------|
| `successCode` | `null` | 业务成功码，null 用全局配置 |
| `dispatcher` | `Dispatchers.IO` | 协程调度器 |
| `tag` | `null` | 监控标签 |
| `retryOnFailure` | `0` | 协程重试次数（不含首次） |
| `retryDelayMs` | `300` | 重试间隔 |
| `retryOnTechnical` | `true` | 技术失败是否重试 |
| `retryOnBusiness` | `false` | 业务失败是否重试 |
| `totalTimeoutMs` | `null` | 含重试的整体超时 |
| `extraHeaders` | 空 | 请求级 Header |
| `disableOkHttpRetry` | `false` | 禁用 OkHttp 层重试 |

### 重试

- **推荐**：`RequestOption.retryOnFailure`（协程层）
- **可选**：`NetworkConfig.enableRetryInterceptor = true`（OkHttp 层）

**不要同时开启**，否则退避叠加。设置 `retryOnFailure > 0` 时会自动避免 OkHttp 重试叠加。

### 动态 BaseUrl

全局：

```kotlin
configProvider.update { it.copy(baseUrl = "https://staging.example.com/") }
```

单接口：

```kotlin
@BaseUrl("https://cdn.example.com/")
@GET("avatar.png")
suspend fun avatar(): ResponseBody
```

### 运行时配置说明

通过 `NetworkConfigProvider.update { it.copy(...) }` **立即生效**的字段包括：`baseUrl`、`networkLogLevel`、`extraHeaders`、`defaultSuccessCode`、`responseFieldMapping`、`requireValidatedNetwork` 等。

**仅构建 OkHttp/Retrofit 时读取一次**的字段包括：`connectTimeout` / `readTimeout` / `writeTimeout`、`certificatePins`、`cacheDir` / `cacheSize`、`enableRetryInterceptor` 等；修改后需自行重建 Client。

---

## 第三方接口

第三方 API 常见特征：**域名与主业务不同**、响应为**裸 JSON**（无 `{code,msg,data}`）。

```kotlin
interface GitHubApi {
    @RawResponse
    @BaseUrl("https://api.github.com/")
    @GET("users/{login}")
    suspend fun getUser(@Path("login") login: String): GitHubUser
}
```

```kotlin
val result = executor.execute(
    option = RequestOption(
        extraHeaders = mapOf("Accept" to "application/vnd.github+json"),
    ),
) { githubApi.getUser("octocat") }
```

| 要点 | 说明 |
|------|------|
| `@RawResponse` | 跳过业务包装解包，Gson 直接解析为 `T` |
| `@BaseUrl` | 切换本次请求的 host；**不要**只写 `@GET("https://第三方/...")` 而不加 `@BaseUrl`（会被动态 BaseUrl 拦截器改写到主域名） |
| `execute` | 与自有接口相同；失败多为 `TechnicalFailure`（HTTP/网络/解析），一般无 `BusinessFailure` |
| 鉴权 Header | 全局 `AuthHeaderInterceptor` 会给所有请求加 Token；第三方专用 Key 用 `RequestOption.extraHeaders` |

可与主业务 API 共用一个 `Retrofit` / `NetworkExecutor`，按方法区分注解即可。

---

## NetworkResult

```kotlin
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T?) : NetworkResult<T>()
    data class TechnicalFailure(val exception: BaseNetException) : NetworkResult<Nothing>()
    data class BusinessFailure(val code: Int, val msg: String) : NetworkResult<Nothing>()
}
```

```kotlin
// 链式
result.onSuccess { }.onTechnicalFailure { }.onBusinessFailure { code, msg -> }

// fold
result.fold(onSuccess = { }, onTechnicalFailure = { }, onBusinessFailure = { code, msg -> })

// when
when (result) { is NetworkResult.Success -> ... }
```

离线时返回 `TechnicalFailure`，不会抛未捕获异常。

---

## Token 与鉴权

提供 `TokenProvider` 后：

1. 自动添加 `Authorization: Bearer <token>`
2. **HTTP 401**：由 OkHttp `TokenAuthenticator` 刷新并重试
3. **业务 JSON 内 `code == 401`**（含 `execute { suspend fun(): T? }` 与 `executeRequest`）：协程层刷新 Token 后**重试一次**

```kotlin
@Provides @Singleton
fun provideTokenProvider(): TokenProvider = object : TokenProvider {
    override fun getAccessToken(): String? = store.token
    override fun refreshTokenBlocking(): Boolean = store.refresh()
}

@Provides @Singleton
fun provideUnauthorizedHandler(): UnauthorizedHandler = UnauthorizedHandler {
    // 跳转登录
}
```

```kotlin
interface TokenProvider {
    fun getAccessToken(): String?
    fun refreshTokenBlocking(): Boolean
    suspend fun refreshTokenSuspend(): Boolean = refreshTokenBlocking()
    fun clear() {}
}
```

---

## 上传与下载

### 下载

```kotlin
val progress = NetworkExecutor.createDefaultProgressFlow()

val result = executor.downloadFile(
    targetFile = file,
    progressFlow = progress,
    expectedHash = sha256,
) { api.download() }
```

### 断点续传

调用方在接口中自行加 `Range`：

```kotlin
executor.downloadFileResumable(
    targetFile = file,
    existingFileSize = file.length(),
    progressFlow = progress,
) {
    api.downloadWithRange("bytes=${file.length()}-")
}
```

### 上传

```kotlin
val part = executor.createProgressPart("file", uploadFile, progressFlow)
val result = executor.execute { api.uploadFile(part) }
```

上传接口与 HTTP 一致：Retrofit 直接声明业务类型 `T?`，使用 `executor.execute` 调用。

---

## WebSocket

```kotlin
@Inject lateinit var wsManager: WebSocketManager

wsManager.connect(
    connectionId = "chat",
    url = "wss://example.com/socket",
    config = WebSocketManager.Config(
        headers = mapOf("Authorization" to "Bearer $token"),
        heartbeatMessage = "ping",
        maxReconnectAttempts = 5,
    ),
    listener = myListener,
)

wsManager.sendMessage("chat", "Hello")
wsManager.disconnect("chat")
```

单连接快捷 API：`connectDefault` / `sendText` / `disconnectDefault`。

`connectionStateFlow` 状态：`CONNECTING`、`CONNECTED`、`RECONNECTING`、`DISCONNECTED`、`ERROR`。

WebSocket **不会**自动刷新 HTTP Token，建连前请确保 Header 中 Token 有效。

---

## 网络状态

```kotlin
@Inject lateinit var networkMonitor: NetworkMonitor

networkMonitor.isOnline()

lifecycleScope.launch {
    networkMonitor.isConnected.collect { online -> }
    networkMonitor.networkType.collect { type -> }
}
```

减少 captive portal 误判（要求系统判定网络已 VALIDATED）：

```kotlin
NetworkConfig.builder(baseUrl)
    .apply { requireValidatedNetwork = true }
    .build()
```

---

## 注解

| 注解 | 作用 |
|------|------|
| `@RawResponse` | 裸 JSON，跳过 `{code,msg,data}` 解包 |
| `@ResponseFields(...)` | 单接口字段名 / 成功码 |
| `@SuccessCode(200)` | 单接口业务成功码（配合 `suspend fun (): T?`） |
| `@BaseUrl("https://...")` | 单接口 BaseUrl |
| `@Timeout(connect, read, write)` | 单接口超时（秒） |
| `@Retry(maxAttempts, ...)` | OkHttp 层单接口重试 |

```kotlin
interface Api {
    @SuccessCode(200)
    @GET("user/profile")
    suspend fun profile(): User?

    @RawResponse
    @BaseUrl("https://api.third-party.com/")
    @GET("v1/posts")
    suspend fun thirdPartyPosts(): List<Post>
}
```

---

## 进阶工具

库内提供可选工具类，配合 `executor.execute` 使用（Demo 见 `AdvancedActivity`）。

### 请求去重 `RequestDedup`

相同 key 的并发请求合并为一次，结果共享：

```kotlin
val dedup = RequestDedup()
val result = dedup.dedupRequest("user_123") {
    executor.execute { api.getUser() }
}
```

### 节流 `RequestThrottle`

限制同一 key 的请求频率：

```kotlin
val throttle = RequestThrottle(intervalMs = 500)
val result = throttle.throttleRequest("search") {
    executor.execute { api.search(keyword) }
}
```

### 轮询 `pollingFlow`

```kotlin
pollingFlow(periodMillis = 3_000, maxAttempts = 10, stopWhen = { it is NetworkResult.Success }) {
    executor.execute { api.pollStatus() }
}.collect { result -> /* 处理每次结果 */ }
```

### 遗留 API（不推荐新项目使用）

| API | 替代 |
|-----|------|
| `executeRawRequest` | `execute`（裸 JSON 用方法上的 `@RawResponse`） |
| `executeDataRequest` / `executeRequest` + `GlobalResponse` | `execute` + `suspend fun (): T?` |
| `requestResultFlow` / `rawRequestResultFlow` | `flow { emit(executor.execute { ... }) }` |
| `NetworkConfig.toBuilder()` | `NetworkConfig.builder(url)` 或 `copy()` |

---

## ProGuard

AAR 已附带 consumer 规则（公共 API、注解、Gson/Retrofit 元数据）。使用 Gson 的模型类若未加 `@SerializedName`，请在 App 的 ProGuard 规则中 keep 对应 DTO。

---

## 传递依赖版本

本库以 `api` 暴露 OkHttp、Retrofit、协程，一般无需重复声明。冲突时请与下表对齐：

| 组件 | 版本 |
|------|------|
| OkHttp | 4.12.0 |
| Retrofit | 2.12.0 |
| kotlinx-coroutines | 1.10.2 |
| Hilt | 2.56.2 |

---

## 本地构建

```powershell
./gradlew.bat :aw-net:compileDebugKotlin :demo:assembleDebug
```
