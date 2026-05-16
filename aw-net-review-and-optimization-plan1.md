# aw-net 网络库全面审查与优化改进计划

> **合併版請見**：[aw-net-review-and-optimization-plan-v2.md](./aw-net-review-and-optimization-plan-v2.md)（含與程式碼核對後的狀態與修正說明）。本檔為原始計畫保留。

## 一、项目总览

aw-net 是一个基于 Kotlin + Hilt + OkHttp + Retrofit 的 Android 网络库，包含 HTTP 和 WebSocket 两部分。项目由 `aw-net`（lib）和 `demo` 组成。

**技术栈**：Kotlin 2.0.21 / Hilt 2.56.2 / OkHttp 4.12.0 / Retrofit 2.12.0 / Coroutines 1.10.2 / Gson / AGP 8.2.2

---

## 二、审查发现（按严重程度排序）

### 🔴 严重问题（可能导致运行时 Bug 或数据丢失）

#### S1. RequestExtraHeadersInterceptor 使用 ThreadLocal 存在协程上下文切换丢失风险

**文件**：`RequestExtraHeadersInterceptor.kt`

`threadLocalHeaders` 基于 `ThreadLocal`，但 OkHttp 拦截器链可能在不同线程执行（尤其使用 `withContext(dispatcher)` 切换调度器时），导致请求级 Header 丢失。此外，`finally` 块中的 `remove()` 可能在拦截器读取之前就执行，或因线程不同而无法清除，造成内存泄漏和 Header 污染。

**建议**：改用 OkHttp 的 `Request.tag()` 机制传递请求级 Header，与 `SuccessCodeInterceptor` 的做法一致，彻底避免线程模型问题。

#### S2. ExceptionHandle.customMappers 非线程安全

**文件**：`ExceptionHandle.kt`

`customMappers` 是 `mutableListOf`，多线程并发 `register()` 和 `handleException()` 可能导致 `ConcurrentModificationException`。虽然注册通常在 Application.onCreate 中进行，但作为公共 API 没有防护是不安全的。

**建议**：改用 `CopyOnWriteArrayList` 或加锁保护。

#### S3. NetEventDispatcher.delegate 使用全局可变单例，与 Hilt 注入路径冲突

**文件**：`NetEventDispatcher.kt`、`NetworkModule.kt`

`NetworkModule.provideNetTrackerDelegate()` 会将 Hilt 注入的 tracker 赋值给 `NetEventDispatcher.delegate`，但用户也可能手动赋值。两条路径并存时行为不可预测。此外，`delegate` 是 `@Volatile var`，在 `trackAsync` 中先读 `delegate` 到局部变量 `d`，但 `track()` 方法直接读 `delegate`，两个方法对 null 的处理不一致。

**建议**：统一事件分发路径，移除手动赋值方式，或至少在文档中明确优先级。`track()` 也应使用局部变量快照。

#### S4. WebSocketClientImpl 中 scope 和 supervisorJob 的重建存在竞态

**文件**：`WebSocketClientImpl.kt`

`ensureScopeActive()` 在 `reconnect()` 和 `connectInternal()` 中被调用，但 `supervisorJob` 和 `scope` 的赋值不是原子操作。多线程同时调用 `reconnect()` 可能导致一个 scope 使用了旧的 supervisorJob。

**建议**：使用 `AtomicReference<CoroutineScope>` 或在 `synchronized` 块中重建 scope。

#### S5. PersistentCookieJar.loadFromDisk() 在 init 中同步执行，可能阻塞主线程

**文件**：`PersistentCookieJar.kt`

如果文件较大或 I/O 慢，`loadFromDisk()` 会阻塞调用线程。由于 Hilt 单例通常在主线程创建，这可能导致 ANR。

**建议**：改为异步加载，内存中先使用空 map，加载完成后再替换。

---

### 🟠 中等问题（影响可维护性、扩展性或存在潜在风险）

#### M1. NetworkConfig 的 data class 本质导致"运行时可变"和"构建时一次性"字段混在一起

