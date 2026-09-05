# Tasks: 第20节 Tool 体系 原理解析、实现与代码讲解

## Phase 1: Setup & Configuration

**Purpose**: 准备配置文件与依赖环境

- [x] T001 [P] 创建 MCP 示例配置文件 `.oryxos/mcp_servers.yaml`，包含 stdio 与 sse 传输规范定义
- [x] T002 检查并确认 `oryxos-tool/pom.xml` 中 SnakeYAML 与 Jackson 依赖正常引入

---

## Phase 2: Foundational (Core Abstraction & Enhancements)

**Purpose**: 夯实 `OryxTool` 与 `ToolResult` 核心抽象基石

- [x] T003 [P] 强化 `oryxos-core/src/main/java/com/oryxos/core/model/ToolResult.java`，增加 record 风格别名方法（`success()`, `content()`, `errorMessage()`, `retryable()`），保障向后兼容
- [x] T004 [P] 校验与强化 `oryxos-core/src/main/java/com/oryxos/core/OryxTool.java` 契约，确保包含 `getName()`, `getDescription()`, `getInputSchema()`, `execute(String inputJson)` 及 `execute(JsonNode input)` 重载

---

## Phase 3: User Story 1 - 统一工具抽象与内置基础工具执行 (Priority: P1) 🎯 MVP

**Goal**: 提供内置文件工具集（`read_file`, `write_file`, `list_dir`）、Shell 工具（`shell`）和 HTTP 工具（`http_get`, `http_post`），所有涉外 IO 执行前强制调用 `Sandbox.enforce` 白名单拦截。

**Independent Test**: `FileToolsTest`, `ShellToolsTest`, `HttpToolsTest` 正常操作能跑通，命中白名单外或非法越界时被安全拦截。

### Tests for User Story 1 (TDD 先行)

- [x] T005 [P] [US1] 编写文件工具测试套件 `oryxos-tool/src/test/java/com/oryxos/tool/builtin/FileToolsTest.java`（覆盖 read_file、write_file、list_dir 正常读写与越界路径沙箱拦截）
- [x] T006 [P] [US1] 编写 Shell 工具测试套件 `oryxos-tool/src/test/java/com/oryxos/tool/builtin/ShellToolsTest.java`（覆盖命令正常执行、argv 直传、超时阻断、非白名单命令沙箱拦截）
- [x] T007 [P] [US1] 编写 HTTP 工具测试套件 `oryxos-tool/src/test/java/com/oryxos/tool/builtin/HttpToolsTest.java`（覆盖 http_get、http_post 正常请求与非法/越界域名沙箱拦截）
- [x] T008 [P] [US1] 编写工具契约参数化测试 `oryxos-tool/src/test/java/com/oryxos/tool/contract/OryxToolContractTest.java`（遍历 ToolRegistry 中所有工具，断言 name、description、inputSchema 契约三件套非空且非空白）

### Implementation for User Story 1

- [x] T009 [P] [US1] 实现内置文件工具集 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/FileTools.java`，包含 `read_file`、`write_file`、`list_dir`，首行调用 `Sandbox.enforce`
- [x] T010 [P] [US1] 实现内置 Shell 工具 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/ShellTools.java`，支持直接执行白名单内的可执行文件与参数数组，超时保护，首行调用 `Sandbox.enforce`
- [x] T011 [P] [US1] 实现内置 HTTP 工具集 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpTools.java`，包含 `http_get`、`http_post`，首行调用 `Sandbox.enforce`，防止 SSRF
- [x] T012 [US1] 校验前序交付的 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/NotifyTools.java` 与新工具契约完全对齐

---

## Phase 4: User Story 2 - 工具注册表管理与 Profile 权限过滤 (Priority: P2)

**Goal**: 工具注册中心 `ToolRegistry` 统一管理多来源工具，支持按 Profile 声明的 `tools` 列表精确过滤出可用子集（不多不少）。

**Independent Test**: `ToolRegistryTest` 验证多来源工具统一注册，按 Profile 过滤后子集精确匹配。

### Tests for User Story 2 (TDD 先行)

- [x] T013 [P] [US2] 编写工具注册表测试套件 `oryxos-tool/src/test/java/com/oryxos/tool/ToolRegistryTest.java`（覆盖多来源工具统一注册、`contains` 判定、按 Profile tools 列表精确过滤不多不少）

### Implementation for User Story 2

- [x] T014 [US2] 强化 `oryxos-tool/src/main/java/com/oryxos/tool/ToolRegistry.java`，新增 `filterTools(List<String> allowedToolNames)` 精确过滤方法和 `contains(String name)` 方法
- [x] T015 [US2] 在 `oryxos-tool/src/main/java/com/oryxos/tool/config/ToolAutoConfiguration.java` 中配置内置工具 Bean（FileTools、ShellTools、HttpTools、NotifyTools）并自动注册到 `ToolRegistry`

---

## Phase 5: User Story 3 - 外部 MCP 协议工具适配与容错隔离 (Priority: P3)

**Goal**: 支持通过 `.oryxos/mcp_servers.yaml` 配置外部 MCP Server，启动时建立连接拉取工具并包装为 `OryxTool`；失联时记录 WARN 告警，启动不炸，其余工具正常注册。

**Independent Test**: `McpToolAdapterTest` 验证调用转发与结果封装；`McpClientServiceTest` 验证健康 server 正常注册且失联 server 隔离不崩。

### Tests for User Story 3 (TDD 先行)

- [x] T016 [P] [US3] 编写 MCP 工具适配器测试套件 `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpToolAdapterTest.java`（覆盖调用参数转发、`ToolResult.success` 包装、失败场景及 `retryable=true` 可重试标记）
- [x] T017 [P] [US3] 编写 MCP 客户端服务测试套件 `oryxos-tool/src/test/java/com/oryxos/tool/mcp/McpClientServiceTest.java`（覆盖从配置解析 MCP Server、MockClient 注册工具、单个 server 失联抛异常时只记录 WARN 且主启动流程不炸、其余工具照常注册）

### Implementation for User Story 3

- [x] T018 [P] [US3] 定义 MCP 配置与规格模型 `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpServerConfig.java` 与 `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpToolSpec.java`
- [x] T019 [P] [US3] 定义 MCP 客户端抽象接口 `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpClient.java`
- [x] T020 [US3] 实现 MCP 工具适配器 `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpToolAdapter.java`，实现 `OryxTool` 接口，转发调用至 `McpClient` 并封装结果
- [x] T021 [US3] 实现 MCP 客户端管理服务 `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpClientService.java`，读取 `.oryxos/mcp_servers.yaml`，并在启动时连接外部 Server，失联捕获异常只记 WARN，包装注册工具至 `ToolRegistry`

---

## Phase 6: Polish & Verification

**Purpose**: 端到端回归测试与全量构建门禁验证

- [x] T022 运行 `mvn test -pl oryxos-tool` 确认 Tool 模块全部单元测试全绿
- [x] T023 运行全量 `mvn clean verify` 确保 Spotless、P3C、Checkstyle、SpotBugs、OWASP 门禁 100% 通过且前序节测试零回归

