# 参与贡献 aw-net

## 环境

- **JDK 17+**（Android Gradle Plugin 8.2+ 的硬性要求）
- 建议：Android Studio 与仓库根目录 `gradlew` 一致，Gradle JDK 在 IDE 中指向 JDK 17

## 提交流程建议

1. 从 `main` / `develop` 建分支
2. 改代码后在本机跑：
   - `./gradlew :aw-net:ktlintCheck`（不通过时 `./gradlew :aw-net:ktlintFormat`）
   - `./gradlew :aw-net:lintRelease`
   - `./gradlew :demo:assembleRelease`（R8 冒烟；行为变更请对照 demo 手测）
   - 若改了公开 API/混淆规则：确认 `aw-net/consumer-rules.pro` 与调用方 R8
3. 若改动了 [README.md](README.md) 行为说明，请同步 [CHANGELOG.md](CHANGELOG.md) 的 `[Unreleased]` → **Documentation**（用户可见的变更、破坏性变更另在 **Fixed/Changed** 写清）
4. 发起 Pull Request

## 常见 Gradle 任务

| 任务 | 说明 |
|------|------|
| `:aw-net:assembleRelease` | 编库 release AAR |
| `:aw-net:ktlintCheck` / `:aw-net:ktlintFormat` | 代码风格检查 / 自动格式化 |
| `:aw-net:lintRelease` | Android Lint |
| `:demo:assembleRelease` | 编 demo release（R8 冒烟，建议每次 PR 执行） |

## 模块与职责

- **`aw-net`**：主库（HTTP、WebSocket、Hilt 模块）
- **`demo`**：示例应用；集成验证与回归以 demo + 手测为主

## 设计约定（简要）

- **重试不要叠两层**：`NetworkConfig.enableRetryInterceptor` 与 `RequestExecutor`/`NetworkExecutor` 的协程重试二选一，见 README「使用须知」
- 公开类尽量有 **KDoc**；敏感行为（脱敏、证书钉、WebSocket 与 HTTP 鉴权差异）在对应类中说明

欢迎 Issue / PR。
