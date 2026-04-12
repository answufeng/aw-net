# 贡献指南

感谢你对 aw-net 的关注！欢迎提交 Issue 和 Pull Request。

## 提交 Issue

- Bug 报告请包含：复现步骤、预期行为、实际行为、相关日志
- 功能请求请描述：使用场景、期望的 API 设计、替代方案

## 提交 Pull Request

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交改动：`git commit -m 'Add some feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

### 代码规范

- 遵循 Kotlin 编码规范
- 公共 API 必须添加 KDoc 注释
- 新功能需要配套单元测试
- 保持向后兼容，废弃 API 使用 `@Deprecated` 注解并提供迁移路径

### 提交信息格式

```
<type>: <subject>

<body>
```

type 类型：
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `refactor`: 代码重构
- `test`: 测试相关
- `chore`: 构建/工具变更

## 开发环境

- JDK 17+
- Android SDK，compileSdk 35
- Kotlin 2.0.21+

## 运行测试

```bash
./gradlew :aw-net:testDebugUnitTest
```

## License

提交代码即表示你同意以 Apache 2.0 许可证授权你的贡献。
