# 参与贡献 aw-net

## 环境

- **JDK 17+**（Android Gradle Plugin 8.2+ 的硬性要求）
- 建议：Android Studio 与仓库根目录 `gradlew` 一致，Gradle JDK 在 IDE 中指向 JDK 17

若命令行 `java -version` 仍是 11/8，而本机已安装 JDK 17，可任选其一将 **运行 Gradle 的 JVM** 切到 17：设置环境变量 `JAVA_HOME`；或在用户级 `~/.gradle/gradle.properties` 中配置 `org.gradle.java.home` 指向该安装（**不要**把个人机路径写进本仓库的 `gradle.properties`）。与根目录 [gradle.properties](gradle.properties) 中注释说明一致。

## 提交流程建议

1. 从 `main` / `develop` 建分支
2. 改代码后在本机跑：
   - `./gradlew :aw-net:ktlintCheck`（不通过时 `./gradlew :aw-net:ktlintFormat`）
   - `./gradlew :aw-net:lintRelease`
   - `./gradlew :demo:assembleRelease`（R8 冒烟；行为变更请对照 demo 手测）
   - 若改了公开 API/混淆规则：确认 `aw-net/consumer-rules.pro` 与调用方 R8
3. 若改动了 [README.md](README.md) 行为说明，请在 PR 描述中写清用户可见的变更与破坏性变更
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

根目录 [`.editorconfig`](.editorconfig) 与 **ktlint** 共同约束 Kotlin 缩进与换行；提交前勿与 `ktlintFormat` 结果冲突。

## 设计约定（简要）

- **重试不要叠两层**：`NetworkConfig.enableRetryInterceptor` 与 `RequestExecutor`/`NetworkExecutor` 的协程重试二选一，见 README「使用须知」
- 公开类尽量有 **KDoc**；敏感行为（脱敏、证书钉、WebSocket 与 HTTP 鉴权差异）在对应类中说明

## 发版前补充检查（R8 与依赖）

- **R8**：发版前在 demo 或宿主工程执行 release 打包；若收紧混淆，可用 `-printusage usage.txt` 审查是否误删公共 API，再决定是否调整 **宿主** `proguard` 或本库 `consumer-rules.pro`（避免过度 `-keep`）。
- **依赖升级**：升级 OkHttp/Retrofit/Hilt 后跑通 `assembleRelease` + demo，并更新 README「环境要求」中的版本说明。

欢迎 Issue / PR。