**文件**：`NetworkConfig.kt`

文档注释明确区分了"运行时可变"和"构建时一次性"字段，但它们混在同一个 data class 中，用户无法在编译期区分。修改 `connectTimeout` 等一次性字段后调用 `updateConfig` 不会生效，容易造成困惑。

**建议**：将一次性配置拆分为 `OkHttpConfig`（构建时），运行时可变配置保留在 `NetworkConfig` 中。或至少用 `@Deprecated` + 注解标记一次性字段，提醒用户不要在运行时修改。

#### M2. NetworkModule 和 AwNet 中 OkHttpClient 构建逻辑重复

**文件**：`NetworkModule.kt`、`AwNet.kt`

`provideOkHttpClient()` 和 `AwNet.createOkHttpClient()` 有大量重复的拦截器配置逻辑。维护时需要同步修改两处，容易遗漏。

**建议**：提取公共的 `OkHttpClientConfigurer` 类，两个入口都委托给它。

#### M3. DynamicRetryInterceptor 使用 Thread.sleep 阻塞 OkHttp 线程

**文件**：`DynamicRetryInterceptor.kt`

文档已说明此问题，但 `Thread.sleep` 在 OkHttp 线程上执行退避，高并发时会占用连接池线程。库同时提供协程层重试（RequestExecutor），但两层重试的交互仅靠 Debug 日志警告，缺少运行时保护。

**建议**：
- 当检测到协程层重试已启用时（`retryOnFailure > 0`），自动跳过 OkHttp 层重试
- 或提供 `@Retry(maxAttempts = 0)` 注解让用户显式禁用某一层

#### M4. TokenRefreshCoordinator 中阻塞路径和协程路径的互斥机制不够健壮

**文件**：`TokenRefreshCoordinator.kt`

`waitForBlockingRefresh()` 使用 50ms 轮询等待 `refreshing` 标志，这是一种 busy-wait 模式。在高频请求场景下，协程路径可能浪费大量 CPU 时间在轮询上。

**建议**：使用 `CountDownLatch` 或 `CompletableDeferred` 替代轮询等待，让协程真正挂起。

#### M5. GlobalResponseTypeAdapterFactory 每次解析都调用 mappingProvider()

**文件**：`GlobalResponseTypeAdapterFactory.kt`

`mappingProvider()` 在 `read()` 和 `write()` 中被多次调用（每次读 JSON 都调用），如果 provider 涉及 AtomicReference 读取，在高 QPS 场景下有微小的性能开销。

**建议**：影响较小，可保留现状，但可在 `read()` 入口处缓存一次 mapping 快照。

#### M6. WebSocket 模块缺少对 OkHttp WebSocket pingInterval 的配置说明

**文件**：`WebSocketModule.kt`、`WebSocketClientImpl.kt`

`WebSocketModule` 创建默认 OkHttpClient 时设置了 `pingInterval(30s)`，但 `WebSocketClientImpl` 内部的 `wsClient` 又将 `pingInterval` 设为 0。这意味着如果用户通过 `@WebSocketClient` 注入自定义 OkHttpClient 并设置了 pingInterval，它会被覆盖为 0。

**建议**：`WebSocketClientImpl` 应尊重用户传入的 OkHttpClient 配置，仅在需要覆盖时才修改 pingInterval，或通过 Config 暴露 pingInterval 配置。

#### M7. MockInterceptor 的通配符匹配每次请求都重新编译正则

**文件**：`MockInterceptor.kt`

`findMock()` 中对含 `*` 的 key 每次都 `Regex.escape(key).replace("\\*", ".*")`，以及 `regexMocks` 每次都 `Regex(pattern).matches(path)`，在高频请求下有性能开销。

**建议**：预编译正则并缓存。

#### M8. NetworkConfig.Builder 与 data class copy() 功能重叠

**文件**：`NetworkConfig.kt`

