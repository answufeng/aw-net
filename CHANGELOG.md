# Changelog

All notable changes to this project are documented in this file.  
版本与 [JitPack](https://jitpack.io/#answufeng/aw-net) 标签对应：`implementation("com.github.answufeng:aw-net:<tag>")`。

## [Unreleased]

### Added

- `NetworkConfig.enableRequestTracking`：为 `false` 时跳过量请求/上传/下载经 `NetTracker` 的埋点事件（`NetEvent` 起止），默认 `true`。
- `requestOption { }` DSL（[RequestOptionDsl.kt](aw-net/src/main/java/com/answufeng/net/http/model/RequestOptionDsl.kt)）用于构建 `RequestOption`。
- `NetworkExecutor.requestResultFlow` / `rawRequestResultFlow`：与 `executeRequestFlow` / `executeRawRequestFlow` 等价，强调「单结果冷流」语义。
- `DynamicRetryInterceptor` 可配置 `minJitteredBackoffLowerBoundMs`；`companion` 暴露 `ABSOLUTE_MAX_RETRY_ROUNDS` 供排障与单测；恶意永远 `true` 的 [RetryStrategy] 会在该上限处失败。
- 单元测试补全：`SuccessCodeInterceptor`、`DynamicTimeoutInterceptor`、重试安全上限、`NetTracker.trackAsync` 等；[CONTRIBUTING.md](CONTRIBUTING.md) 与 README「工程品质」段。

### Quality

- ktlint 在 `aw-net` 模块**默认失败即断 CI**（`ignoreFailures = false`），提交前请 `ktlintFormat`。

### Fixed

- `ProgressResponseBody`：显式 `close()` 并委托给底层 `ResponseBody`，保证连接/资源释放更可靠。

### Documentation

- README：使用须知（JitPack、Gson 默认、**重试只开一层**、Hilt 可选与 `Optional`、`NetTracker` 与 `enableRequestTracking`）、`requestOption` 示例、Flow 单发射说明、Moshi 等自定义 `NetworkClientFactory` 示例；修订「运行时配置」表（与单例 `OkHttpClient` / `@Timeout` 的区分）、`NetEvent` 埋点字段示例、WebSocket 可编译样例、并发与 Token FAQ、baseUrl 格式约定。
- 若干公开注解与 `BaseResponse` / `NetEvent` / `NetworkResult` / `NetworkConfigProvider` 的 KDoc 与注释梳理。

### Removed

- 删除误置于 `com.answufeng.net.http.annotations` 包下与 [DeprecatedAliases](aw-net/src/main/java/com/answufeng/net/http/annotations/DeprecatedAliases.kt) 冲突的重复 `NetworkConfig` 类定义；以 `com.answufeng.net.http.config.NetworkConfig` 为准，旧包名仍通过 `typealias` 提供迁移提示。

---

## 1.0.0

- 初始 JitPack 发布线（以仓库 tag 为准）。
