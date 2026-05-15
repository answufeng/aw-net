# aw-net 2.0

`aw-net` 是一个基于 Kotlin、OkHttp、Retrofit、协程以及可选 Hilt 集成构建的 Android 网络库。它提供两个主要入口：

- `NetworkExecutor`：用于 HTTP API 调用、原始 Retrofit 请求、上传、下载、进度追踪、重试、Token 刷新及统一结果处理。
- `WebSocketManager`：用于单个或多个 WebSocket 连接管理、自动重连、心跳检测、离线消息补发及连接状态监听。

Demo 模块使用传统 Android View 实现。Compose 与单元测试不在 2.0 范围内。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| minSdk | 24 |
| compileSdk | 35 |
| JDK | 17 |
| Kotlin | 2.0.21 |
| AGP | 8.2.2 |

## 安装

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.answufeng:aw-net:2.0.0")

    // 仅在使用 Hilt 集成时需要
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
}
```

## Hilt 接入

```kotlin
@HiltAndroidApp
class App : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {
    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig {
        return NetworkConfig(
            baseUrl = "https://api.example.com/",
            networkLogLevel = NetworkLogLevel.BASIC
        )
    }
}
```

然后注入主入口：

```kotlin
@AndroidEntryPoint
class UserActivity : AppCompatActivity() {
    @Inject lateinit var executor: NetworkExecutor

    private val api by lazy { executor.createApi<UserApi>() }
}
```

## 非 Hilt 接入

2.0 为不使用 Hilt 的应用提供了工厂方法：

```kotlin
val executor = AwNet.createExecutor(
    config = NetworkConfig(baseUrl = "https://api.example.com/")
)

val api = executor.createApi<UserApi>()
```

## HTTP 用法

```kotlin
interface UserApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Long): GlobalResponse<User>
}

lifecycleScope.launch {
    val result = executor.executeRequest(
        option = RequestOption(tag = "getUser", retryOnFailure = 2)
    ) {
        api.getUser(1)
    }

    result
        .onSuccess { user -> render(user) }
        .onBusinessFailure { code, msg -> showMessage("$code $msg") }
        .onTechnicalFailure { error -> showMessage(error.message.orEmpty()) }
}
```

对于不使用 `BaseResponse` 的 API，使用 `executeRawRequest`：

```kotlin
val result = executor.executeRawRequest {
    api.getPosts()
}
```

## 2.0 API 变更

- 移除了已弃用的多参数 `executeRequest(...)` 和 `executeRawRequest(...)` 重载。
- 移除了重复的 `executeRequestFlow` 和 `executeRawRequestFlow` 别名。
- 使用 `RequestOption` 统一配置调度器、标签、重试和成功码。
- 协程级重试为推荐的重试方式。OkHttp 重试拦截器仍可用于高级的阻塞式拦截器链场景。
- `AwNet.createExecutor(...)` 提供了非 Hilt 的构造路径。

## 动态 BaseUrl

全局运行时切换：

```kotlin
configProvider.update { it.copy(baseUrl = "https://staging.example.com/") }
```

单接口切换：

```kotlin
@BaseUrl("https://cdn.example.com/files/")
@GET("avatar.png")
suspend fun avatar(): ResponseBody
```

2.0 替换原始 Retrofit 基础路径，而非在旧路径上追加新路径。

## Token 刷新

提供 `TokenProvider` 以启用 HTTP 401 和业务码未授权响应的自动刷新：

```kotlin
@Provides
@Singleton
fun provideTokenProvider(): TokenProvider = MyTokenProvider()

@Provides
@Singleton
fun provideUnauthorizedHandler(): UnauthorizedHandler = UnauthorizedHandler {
    // 跳转登录页或清除会话
}
```

HTTP 401 和业务未授权响应共享同一个刷新临界区，避免并发重复刷新。

## 上传与下载

```kotlin
val progress = NetworkExecutor.createDefaultProgressFlow()

val result = executor.downloadFile(
    targetFile = file,
    progressFlow = progress,
    expectedHash = sha256,
    failureStrategy = DownloadFailureStrategy.DELETE_PARTIAL
) {
    api.download()
}
```

断点续传下载需传入已有文件大小，并在 Retrofit API 调用中发送对应的 `Range` 请求头。2.0 会校验负数大小、缺失的部分文件以及续传返回空 body 的情况。

## WebSocket

```kotlin
wsManager.connect(
    connectionId = "chat",
    url = "wss://example.com/socket",
    config = WebSocketManager.Config(
        headers = mapOf("Authorization" to "Bearer $token"),
        queryParameters = mapOf("client" to "android"),
        heartbeatMessage = "ping",
        heartbeatResponseMessage = "pong",
        maxReconnectAttempts = 5
    ),
    listener = listener
)
```

2.0 校验配置值、重连前取消旧 socket、限制二进制日志预览长度，并通过 `connectionStateFlow` 暴露连接状态。

## ProGuard / R8

AAR 内置了公共 API、运行时注解、Gson 适配器、Retrofit 服务方法及 Kotlin 元数据的消费者规则。宿主应用在使用 Gson 反射且未添加 `@SerializedName` 时，仍需自行保留响应模型类。

## 构建验证

```powershell
./gradlew.bat :aw-net:compileDebugKotlin
./gradlew.bat :demo:compileDebugKotlin
./gradlew.bat :aw-net:lintDebug
./gradlew.bat :demo:lintDebug
./gradlew.bat :demo:assembleRelease
```

手动 Demo 验证应覆盖：HTTP 请求、动态 BaseUrl、401 刷新、上传/下载进度、断点续传、WebSocket 重连及不可恢复的 WebSocket 握手失败。