同时提供 `Builder` 和 `toBuilder()` + `copy()`，两种构建方式并存增加了维护负担。Builder 的字段默认值与 data class 的默认值需要保持同步。

**建议**：统一为一种方式。推荐保留 Builder（适合 Java 互操作），移除 `toBuilder()` 或将其标记为辅助方法。

---

### 🟡 轻微问题（代码质量、一致性、可读性）

#### L1. 日志体系不统一

HTTP 侧使用 `NetLogger`（d/e 两个方法），WebSocket 侧使用 `WebSocketLogger`（d/i/w/e 四个方法），接口设计不一致。

**建议**：统一日志接口设计，至少方法级别保持一致。可以定义一个基础 `Logger` 接口，HTTP 和 WebSocket 各自扩展。

#### L2. OptionalExt 中的扩展函数使用 java.util.Optional

**文件**：`OptionalExt.kt`

Kotlin 项目中使用 `java.util.Optional` 不够惯用。这些扩展函数仅用于 Hilt 的 `Optional<T>` 注入结果处理。

**建议**：可以保留（Hilt 强制使用 `java.util.Optional`），但建议在文档中说明原因。

#### L3. NetCode.Business 常量与 HTTP 状态码冲突

**文件**：`NetCode.kt`

`NetCode.Business.UNAUTHORIZED = 401` 和 `NetCode.Business.FORBIDDEN = 403` 与 HTTP 状态码数值相同，容易混淆。业务码 401 和 HTTP 401 是完全不同的概念。

**建议**：使用不同的数值范围（如 10001、10003）或在文档中更明确地说明这是业务码而非 HTTP 状态码。

#### L4. PrettyNetLogger 对大 JSON 直接截断，丢失脱敏机会

**文件**：`PrettyNetLogger.kt`

当 `trimmedMessage.length > LARGE_JSON_THRESHOLD` 时，直接调用 `maskAndTruncate`，只做 Header 脱敏和截断，跳过了 JSON body 的敏感字段脱敏。

**建议**：对大 JSON 也尝试流式脱敏，或至少在截断前做关键字段提取脱敏。

#### L5. DownloadExecutor 和 UploadExecutor 中 successCode 解析逻辑重复

**文件**：`DownloadExecutor.kt`、`UploadExecutor.kt`、`RequestExecutor.kt`

三处都有类似的 `ResponseSuccessCodeResolver.resolve() + code == effectiveSuccessCode` 判断 + Success/BusinessFailure 分支逻辑。

**建议**：提取为 `fun <T> BaseResponse<T>.toNetworkResult(successCode: Int?, configProvider: NetworkConfigProvider): NetworkResult<T>` 扩展函数。

#### L6. consumer-rules.pro 过度保留

**文件**：`consumer-rules.pro`

对 `NetworkExecutor`、`RequestExecutor`、`DownloadExecutor`、`UploadExecutor` 等内部类做了 `-keep class ... { *; }`，这些类虽然通过 Hilt 注入是 public 的，但 `{ *; }` 保留了所有成员，包括内部方法。

**建议**：仅保留公共 API 方法，内部实现允许混淆以减小包体积。

#### L7. 缺少 ktlint format 的 CI 集成

**文件**：`.github/workflows/ci.yml`（未读取但项目有 ktlint 配置）

项目配置了 ktlint 但未确认 CI 是否运行。

**建议**：确保 CI 中执行 `./gradlew ktlintCheck`。

---

### 🔵 功能增强建议

#### F1. 缺少请求级超时的注解支持（类似 @Timeout 但用于协程层 totalTimeoutMs）

当前 `@Timeout` 注解只影响 OkHttp 层的 connect/read/write 超时，而 `RequestOption.totalTimeoutMs` 只能通过代码设置。对于"整体超时"这种常见需求，缺少注解级配置。

**建议**：扩展 `@Timeout` 注解增加 `totalMs` 字段，或在 `RequestOption` 中支持从注解读取。

#### F2. WebSocket 缺少自动重连时的 Token 刷新支持

