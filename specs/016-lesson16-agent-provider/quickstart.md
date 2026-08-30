# Quickstart & Verification: 第16节 Agent Provider 与 Profile

## 1. 验证目标

验证第 16 节核心交付物：
1. `Profile` / `ProfileLoader` / `ProfileRegistry` 在 `oryxos-core`
2. `ProviderService` / `ToolSchemaAdapter` / `ProviderRegistry` 在 `oryxos-provider`
3. `LlmCallEntity` / `LlmCallRepository` 在 `oryxos-storage`
4. 验收 Harness 全绿

## 2. 自动化执行命令

```bash
# 1. 运行全部单元测试 (100% 离线脱机，秒级通过)
mvn test

# 2. 运行构建质量与安全门禁 (Checkstyle, Spotless, SpotBugs, P3C, PMD)
mvn clean verify

# 3. (可选) 手动真实模型集成冒烟测试
DEEPSEEK_API_KEY=sk-xxxx mvn test -Dgroups=integration
```

## 3. 验收标准清单

- [ ] `ProfileLoaderTest`: 合法 YAML 全字段解析、不存在 Provider 报错、坏文件容错、`${ENV}` 占位解析
- [ ] `ProviderServiceTest`: 双 Provider 路由不串台、未知名抛异常、成功/失败均落库审计、autoExecuteTools=false
- [ ] `ToolSchemaAdapterTest`: OryxTool 翻译 Spring AI 工具格式，只翻译不执行
- [ ] `LlmCallRepositoryTest`: SQLite 手工脚本建表读写验证，`success` 和 `error_message` 字段存在
- [ ] `ProviderSmokeIT`: `@Tag("integration")` 真实 Key 连通性测试
