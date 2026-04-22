# aw-net Demo 功能矩阵

主界面卡片 → Activity → 建议验证点。工具栏 **「演示清单」** 可查看摘要。

| 卡片 | Activity | 核心能力 |
|------|----------|----------|
| 基础请求 | `BasicRequestActivity` | GET/POST、`NetworkResult` 链式处理 |
| HTTP 综合 | `HttpDemoActivity` | 多场景 HTTP |
| 鉴权 | `AuthActivity` | Token、401、刷新协调 |
| 动态配置 | `DynamicConfigActivity` | BaseUrl / Timeout / Retry 注解 |
| 高级配置 | `AdvancedConfigActivity` | 日志级别、Header、运行时 `NetworkConfig` |
| 下载 | `DownloadActivity` | 进度、断点、Hash |
| 上传 | `UploadActivity` | Multipart、进度 |
| 高级工具 | `AdvancedActivity` | 去重、节流、轮询 |
| 错误处理 | `ErrorHandlingActivity` | 技术/业务失败、重试心智（勿与拦截器叠开） |
| 网络监听 | `NetworkMonitorActivity` | 连通性、类型 |
| MVVM 示例 | `MvvmDemoActivity` | `StateFlow` + Hilt |
| WebSocket | `WebSocketActivity` | 连接、重连 |

工具栏菜单 **「演示清单」** 可在应用内快速查看摘要。完整说明见仓库根目录 [README.md](../README.md)。
