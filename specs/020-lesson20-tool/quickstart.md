# Quickstart & Verification Guide: 第20节 Tool 体系

## 一、验收标准与 Harness 测试映射

第 20 节交付物通过以下 5 组自动化 Harness 测试类进行全量验证：

1. **`OryxToolContractTest`**：
   - 参数化测试遍历 `ToolRegistry.getAllTools()`；
   - 断言每个工具的 `getName()`、`getDescription()`、`getInputSchema()` 契约三件套非空且非空白字符串。
2. **`ToolRegistryTest`**：
   - 测试内置工具与 MCP 工具的多来源注册；
   - 验证 `filterTools`：当 Profile 声明特定 `tools` 列表时，过滤得到的工具列表不多不少精确匹配。
3. **`FileToolsTest` / `ShellToolsTest` / `HttpToolsTest`**：
   - 测试各内置工具在合法参数下的正确执行逻辑与输出结果；
   - 测试当参数触发沙箱非白名单时，`Sandbox.enforce` 能在前置执行阶段抛出违规异常并中断底层物理 IO。
4. **`McpToolAdapterTest`**：
   - Mock `McpClient` 验证协议字段透传与执行调用；
   - 验证成功与失败场景下 `ToolResult` 的结构化封装（含 `retryable=true` 标志）。
5. **`McpClientServiceTest`**：
   - 验证读取配置并连接健康 MCP Server 时工具被正确包装注册；
   - **关键韧性验证**：当某个 MCP Server 连接失败（抛出 ConnectException 等）时，系统仅记录 `WARN` 告警，启动流程绝不中断，且其余健康服务的工具正常注册。

## 二、本地快速验证执行指令

```bash
# 1. 运行第20节 Tool 体系专属单测套件
mvn test -pl oryxos-tool

# 2. 全量项目构建门禁验证（格式化、规范检查、代码安全、全量测试回归）
mvn clean verify
```