**文件**：`WebSocketClientImpl.kt`

文档已说明 WebSocket 建连不会自动与 TokenRefreshCoordinator 同步刷新。但对于需要鉴权的 WebSocket 连接，这是一个常见需求。

**建议**：提供 `onBeforeConnect` 回调或 `tokenProvider` 接口，让用户在建连前刷新 Token 并注入到 headers/queryParameters 中。

#### F3. 缺少请求拦截/修改能力（类似 OkHttp Interceptor 但在协程层）

当前请求的拦截和修改完全依赖 OkHttp Interceptor。但在协程层（RequestExecutor），无法在业务逻辑执行前后插入通用处理（如打点、参数校验、缓存判断等）。

**建议**：提供 `RequestInterceptor` 接口（协程友好），在 `executeRequest` 前后调用。

#### F4. NetworkResult 缺少 mapSuccess / flatMap 操作

**文件**：`NetworkResultExt.kt`

当前有 `map`（转换 Success 的 data）和 `recover`/`recoverWith`（失败恢复），但缺少 `flatMap`（链式请求）和 `mapSuccess`（仅转换 Success 但保留 data 可空性语义）。

**建议**：增加 `flatMap` 和更丰富的函数式操作。

#### F5. 缺少对 Kotlin Serialization 的官方支持

当前 Gson 是唯一的 Converter 实现。虽然提供了 `ConverterFactoryProvider` 扩展点，但缺少 Kotlin Serialization 的官方适配模块。

**建议**：考虑提供 `aw-net-converter-kotlinx-serialization` 子模块。

#### F6. NetworkMonitor 未在请求失败时自动提示

`NetworkMonitor` 是独立组件，未与 `RequestExecutor` 集成。当网络断开时，请求仍然会发出去然后失败。

**建议**：在 `RequestExecutor` 中集成网络状态检查，网络不可用时直接返回 `TechnicalFailure(NO_NETWORK)` 而非等待超时。

#### F7. 缺少请求缓存能力

当前只有 OkHttp 层的 HTTP 缓存（Cache），缺少协程层的业务缓存（如内存缓存 + 过期策略）。

**建议**：可考虑提供 `CachedRequestExecutor` 装饰器，与 `RequestThrottle` 配合使用。

#### F8. WebSocket 的 State 枚举缺少 ERROR 状态

**文件**：`WebSocketManager.kt`

当前只有 `DISCONNECTED / CONNECTING / CONNECTED / RECONNECTING`，连接失败后直接回到 `DISCONNECTED`，无法区分"从未连接"和"连接失败"。

**建议**：增加 `ERROR` 状态，或在 `DISCONNECTED` 中增加原因字段。

---

## 三、优化改进计划（按优先级排列）

### 第一阶段：修复严重问题（P0）

| 编号 | 任务 | 涉及文件 | 改动范围 |
|------|------|----------|----------|
| S1 | 将 RequestExtraHeadersInterceptor 从 ThreadLocal 改为 Request.tag() 机制 | `RequestExtraHeadersInterceptor.kt`、`NetworkExecutor.kt`、`RequestExecutor.kt` | 中 |
| S2 | ExceptionHandle.customMappers 改为 CopyOnWriteArrayList | `ExceptionHandle.kt` | 小 |
| S3 | 统一 NetEventDispatcher 的事件分发路径，修复 track() 的 null 安全问题 | `NetEventDispatcher.kt`、`NetworkModule.kt` | 中 |
| S4 | 修复 WebSocketClientImpl 的 scope 重建竞态 | `WebSocketClientImpl.kt` | 小 |
| S5 | PersistentCookieJar 异步加载 | `PersistentCookieJar.kt` | 中 |

### 第二阶段：解决中等问题（P1）

