# aw-net

[![JitPack](https://jitpack.io/v/answufeng/aw-net.svg)](https://jitpack.io/#answufeng/aw-net)

基于 **OkHttp + Retrofit + 协程 + Hilt** 的 Android 网络库：提供 HTTP 请求执行（重试、Token 刷新、统一结果处理）、上传下载（进度回调、断点续传）、WebSocket 管理（自动重连、心跳、离线补发）等能力。

如果你只想最快接入并跑通第一个请求，直接看下面的「5 分钟上手」即可；其它内容都可以后置按需查阅。

| | |
|:--|:--|
| **当前版本** | `1.0.4`（[Git 标签](https://github.com/answufeng/aw-net/tags) / JitPack 同名） |
| **范围** | minSdk **24**；本仓库用 compileSdk 35、**JDK 17** 跑 CI / demo |
| **示例** | 见 demo 模块各 Activity |

---

## 5 分钟上手（最小接入）

### 1) 添加依赖（JitPack）

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
    implementation("com.github.answufeng:aw-net:1.0.4")

    // 仅在使用 Hilt 集成时需要
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
}
```

### 2) 初始化 Hilt + 网络配置

```kotlin
@HiltAndroidApp
class App : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {
    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig {
        return NetworkConfig.builder("https://api.example.com/")
            .networkLogLevel = NetworkLogLevel.BODY
            .build()
    }
}
```

### 3) 发起第一个请求

```kotlin
@AndroidEntryPoint
class UserActivity : AppCompatActivity() {
    @Inject lateinit var executor: NetworkExecutor
    @Inject lateinit var api: UserApi

    lifecycleScope.launch {
        val result = executor.executeRawRequest { api.getUsers() }
        result.onSuccess { users -> render(users) }
            .onTechnicalFailure { ex -> showError(ex.message) }
            .onBusinessFailure { code, msg -> showError("$code $msg") }
    }
}
```

<details>
<summary><b>依赖与传递版本说明（点击展开）</b></summary>

- 本库对 OkHttp / Retrofit / kotlinx-coroutines 使用 `api`，多数项目**不必**再写 `implementation(okhttp)` 等。
- 多模块冲突时请在宿主**统一** `okhttp` / `retrofit` / `kotlinx-coroutines` 版本。
- **Release** 请在混淆包上点一遍网络请求；AAR 已含 consumer 规则。

| 组件 | 版本 |
|------|------|
| OkHttp | 4.12.0 |
| Retrofit | 2.12.0 |
| kotlinx-coroutines | 1.10.2 |
| Hilt | 2.56.2 |

</details>

---

## 目录（按常见需求跳转）

| 想做什么 | 跳转到 |
|----------|--------|
| 最短时间跑通依赖与请求 | [5 分钟上手（最小接入）](#5-分钟上手最小接入) · [环境要求](#环境要求) |
| 在 ViewModel 中使用 | [MVVM 用法](#mvvm-用法) |
| 不用 Hilt 怎么接入 | [非 Hilt 接入](#非-hilt-接入) |
| 请求结果怎么处理 | [结果处理](#结果处理) |
| 重试 / 超时 / 请求级配置 | [请求配置](#请求配置) |
| 动态切换 BaseUrl | [动态 BaseUrl](#动态-baseurl) |
| Token 刷新与未授权处理 | [Token 刷新](#token-刷新) |
| 上传与下载 | [上传与下载](#上传与下载) |
| WebSocket | [WebSocket](#websocket) |
| 网络状态监听 | [网络状态监听](#网络状态监听) |
| 注解速查 | [注解速查](#注解速查) |
| ProGuard / R8 | [ProGuard / R8](#proguard--r8) |

---

## 环境要求

| 项目 | 最低版本 |
|------|----------|
| Android minSdk | 24 |
| 本仓库 compileSdk（验证用） | 35 |
| JDK（仅本仓库 / demo） | 17 |
| Kotlin（库内对齐） | 2.0.21 |

---

## MVVM 用法

在 Hilt Module 中提供 API 接口，ViewModel 直接注入：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi =
        retrofit.create(UserApi::class.java)
}
```

```kotlin
@HiltViewModel
class UserViewModel @Inject constructor(
    private val executor: NetworkExecutor,
    private val api: UserApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun loadUsers() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = executor.executeRawRequest { api.getUsers() }
            when (result) {
                is NetworkResult.Success ->
                    _uiState.value = UiState.Success(result.data ?: emptyList())
                is NetworkResult.TechnicalFailure ->
                    _uiState.value = UiState.Error(result.exception.message ?: "Error")
                is NetworkResult.BusinessFailure ->
                    _uiState.value = UiState.Error("${result.code}: ${result.msg}")
            }
        }
    }
}
```

Activity 中通过 `by viewModels()` 获取 ViewModel，用 `repeatOnLifecycle` 收集 StateFlow 即可。

---

## 非 Hilt 接入

不使用 Hilt 的应用可通过 `AwNet` 工厂方法创建：

```kotlin
val executor = AwNet.createExecutor(
    config = NetworkConfig(baseUrl = "https://api.example.com/"),
    networkMonitor = myNetworkMonitor
)

val api = executor.createApi<UserApi>()
```

> `networkMonitor` 为必填参数；如需 Token 刷新可传入 `tokenProvider` 和 `unauthorizedHandler`。

WebSocket 同理：

```kotlin
val wsManager = AwNet.createWebSocketManager()
```

---

## 结果处理

`NetworkResult` 是三态密封类，覆盖所有请求结果：

```kotlin
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T?) : NetworkResult<T>()
    data class TechnicalFailure(val exception: BaseNetException) : NetworkResult<Nothing>()
    data class BusinessFailure(val code: Int, val msg: String) : NetworkResult<Nothing>()
}
```

### 链式调用

```kotlin
result.onSuccess { data -> render(data) }
    .onSuccessNotNull { data -> render(data) }
    .onBusinessFailure { code, msg -> showError("$code $msg") }
    .onTechnicalFailure { ex -> showError(ex.message) }
    .onFailure { /* 任意失败 */ }
```

### fold 折叠

```kotlin
val message = result.fold(
    onSuccess = { "成功: $it" },
    onTechnicalFailure = { "网络错误: ${it.message}" },
    onBusinessFailure = { code, msg -> "业务错误: $code $msg" }
)
```

### when 分支

```kotlin
when (result) {
    is NetworkResult.Success -> render(result.data)
    is NetworkResult.TechnicalFailure -> showError(result.exception.message)
    is NetworkResult.BusinessFailure -> showError("${result.code}: ${result.msg}")
}
```

---

## 请求配置

### executeRequest vs executeRawRequest

| 方法 | 适用场景 | 返回类型 |
|------|----------|----------|
| `executeRequest` | 后端返回 `BaseResponse`（code/msg/data）结构 | `NetworkResult<T>`，自动判断业务码 |
| `executeRawRequest` | 直接返回原始数据，无统一包装 | `NetworkResult<T>`，HTTP 成功即 Success |

```kotlin
// 标准业务接口（返回 GlobalResponse<T> 或自定义 BaseResponse<T>）
val result = executor.executeRequest { api.getUser(1) }

// 原始数据接口（如第三方 API、列表接口）
val result = executor.executeRawRequest { api.getPosts() }
```

当后端把业务对象 **二次序列化成字符串** 放在 `data` 里（`"data": "{\"foo\":1}"`）时，请使用 `GlobalResponse<YourDto>` + `executeRequest`；库内 [GlobalResponseTypeAdapterFactory](aw-net/src/main/java/com/answufeng/net/http/model/GlobalResponseTypeAdapterFactory.kt) 默认会再解析一层（可通过 [ResponseFieldMapping.parseEmbeddedJsonStringData] 关闭）。`onSuccess` 收到的是已解析的 `YourDto`，不是外层 JSON 字符串。

### RequestOption

```kotlin
val result = executor.executeRequest(
    option = RequestOption(
        tag = "getUser",
        retryOnFailure = 2,
        retryDelayMs = 500,
        retryOnTechnical = true,
        retryOnBusiness = false,
        totalTimeoutMs = 10_000,
        extraHeaders = mapOf("X-Custom" to "value"),
    )
) { api.getUser(1) }
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `successCode` | `null`（使用全局配置） | 业务成功码 |
| `dispatcher` | `Dispatchers.IO` | 请求调度器 |
| `tag` | `null` | 请求标签 |
| `retryOnFailure` | `0` | 协程级重试次数（不含首次） |
| `retryDelayMs` | `300` | 重试间隔 |
| `retryOnTechnical` | `true` | 技术错误时是否重试 |
| `retryOnBusiness` | `false` | 业务错误时是否重试 |
| `totalTimeoutMs` | `null` | 请求+重试整体超时 |
| `extraHeaders` | `emptyMap()` | 请求级额外 Header |
| `disableOkHttpRetry` | `false` | 禁用 OkHttp 层重试 |

---

## 动态 BaseUrl

### 全局运行时切换

```kotlin
@Inject lateinit var configProvider: NetworkConfigProvider

configProvider.update { it.copy(baseUrl = "https://staging.example.com/") }
```

### 单接口切换（注解）

```kotlin
@BaseUrl("https://cdn.example.com/files/")
@GET("avatar.png")
suspend fun avatar(): ResponseBody
```

注解优先级高于全局 `baseUrl`，直接替换 Retrofit 基础路径。

---

## Token 刷新

提供 `TokenProvider` 即可启用 HTTP 401 和业务码未授权的自动刷新：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = MyTokenProvider()

    @Provides
    @Singleton
    fun provideUnauthorizedHandler(): UnauthorizedHandler = UnauthorizedHandler {
        // 跳转登录页或清除会话
    }
}
```

```kotlin
interface TokenProvider {
    fun getAccessToken(): String?
    fun refreshTokenBlocking(): Boolean
    suspend fun refreshTokenSuspend(): Boolean
    fun clear() {}
}
```

HTTP 401 和业务未授权共享同一个刷新临界区，避免并发重复刷新。刷新失败时调用 `UnauthorizedHandler.onUnauthorized()`。

---

## 上传与下载

### 下载（含进度与校验）

```kotlin
val progress = NetworkExecutor.createDefaultProgressFlow()

lifecycleScope.launch {
    progress.collect { info -> updateProgress(info.progress) }
}

val result = executor.downloadFile(
    targetFile = file,
    progressFlow = progress,
    expectedHash = sha256,
    failureStrategy = DownloadFailureStrategy.DELETE_PARTIAL
) {
    api.download()
}
```

### 断点续传

```kotlin
val result = executor.downloadFileResumable(
    targetFile = file,
    existingFileSize = file.length(),
    progressFlow = progress
) {
    api.downloadWithRange("bytes=${file.length()}-")
}
```

### 上传（含进度）

```kotlin
val part = executor.createProgressPart("file", uploadFile, progressFlow)

val result = executor.executeRawRequest {
    api.uploadFile(part)
}
```

<details>
<summary><b>DownloadOption 完整参数（点击展开）</b></summary>

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `progressFlow` | `null` | 进度回调 Flow |
| `expectedHash` | `null` | 预期文件摘要 |
| `hashAlgorithm` | `"SHA-256"` | 摘要算法 |
| `hashStrategy` | `DELETE_ON_MISMATCH` | 校验失败策略 |
| `failureStrategy` | `DELETE_PARTIAL` | 下载失败策略 |
| `dispatcher` | `Dispatchers.IO` | 调度器 |
| `tag` | `null` | 请求标签 |

</details>

---

## WebSocket

### Hilt 注入

```kotlin
@Inject lateinit var wsManager: WebSocketManager
```

### 连接与消息

```kotlin
wsManager.connect(
    connectionId = "chat",
    url = "wss://example.com/socket",
    config = WebSocketManager.Config(
        headers = mapOf("Authorization" to "Bearer $token"),
        heartbeatMessage = "ping",
        heartbeatResponseMessage = "pong",
        maxReconnectAttempts = 5
    ),
    listener = object : WebSocketManager.WebSocketListener {
        override fun onOpen(connectionId: String) { }
        override fun onMessage(connectionId: String, text: String) { }
        override fun onMessage(connectionId: String, bytes: ByteArray) { }
        override fun onFailure(connectionId: String, t: Throwable) { }
        // ...其余回调按需实现
    }
)

wsManager.sendMessage("chat", "Hello")
wsManager.disconnect("chat")
```

### 默认单连接快捷 API

大多数场景只需一个 WebSocket 连接，可用快捷方法：

```kotlin
wsManager.connectDefault(url, listener = myListener)
wsManager.sendText("Hello")
wsManager.isConnected()
wsManager.disconnectDefault()
```

### 连接状态监听

```kotlin
lifecycleScope.launch {
    wsManager.connectionStateFlow.collect { stateMap ->
        val state = stateMap["chat"]
        updateUI(state)
    }
}
```

<details>
<summary><b>WebSocketManager.Config 常用参数（点击展开）</b></summary>

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `heartbeatIntervalMs` | `30_000` | 心跳间隔，0 不发送 |
| `heartbeatTimeoutMs` | `60_000` | 心跳超时，触发重连 |
| `heartbeatMessage` | `"ping"` | 心跳消息 |
| `enableHeartbeat` | `true` | 是否启用心跳 |
| `maxReconnectAttempts` | `0` | 最大重连次数，0 无限 |
| `reconnectBaseDelayMs` | `1_000` | 重连基础延迟 |
| `reconnectMaxDelayMs` | `30_000` | 重连最大延迟 |
| `messageQueueCapacity` | `100` | 离线消息队列容量 |
| `enableMessageReplay` | `true` | 是否补发离线消息 |
| `headers` | `emptyMap()` | 连接头 |
| `queryParameters` | `emptyMap()` | URL 查询参数 |
| `callbackOnMainThread` | `true` | 回调是否在主线程 |

</details>

---

## 网络状态监听

`NetworkMonitor` 通过 Hilt 自动注入，提供实时网络状态：

```kotlin
@Inject lateinit var networkMonitor: NetworkMonitor

// 实时监听
lifecycleScope.launch {
    networkMonitor.isConnected.collect { online -> updateUI(online) }
}

// 快速判断
if (networkMonitor.isOnline()) { doRequest() }

// 网络类型
lifecycleScope.launch {
    networkMonitor.networkType.collect { type ->
        when (type) {
            NetworkType.WIFI -> showWifiIcon()
            NetworkType.CELLULAR -> showCellularIcon()
            NetworkType.NONE -> showOfflineIcon()
            else -> showDefaultIcon()
        }
    }
}
```

---

## 注解速查

| 注解 | 作用目标 | 说明 |
|------|----------|------|
| `@BaseUrl("https://...")` | 接口方法 | 单接口切换基地址 |
| `@Timeout(connect = 5, read = 10)` | 接口方法 | 单接口超时配置（秒） |
| `@Retry(maxAttempts = 3)` | 接口方法 | 单接口重试策略 |
| `@SuccessCode(200)` | 接口方法 | 单接口业务成功码 |

```kotlin
interface Api {
    @BaseUrl("https://cdn.example.com/")
    @Timeout(connect = 5, read = 30, unit = TimeUnit.SECONDS)
    @Retry(maxAttempts = 3, initialBackoffMs = 500)
    @GET("files/{id}")
    suspend fun downloadFile(@Path("id") id: String): ResponseBody

    @SuccessCode(200)
    @POST("legacy-api")
    suspend fun legacyApi(): GlobalResponse<Data>
}
```

---

## ProGuard / R8

AAR 内置了公共 API、运行时注解、Gson 适配器、Retrofit 服务方法及 Kotlin 元数据的消费者规则。宿主应用在使用 Gson 反射且未添加 `@SerializedName` 时，仍需自行保留响应模型类。

---

## 构建验证

```powershell
./gradlew.bat :aw-net:compileDebugKotlin
./gradlew.bat :demo:compileDebugKotlin
./gradlew.bat :aw-net:lintDebug
./gradlew.bat :demo:lintDebug
./gradlew.bat :demo:assembleRelease
```

手动 Demo 验证应覆盖：HTTP 请求、动态 BaseUrl、401 刷新、上传/下载进度、断点续传、WebSocket 重连及不可恢复的 WebSocket 握手失败。
