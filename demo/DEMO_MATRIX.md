# aw-net Demo 矩阵

Demo 模块使用传统 Android View 和 Hilt 实现，用于手动验证 2.0 运行时行为。

| 卡片 | Activity | 验证内容 |
| --- | --- | --- |
| 基础请求 | `BasicRequestActivity` | 原始 Retrofit 调用、`NetworkResult`、成功与错误渲染 |
| HTTP 概览 | `HttpDemoActivity` | 常见 HTTP 流程与结果处理 |
| 鉴权 | `AuthActivity` | Token 存储、HTTP 401、业务未授权、刷新协调 |
| 动态配置 | `DynamicConfigActivity` | 运行时 `baseUrl`、`@BaseUrl`、超时、重试注解 |
| 高级配置 | `AdvancedConfigActivity` | 日志级别、Header、运行时 `NetworkConfig` 更新 |
| 下载 | `DownloadActivity` | 进度、摘要校验、失败清理、断点续传 |
| 上传 | `UploadActivity` | Multipart 上传与进度 |
| 高级工具 | `AdvancedActivity` | 去重、节流、轮询、取消辅助 |
| 错误处理 | `ErrorHandlingActivity` | 技术失败、业务失败、`RequestOption` 协程重试 |
| 网络监控 | `NetworkMonitorActivity` | 连接状态与网络类型监听 |
| MVVM | `MvvmDemoActivity` | `StateFlow` + Hilt 用法 |
| WebSocket | `WebSocketActivity` | Header/查询参数、重连、心跳超时、不可恢复的握手失败 |

## 推荐手动验证场景

| 场景 | 步骤 |
| --- | --- |
| 重试分层 | 同时启用 OkHttp 重试和协程重试，确认调试警告和增加的延迟 |
| 动态 BaseUrl | 从一个基础路径切换到另一个，验证旧 Retrofit 基础路径未被追加 |
| 鉴权并发 | 同时触发多个未授权请求，确认只走一次刷新路径 |
| 弱网 | 在上传、下载和普通请求期间切换网络 |
| 断点续传 | 从已有部分文件续传，验证无效偏移量快速失败 |
| WebSocket 重连 | 断开网络，等待心跳超时，恢复网络后验证重连 |
| WebSocket 鉴权失败 | 使用错误 Token 或 401 端点连接，验证其作为不可恢复错误停止 |