| 编号 | 任务 | 涉及文件 | 改动范围 |
|------|------|----------|----------|
| M1 | 拆分 NetworkConfig 为构建时配置和运行时配置 | `NetworkConfig.kt`、`NetworkConfigProvider.kt`、所有拦截器 | 大 |
| M2 | 提取 OkHttpClient 构建公共逻辑 | 新增 `OkHttpClientBuilder.kt`，修改 `NetworkModule.kt`、`AwNet.kt` | 中 |
| M3 | 双层重试自动协调 | `DynamicRetryInterceptor.kt`、`RequestExecutor.kt` | 中 |
| M4 | TokenRefreshCoordinator 用 CompletableDeferred 替代轮询 | `TokenRefreshCoordinator.kt` | 中 |
| M6 | WebSocket pingInterval 配置透明化 | `WebSocketClientImpl.kt`、`WebSocketManager.kt` | 小 |
| M7 | MockInterceptor 预编译正则 | `MockInterceptor.kt` | 小 |

### 第三阶段：功能增强（P2）

| 编号 | 任务 | 涉及文件 | 改动范围 |
|------|------|----------|----------|
| F1 | @Timeout 注解增加 totalMs 支持 | `Timeout.kt`、`DynamicTimeoutInterceptor.kt`、`RequestExecutor.kt` | 中 |
| F2 | WebSocket 建连前回调支持 | `WebSocketManager.kt`、`WebSocketClientImpl.kt` | 中 |
| F6 | RequestExecutor 集成网络状态预检 | `RequestExecutor.kt`、`NetworkMonitor.kt` | 中 |
| F8 | WebSocket 增加 ERROR 状态 | `WebSocketManager.kt`、`WebSocketClientImpl.kt` | 中 |
| L5 | 提取 BaseResponse → NetworkResult 公共转换 | 新增扩展函数，修改三个 Executor | 小 |

### 第四阶段：代码质量与一致性（P3）

| 编号 | 任务 | 涉及文件 | 改动范围 |
|------|------|----------|----------|
| L1 | 统一日志接口 | `NetLogger.kt`、`WebSocketLogger.kt` | 中 |
| L3 | NetCode.Business 使用独立数值范围 | `NetCode.kt` | 小（需注意兼容性） |
| L4 | PrettyNetLogger 大 JSON 脱敏增强 | `PrettyNetLogger.kt` | 中 |
| L6 | 精简 consumer-rules.pro | `consumer-rules.pro` | 小 |
| M8 | 统一 NetworkConfig 构建方式 | `NetworkConfig.kt` | 中 |
| F4 | NetworkResult 增加 flatMap 等函数式操作 | `NetworkResultExt.kt` | 小 |

---

## 四、架构改进建议（长期）

1. **模块化拆分**：将 WebSocket 拆为独立子模块（`aw-net-websocket`），HTTP 核心为 `aw-net-core`，用户可按需依赖
2. **Converter 模块化**：Gson 作为 `aw-net-converter-gson`，未来支持 `aw-net-converter-moshi`、`aw-net-converter-kotlinx`
3. **协程优先设计**：所有公共 API 基于 `suspend` 函数和 `Flow`，移除对 `ThreadLocal`、`Thread.sleep` 等阻塞原语的依赖
4. **配置不可变化**：运行时配置通过 `StateFlow<NetworkConfig>` 暴露，构建时配置在创建 Client 后不可变
5. **依赖注入解耦**：提供 Koin / Manual DI 的支持模块，不强制依赖 Hilt

---

## 五、总结

aw-net 整体架构设计合理，功能覆盖面广（HTTP 请求/上传/下载/断点续传/WebSocket/Token 刷新/网络监控/请求去重/节流/轮询等），代码注释详尽，API 设计考虑了扩展性。主要改进方向集中在：

1. **线程安全**：ThreadLocal → Request.tag()、customMappers 并发保护、scope 重建竞态
2. **配置模型**：区分构建时/运行时配置，减少用户困惑
3. **代码去重**：OkHttpClient 构建、successCode 解析等逻辑统一
4. **协程友好**：消除 Thread.sleep、轮询等待等阻塞模式
5. **功能补全**：网络预检、WebSocket 鉴权、更多 NetworkResult 操作
