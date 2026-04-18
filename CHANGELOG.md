# Changelog

All notable changes to this project will be documented in this file.

## [1.1.0] - 2025-04-18

### Fixed
- **TokenRefreshCoordinator 并发刷新竞态**：统一 `blockingLock` 和 `coroutineMutex` 两把独立锁为一把 `ReentrantLock`，避免 OkHttp 线程和协程同时刷新 Token
- **DownloadExecutor Hash 校验溢出**：`md.update(bytes, 0, readCount.toInt())` 在大文件（>2GB）时溢出，改用 `md.update(bytes)`
- **RequestThrottle 并发竞态**：使用 `ConcurrentHashMap.compute` 保证缓存检查和更新的原子性
- **UploadActivity 上传端点不可用**：改用 httpbin.org 的 `/post` 端点 + `@BaseUrl` 注解
- **RequestExecutor 重试无退避**：固定延迟改为指数退避 + 随机抖动（exponential backoff with jitter）
- **SuccessCodeInterceptor 性能问题**：无 `@SuccessCode` 注解时直接返回，避免不必要的 JSON 解析；改用 `ThreadLocal` 传递成功码，不再修改 JSON body

### Added
- **RequestOption**：数据类封装请求配置，简化 `executeRequest` 参数传递
- **executeRequestFlow / executeRawRequestFlow**：Flow 版本 API，便于在 ViewModel 中转换为 StateFlow
- **createApi\<T\>()**：内联泛型便捷方法，简化 `retrofit.create(XxxApi::class.java)` 调用
- **WebSocket connectionStateFlow**：`IWebSocketManager` 增加 `StateFlow<Map<String, State>>` 属性
- **下载断点续传**：`downloadFileResumable()` 方法，支持从已有文件末尾继续下载
- **RequestCanceller**：请求取消管理器，支持按 tag 批量取消请求
- **PersistentCookieJar**：持久化 CookieJar 实现，将 Cookie 缓存到本地文件
- **MockInterceptor**：Mock 拦截器，支持按 URL 路径注册 Mock 响应，支持通配符和模拟延迟
- **NetworkConfig.cookieJar**：`NetworkConfig` 增加 `cookieJar` 配置项

### Removed
- **RetryInterceptor**：已废弃的旧版重试拦截器，与 `DynamicRetryInterceptor` 功能重叠
- **RetryUtils.retryWithBackoff**：未被使用的重试工具函数

## [1.0.0] - 2024-12-01

### Added
- 统一结果包装 `NetworkResult<T>`（Success / TechnicalFailure / BusinessFailure）
- HTTP 请求执行器（RequestExecutor），支持协程级重试和 Token 自动刷新
- 文件下载执行器（DownloadExecutor），支持进度回调和 SHA-256 Hash 校验
- 文件上传执行器（UploadExecutor），支持单文件/多文件/Multipart 进度回调
- WebSocket 多连接管理器，支持心跳检测、断线重连、离线消息队列
- Token 鉴权：OkHttp Authenticator + 协程层双重保障，并发安全
- 动态配置：`@BaseUrl`、`@Timeout`、`@Retry` 注解级配置
- 响应字段映射：自定义 code/msg/data 字段名
- 日志格式化：敏感信息脱敏（Header + Body 字段级）、JSON 美化
- 请求监控：`NetEvent` 事件追踪
- 轮询工具：`pollingFlow()`
- 请求去重：`RequestDedup`
- 请求节流：`RequestThrottle`
- 网络状态监听：`NetworkMonitor`
- SSL 证书固定支持
- Hilt 依赖注入集成，支持 OptionalBindings 渐进式配置
