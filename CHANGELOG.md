# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2024-xx-xx

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
