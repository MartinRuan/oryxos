# Research & Architecture Decisions: Tool 体系与 Plugin Tool 机制

## 决策一：统一工具抽象契约与接口设计

- **决策**：统一以 `OryxTool` 接口作为所有工具（内置、外部 MCP 协议工具、Java Bean）的抽象契约。接口必须包含：
  - `String getName()`：工具唯一名称（供 LLM Function Calling 标识）；
  - `String getDescription()`：工具功能描述（供 LLM 理解何时调用）；
  - `String getInputSchema()`：工具参数的 JSON Schema 描述（供 LLM 结构化生成参数）；
  - `ToolResult execute(String inputJson)` / `ToolResult execute(JsonNode input)`：实际执行逻辑。
- **理由**：
  ReAct 推理引擎只需要依赖统一的抽象接口，完全不感知工具的具体实现（是本地 Java 方法、文件/Shell/HTTP 操作、还是通过网络调用远程 MCP Server）。这样保证了架构解耦，杜绝引擎层出现对各种工具类型的分支特判。
- **备选方案**：
  - 直接使用 Spring AI 的 `FunctionCallback`： Spring AI 在 1.0.0-M2 版本存在自动执行工具的设计倾向，违反宪法原则二（Spring AI 仅做 Schema 生成与协议转换，严禁自动执行工具）。OryxTool 作为纯净抽象层，与 Spring AI 解耦。

## 决策二：MCP Client 集成与外部服务连接容错

- **决策**：实现轻量自包含的 `McpClientService`，从 `.oryxos/mcp_servers.yaml` 读取外部 MCP 服务配置。启动时采用 `@PostConstruct` 建立连接，并拉取 `tools/list`，将每一个工具使用 `McpToolAdapter` 包装后注册到 `ToolRegistry`。
- **故障隔离原则**：
  外部 MCP Server（如基于 stdio 或 SSE 启动的外部进程/远程服务）可能因网络波动、环境缺失或服务不可用而连接失败。此时必须用 try-catch 捕获异常，输出包含 server 名称与异常详情的 `WARN` 日志，**绝对不允许阻断 OryxOS 主程序的启动**。健康的 MCP Server 以及系统内置工具仍需正常注册并对外提供服务。
- **备选方案**：
  - 强依赖官方 Java MCP SDK 的完整客户端生命周期管理：目前社区 Java MCP SDK 在单二进制打包与虚拟线程支持上可能引入重型依赖或响应式调用链，自研适配层接口契约清晰、纯粹同步阻塞，更符合技术宪法。

## 决策三：涉外 IO 工具前置沙箱安全拦截

- **决策**：
  `FileTools`、`ShellTools`、`HttpTools` 继承统一工具契约。在各个工具的 `execute` 方法首行，必须先执行 `Sandbox.enforce(new SandboxAction(type, target))` 进行权限与白名单检查。未通过时抛出异常直接中断操作，绝不执行底层物理 IO。
- **理由**：
  对齐技术方案 §6.7 与技术宪章第七条。安全检查优先在执行前阻断，违规异常沿既有调用栈抛出，由 `ToolExecutor` 统一记录失败审计至 `tool_invocations` 表，无需为安全违规单独定制旁路审计。
